package MIPSTranslatePackage;

import IR.IRElem;
import IR.IRImmSymbol;
import IR.IRSymbol;
import Optimizer.BasicBlock;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class DefUseAnalysis {
    private ArrayList<BasicBlock> blockList;
    private HashSet<IRSymbol> varSymbol; // 变量标签
    private HashMap<IRSymbol, HashSet<DefUseNetElem>> allGenMap;

    public DefUseAnalysis(ArrayList<BasicBlock> blockList) {
        this.blockList = blockList;
    }

    public void ArrDefAnalysis() { // 到达定义分析
        genKillCal(); // 先计算gen和kill集合
        boolean changed = true;
        for (BasicBlock block : blockList) { // 全部置空
            block.setOutSetDG(new HashSet<>());
        }
        while (changed) {
            changed = false;
            for (int i = 0; i < blockList.size(); i++) {
                BasicBlock curBlock = blockList.get(i);
                HashSet<DefUseNetElem> inSet = new HashSet<>();
                for (BasicBlock predBlock : curBlock.getPredecessors()) {
                    inSet.addAll(predBlock.getOutSetDG());
                }
                curBlock.setInSetDG(inSet);
                HashSet<DefUseNetElem> outSet = new HashSet<>(inSet);
                outSet.removeAll(curBlock.getKillSet());
                outSet.addAll(curBlock.getGenSet());
                if (!outSet.equals(curBlock.getOutSetDG())) {
                    changed = true;
                    curBlock.setOutSetDG(outSet);
                }

            }
        }
    }

    public void genKillCal() {
        HashMap<IRSymbol, HashSet<DefUseNetElem>> killSetByVar = getAllKillSet();
        this.varSymbol = new HashSet<>(killSetByVar.keySet());
        this.allGenMap = new HashMap<>();
        for (BasicBlock block : blockList) {
            blockGenKillCal(block, killSetByVar);
        }
    }

    public void blockGenKillCal(BasicBlock block,
                                HashMap<IRSymbol, HashSet<DefUseNetElem>> killSetByVar) {
        ArrayList<IRElem> iRList = new ArrayList<>(block.getBlockIRList());
        HashSet<DefUseNetElem> lastGen = new HashSet<>();
        HashSet<DefUseNetElem> lastKill = new HashSet<>();
        for (int i = 0; i < iRList.size(); i++) {
            IRElem inst = iRList.get(i);
            if (inst.getType() == IRElem.ADD || inst.getType() == IRElem.MINU ||
                    inst.getType() == IRElem.MULT || inst.getType() == IRElem.DIV ||
                    inst.getType() == IRElem.MOD || inst.getType() == IRElem.LSHIFT ||
                    inst.getType() == IRElem.RSHIFT || inst.getType() == IRElem.GRE ||
                    inst.getType() == IRElem.GEQ || inst.getType() == IRElem.LSS ||
                    inst.getType() == IRElem.LEQ || inst.getType() == IRElem.EQL ||
                    inst.getType() == IRElem.NEQ || inst.getType() == IRElem.LOAD ||
                    inst.getType() == IRElem.ASSIGN || inst.getType() == IRElem.CALL ||
                    inst.getType() == IRElem.GETINT || inst.getType() == IRElem.ALLOCA ||
                    inst.getType() == IRElem.RASHIFT || inst.getType() == IRElem.AND) {
                IRSymbol op3 = inst.getOp3();
                DefUseNetElem currentGen = new DefUseNetElem(op3, block.getBlockID(), i);
                HashSet<DefUseNetElem> killSet = new HashSet<>(lastKill);
                killSet.addAll(killSetByVar.get(op3));
                killSet.remove(currentGen); // 不能含有当前定义
                HashSet<DefUseNetElem> genSet = new HashSet<>(lastGen);
                genSet.removeAll(killSet);
                genSet.add(currentGen);

                HashSet<DefUseNetElem> allGenSet = allGenMap.getOrDefault(op3, null);
                if (allGenSet == null) {
                    allGenSet = new HashSet<>();
                    allGenMap.put(op3, allGenSet);
                }
                allGenSet.add(currentGen); // 在全局Gen中加入记录

                lastGen = genSet;
                lastKill = killSet;
            } else if (inst.getType() == IRElem.FUNC) {
                ArrayList<IRSymbol> symbolList = inst.getSymbolList();
                HashSet<DefUseNetElem> killSet = new HashSet<>(lastKill);
                HashSet<DefUseNetElem> curGenSet = new HashSet<>();
                for (IRSymbol symbol : symbolList) {
                    if (symbol instanceof IRImmSymbol) {
                        continue;
                    }
                    DefUseNetElem currentGen = new DefUseNetElem(symbol, block.getBlockID(), i);
                    curGenSet.add(currentGen);
                    killSet.addAll(killSetByVar.get(symbol));
                    HashSet<DefUseNetElem> allGenSet = allGenMap.getOrDefault(symbol, null);
                    if (allGenSet == null) {
                        allGenSet = new HashSet<>();
                        allGenMap.put(symbol, allGenSet);
                    }
                    allGenSet.add(currentGen); // 在全局Gen中加入记录
                }
                killSet.removeAll(curGenSet); // 不能含有当前定义
                HashSet<DefUseNetElem> genSet = new HashSet<>(lastGen);
                genSet.removeAll(killSet);
                genSet.addAll(curGenSet);

                lastGen = genSet;
                lastKill = killSet;
            }
        }
        block.setGenKillSet(lastGen, lastKill);
    }

    public HashMap<IRSymbol, HashSet<DefUseNetElem>> getAllKillSet() {
        HashMap<IRSymbol, HashSet<DefUseNetElem>> killSetByVar = new HashMap<>();
        for (BasicBlock block : blockList) {
            ArrayList<IRElem> iRList = new ArrayList<>(block.getBlockIRList());
            for (int i = 0; i < iRList.size(); i++) {
                IRElem inst = iRList.get(i);
                if (inst.getType() == IRElem.ADD || inst.getType() == IRElem.MINU ||
                        inst.getType() == IRElem.MULT || inst.getType() == IRElem.DIV ||
                        inst.getType() == IRElem.MOD || inst.getType() == IRElem.LSHIFT ||
                        inst.getType() == IRElem.RSHIFT || inst.getType() == IRElem.GRE ||
                        inst.getType() == IRElem.GEQ || inst.getType() == IRElem.LSS ||
                        inst.getType() == IRElem.LEQ || inst.getType() == IRElem.EQL ||
                        inst.getType() == IRElem.NEQ || inst.getType() == IRElem.LOAD ||
                        inst.getType() == IRElem.ASSIGN || inst.getType() == IRElem.CALL ||
                        inst.getType() == IRElem.GETINT || inst.getType() == IRElem.ALLOCA ||
                        inst.getType() == IRElem.RASHIFT || inst.getType() == IRElem.AND) {
                    IRSymbol op3 = inst.getOp3();
                    HashSet<DefUseNetElem> killSet = killSetByVar.getOrDefault(op3, null);
                    if (killSet == null) {
                        killSet = new HashSet<>();
                        killSetByVar.put(op3, killSet);
                    }
                    killSet.add(new DefUseNetElem(op3, block.getBlockID(), i));
                } else if (inst.getType() == IRElem.FUNC) {
                    ArrayList<IRSymbol> symbols = inst.getSymbolList();
                    for (IRSymbol symbol : symbols) {
                        HashSet<DefUseNetElem> killSet = killSetByVar.getOrDefault(symbol, null);
                        if (killSet == null) {
                            killSet = new HashSet<>();
                            killSetByVar.put(symbol, killSet);
                        }
                        killSet.add(new DefUseNetElem(symbol, block.getBlockID(), i));
                    }
                }
            }
        }
        return killSetByVar;
    }

    public HashSet<IRSymbol> getVarSymbol() {
        return varSymbol;
    }

    public HashMap<IRSymbol, HashSet<DefUseNetElem>> getAllGenMap() {
        return allGenMap;
    }
}
