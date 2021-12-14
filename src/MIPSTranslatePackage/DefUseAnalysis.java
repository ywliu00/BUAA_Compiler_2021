package MIPSTranslatePackage;

import IR.IRElem;
import IR.IRSymbol;
import Optimizer.BasicBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class DefUseAnalysis {
    private ArrayList<BasicBlock> blockList;

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
                    inst.getType() == IRElem.GETINT || inst.getType() == IRElem.ALLOCA) {
                IRSymbol op3 = inst.getOp3();
                DefUseNetElem currentGen = new DefUseNetElem(op3, block.getBlockID(), i);
                HashSet<DefUseNetElem> killSet = new HashSet<>(lastKill);
                killSet.addAll(killSetByVar.get(op3));
                killSet.remove(currentGen); // 不能含有当前定义
                HashSet<DefUseNetElem> genSet = new HashSet<>(lastGen);
                genSet.removeAll(killSet);
                genSet.add(currentGen);
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
                        inst.getType() == IRElem.GETINT || inst.getType() == IRElem.ALLOCA) {
                    IRSymbol op3 = inst.getOp3();
                    HashSet<DefUseNetElem> killSet = killSetByVar.getOrDefault(op3, null);
                    if (killSet == null) {
                        killSet = new HashSet<>();
                        killSetByVar.put(op3, killSet);
                    }
                    killSet.add(new DefUseNetElem(op3, block.getBlockID(), i));
                }
            }
        }
        return killSetByVar;
    }
}
