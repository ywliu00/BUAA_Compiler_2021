package MIPSTranslatePackage;

import IR.IRElem;
import IR.IRLabelManager;
import IR.IRSymbol;
import IR.IRTranslater;
import Optimizer.BasicBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class ArrDefNet {
    private HashMap<IRSymbol, ArrayList<DefUseNet>> symbolNetListMap;
    private HashMap<IRSymbol, DefUseNet> symbolNetMap;
    private HashSet<IRSymbol> varSymbols;
    private DefUseAnalysis defUseAnalyzer;
    private ArrayList<BasicBlock> blockList;
    private HashMap<IRSymbol, HashSet<DefUseNetElem>> allGenMap;
    private IRLabelManager labelManager;

    public ArrDefNet(ArrayList<BasicBlock> blockList, DefUseAnalysis analyzer) {
        this.defUseAnalyzer = analyzer;
        this.symbolNetListMap = new HashMap<>();
        this.varSymbols = defUseAnalyzer.getVarSymbol();
        this.symbolNetMap = new HashMap<>();
        this.blockList = blockList;
        this.allGenMap = analyzer.getAllGenMap();
        this.labelManager = IRLabelManager.getIRLabelManager();
    }

    public void buildNet() {
        for (IRSymbol symbol : varSymbols) {
            HashSet<DefUseNetElem> symbolGenSet = allGenMap.get(symbol);
            ArrayList<DefUseNet> netList = new ArrayList<>();
            symbolNetListMap.put(symbol, netList);
            for (DefUseNetElem elem : symbolGenSet) { // 对每个定义点
                DefUseNet curNet = new DefUseNet();
                curNet.addDef(elem);
                HashSet<Integer> mergeNum = new HashSet<>(); // 合并ID表，空则表示不合并
                for (BasicBlock block : blockList) {
                    int i = 0;
                    if (block.getBlockID() != elem.getBlockID()) {
                        if (!block.getInSetDG().contains(elem)) { // 不可达且非定义块
                            continue;
                        } else if (!block.getUseSet().contains(symbol)) { // 可达，但没用到
                            continue;
                        }
                    } else {
                        i = elem.getLineNo() + 1; // 定义块，从定义下一条算起
                    }
                    ArrayList<IRElem> blockIRList = new ArrayList<>(block.getBlockIRList());
                    for (; i < blockIRList.size(); i++) {
                        IRElem inst = blockIRList.get(i);
                        if (inst.getType() == IRElem.FUNC) {
                            continue;
                        } else if (inst.getType() == IRElem.ADD || inst.getType() == IRElem.MINU ||
                                inst.getType() == IRElem.MULT || inst.getType() == IRElem.DIV ||
                                inst.getType() == IRElem.MOD || inst.getType() == IRElem.LSHIFT ||
                                inst.getType() == IRElem.RSHIFT || inst.getType() == IRElem.GRE ||
                                inst.getType() == IRElem.GEQ || inst.getType() == IRElem.LSS ||
                                inst.getType() == IRElem.LEQ || inst.getType() == IRElem.EQL ||
                                inst.getType() == IRElem.NEQ || inst.getType() == IRElem.LOAD ||
                                inst.getType() == IRElem.RASHIFT || inst.getType() == IRElem.AND) {
                            IRSymbol op1 = inst.getOp1();
                            IRSymbol op2 = inst.getOp2();
                            IRSymbol op3 = inst.getOp3();
                            if (op1 == symbol || op2 == symbol) { // 使用，加入网
                                DefUseNetElem newElem = new DefUseNetElem(symbol, block.getBlockID(), i);
                                curNet.add(newElem);
                                for (int j = 0; j < netList.size(); j++) {
                                    if (netList.get(j).contains(newElem)) {
                                        mergeNum.add(j);
                                    }
                                }
                            }
                            if (op3 == symbol) { // 新定义点，直接退出
                                break;
                            }
                        } else if (inst.getType() == IRElem.ASSIGN) {
                            IRSymbol op1 = inst.getOp1();
                            IRSymbol op3 = inst.getOp3();
                            if (op1 == symbol) { // 使用，加入网
                                DefUseNetElem newElem = new DefUseNetElem(symbol, block.getBlockID(), i);
                                curNet.add(newElem);
                                for (int j = 0; j < netList.size(); j++) {
                                    if (netList.get(j).contains(newElem)) {
                                        mergeNum.add(j);
                                    }
                                }
                            }
                            if (op3 == symbol) { // 新定义点，直接退出
                                break;
                            }
                        } else if (inst.getType() == IRElem.BR || inst.getType() == IRElem.SETRET) {
                            IRSymbol op3 = inst.getOp3();
                            if (op3 == symbol) { // 使用，加入网
                                DefUseNetElem newElem = new DefUseNetElem(symbol, block.getBlockID(), i);
                                curNet.add(newElem);
                                for (int j = 0; j < netList.size(); j++) {
                                    if (netList.get(j).contains(newElem)) {
                                        mergeNum.add(j);
                                    }
                                }
                            }
                        } else if (inst.getType() == IRElem.BZ || inst.getType() == IRElem.BNZ) {
                            IRSymbol op1 = inst.getOp1();
                            IRSymbol op3 = inst.getOp3();
                            if (op1 == symbol || op3 == symbol) { // 使用，加入网
                                DefUseNetElem newElem = new DefUseNetElem(symbol, block.getBlockID(), i);
                                curNet.add(newElem);
                                for (int j = 0; j < netList.size(); j++) {
                                    if (netList.get(j).contains(newElem)) {
                                        mergeNum.add(j);
                                    }
                                }
                            }
                        } else if (inst.getType() == IRElem.CALL) {
                            ArrayList<IRSymbol> symbolList = inst.getSymbolList();
                            for (IRSymbol tmpSymbol : symbolList) {
                                if (tmpSymbol == symbol) { // 使用，加入网
                                    DefUseNetElem newElem = new DefUseNetElem(symbol, block.getBlockID(), i);
                                    curNet.add(newElem);
                                    for (int j = 0; j < netList.size(); j++) {
                                        if (netList.get(j).contains(newElem)) {
                                            mergeNum.add(j);
                                        }
                                    }
                                }
                            }
                            IRSymbol op3 = inst.getOp3();
                            if (op3 == symbol) { // 新定义点，直接退出
                                break;
                            }
                        } else if (inst.getType() == IRElem.STORE) {
                            IRSymbol op1 = inst.getOp1();
                            IRSymbol op2 = inst.getOp2();
                            IRSymbol op3 = inst.getOp3();
                            if (op1 == symbol || op2 == symbol || op3 == symbol) { // 使用，加入网
                                DefUseNetElem newElem = new DefUseNetElem(symbol, block.getBlockID(), i);
                                curNet.add(newElem);
                                for (int j = 0; j < netList.size(); j++) {
                                    if (netList.get(j).contains(newElem)) {
                                        mergeNum.add(j);
                                    }
                                }
                            }
                        } else if (inst.getType() == IRElem.ALLOCA) {
                            IRSymbol op1 = inst.getOp1();
                            IRSymbol op3 = inst.getOp3();
                            if (op1 == symbol) { // 使用，加入网
                                DefUseNetElem newElem = new DefUseNetElem(symbol, block.getBlockID(), i);
                                curNet.add(newElem);
                                for (int j = 0; j < netList.size(); j++) {
                                    if (netList.get(j).contains(newElem)) {
                                        mergeNum.add(j);
                                    }
                                }
                            }
                            if (op3 == symbol) { // 新定义点，直接退出
                                break;
                            }
                        } else if (inst.getType() == IRElem.GETINT) {
                            IRSymbol op3 = inst.getOp3();
                            if (op3 == symbol) { // 新定义点，直接退出
                                break;
                            }
                        } else if (inst.getType() == IRElem.PRINTI) {
                            IRSymbol op3 = inst.getOp3();
                            if (op3 == symbol) { // 使用，加入网
                                DefUseNetElem newElem = new DefUseNetElem(symbol, block.getBlockID(), i);
                                curNet.add(newElem);
                                for (int j = 0; j < netList.size(); j++) {
                                    if (netList.get(j).contains(newElem)) {
                                        mergeNum.add(j);
                                    }
                                }
                            }
                        }
                    }
                }
                if (!mergeNum.isEmpty()) {
                    ArrayList<DefUseNet> mergeSets = new ArrayList<>();
                    for (int j : mergeNum) {
                        mergeSets.add(netList.get(j));
                        curNet.merge(netList.get(j));
                    }
                    for (DefUseNet delSet : mergeSets) {
                        netList.remove(delSet);
                    }
                }
                netList.add(curNet);
            }
        }
    }

    public void netVarRename() {
        for (IRSymbol symbol : symbolNetListMap.keySet()) {
            ArrayList<DefUseNet> netList = symbolNetListMap.get(symbol);
            if (netList.size() == 1) {
                symbolNetMap.put(symbol, netList.get(0));
            } else if (netList.size() > 1) {
                symbolNetMap.put(symbol, netList.get(0));
                for (int i = 1; i < netList.size(); i++) {
                    renameNet(netList.get(i), symbol);
                }
            }
        }
    }

    public void renameNet(DefUseNet net, IRSymbol formerSymbol) {
        HashSet<DefUseNetElem> body = net.getNetBody();
        HashSet<DefUseNetElem> defPart = net.getDefPart();
        IRSymbol newSymbol = labelManager.allocSymbol();
        for (DefUseNetElem elem : body) {
            if (defPart.contains(elem)) { // 定义点更名，必然在Op3
                BasicBlock block = blockList.get(elem.getBlockID());
                IRElem inst = block.getBlockIRList().get(elem.getLineNo());
                if (inst.getOp3() == formerSymbol) {
                    inst.setOp3(newSymbol);
                    block.getBlockIRList().set(elem.getLineNo(), inst);
                }
            } else { // 使用点更名
                BasicBlock block = blockList.get(elem.getBlockID());
                IRElem inst = block.getBlockIRList().get(elem.getLineNo());
                if (inst.getType() == IRElem.ADD || inst.getType() == IRElem.MINU ||
                        inst.getType() == IRElem.MULT || inst.getType() == IRElem.DIV ||
                        inst.getType() == IRElem.MOD || inst.getType() == IRElem.LSHIFT ||
                        inst.getType() == IRElem.RSHIFT || inst.getType() == IRElem.GRE ||
                        inst.getType() == IRElem.GEQ || inst.getType() == IRElem.LSS ||
                        inst.getType() == IRElem.LEQ || inst.getType() == IRElem.EQL ||
                        inst.getType() == IRElem.NEQ || inst.getType() == IRElem.LOAD ||
                        inst.getType() == IRElem.RASHIFT || inst.getType() == IRElem.AND) {
                    IRSymbol op1 = inst.getOp1();
                    IRSymbol op2 = inst.getOp2();
                    if (op1 == formerSymbol) {
                        inst.setOp1(newSymbol);
                    }
                    if (op2 == formerSymbol) {
                        inst.setOp2(newSymbol);
                    }
                    block.getBlockIRList().set(elem.getLineNo(), inst);
                } else if (inst.getType() == IRElem.ASSIGN) {
                    IRSymbol op1 = inst.getOp1();
                    if (op1 == formerSymbol) {
                        inst.setOp1(newSymbol);
                    }
                    block.getBlockIRList().set(elem.getLineNo(), inst);
                } else if (inst.getType() == IRElem.BR || inst.getType() == IRElem.SETRET) {
                    IRSymbol op3 = inst.getOp3();
                    if (op3 == formerSymbol) {
                        inst.setOp3(newSymbol);
                    }
                    block.getBlockIRList().set(elem.getLineNo(), inst);
                } else if (inst.getType() == IRElem.BZ || inst.getType() == IRElem.BNZ) {
                    IRSymbol op1 = inst.getOp1();
                    IRSymbol op3 = inst.getOp3();
                    if (op1 == formerSymbol) {
                        inst.setOp1(newSymbol);
                    }
                    if (op3 == formerSymbol) {
                        inst.setOp3(newSymbol);
                    }
                    block.getBlockIRList().set(elem.getLineNo(), inst);
                } else if (inst.getType() == IRElem.CALL) {
                    ArrayList<IRSymbol> symbolList = inst.getSymbolList();
                    for (int i = 0; i < symbolList.size(); i++) {
                        IRSymbol tmpSymbol = symbolList.get(i);
                        if (tmpSymbol == formerSymbol) {
                            symbolList.set(i, newSymbol);
                        }
                    }
                    block.getBlockIRList().set(elem.getLineNo(), inst);
                } else if (inst.getType() == IRElem.STORE) {
                    IRSymbol op1 = inst.getOp1();
                    IRSymbol op2 = inst.getOp2();
                    IRSymbol op3 = inst.getOp3();
                    if (op1 == formerSymbol) {
                        inst.setOp1(newSymbol);
                    }
                    if (op2 == formerSymbol) {
                        inst.setOp2(newSymbol);
                    }
                    if (op3 == formerSymbol) {
                        inst.setOp3(newSymbol);
                    }
                    block.getBlockIRList().set(elem.getLineNo(), inst);
                } else if (inst.getType() == IRElem.ALLOCA) {
                    IRSymbol op1 = inst.getOp1();
                    if (op1 == formerSymbol) {
                        inst.setOp1(newSymbol);
                    }
                    block.getBlockIRList().set(elem.getLineNo(), inst);
                } else if (inst.getType() == IRElem.PRINTI) {
                    IRSymbol op3 = inst.getOp3();
                    if (op3 == formerSymbol) {
                        inst.setOp3(newSymbol);
                    }
                    block.getBlockIRList().set(elem.getLineNo(), inst);
                }
            }
            elem.setSymbol(newSymbol);
        }
        symbolNetMap.put(newSymbol, net);
    }

    public HashMap<IRSymbol, DefUseNet> getSymbolNetMap() {
        return symbolNetMap;
    }
}
