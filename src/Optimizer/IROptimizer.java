package Optimizer;

import IR.IRElem;
import IR.IRImmSymbol;
import IR.IRSymbol;
import IR.IRTranslater;
import MIPSTranslatePackage.DefUseNetElem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

public class IROptimizer {
    private IRTranslater iRPackage;
    private LinkedList<IRElem> iRList;
    private ArrayList<BasicBlock> blockList;

    public IROptimizer(IRTranslater iRPackage) {
        this.iRPackage = iRPackage;
        this.iRList = iRPackage.getIRList();
    }

    public void doOptimizeWithNewLVA() {
        PrintOpt.emptyStrOpt(iRPackage);
        for (int i = 0; i < 5; i++) {
            doJumpOptimize();
            basicBlockInit(iRList);
            ConstSpread constSpread = new ConstSpread(blockList);
            constSpread.doConstSpread();
            for (BasicBlock block : blockList) {
                block.buildDAG();
            }
            LiveVarAnalysis liveVarAnalyzer = new LiveVarAnalysis(blockList);
            liveVarAnalyzer.liveVarAnalysisWithBlock();
            for (BasicBlock block : blockList) {
                block.reArrangeInstFromDAG();
            }
            basicBlockToIRList();
            if (i == 0) {
                MultDivOpt multDivOpter = new MultDivOpt(iRPackage);
                LinkedList<IRElem> newList = multDivOpter.powOfTwoOpt();
                iRPackage.setiRList(newList);
                //this.iRList = iRPackage.getIRList();
                this.iRList = newList;
            } else {
                this.iRList = iRPackage.getIRList();
            }
            constInstCal();
        }
    }

    public void doOptimize() {
        PrintOpt.emptyStrOpt(iRPackage);
        for (int i = 0; i < 5; i++) {
            doJumpOptimize();
            basicBlockInit(iRList);
            ConstSpread constSpread = new ConstSpread(blockList);
            constSpread.doConstSpread();
            LiveVarAnalysis liveVarAnalyzer = new LiveVarAnalysis(blockList);
            liveVarAnalyzer.liveVarAnalysis();
            for (BasicBlock block : blockList) {
                block.buildDAG();
                block.reArrangeInstFromDAG();
            }
            basicBlockToIRList();
            if (i == 0) {
                MultDivOpt multDivOpter = new MultDivOpt(iRPackage);
                LinkedList<IRElem> newList = multDivOpter.powOfTwoOpt();
                iRPackage.setiRList(newList);
                //this.iRList = iRPackage.getIRList();
                this.iRList = newList;
            } else {
                this.iRList = iRPackage.getIRList();
            }
            constInstCal();
        }
    }

    public void basicBlockInit(LinkedList<IRElem> iRList) {
        ArrayList<Integer> breakPoint = buildBasicBlockBreakpoint(iRList);
        HashMap<Integer, Integer> labelBlockMap = buildBasicBlock(breakPoint, iRList);
        fillBlockLink(labelBlockMap);
    }

    public void fillBlockLink(HashMap<Integer, Integer> labelBlockMap) { // 填写基本块前驱后继
        for (int i = 0; i < blockList.size(); i++) {
            BasicBlock curBlock = blockList.get(i);
            IRElem endInst = curBlock.getBlockIRList().getLast();
            if (endInst.getType() == IRElem.BR) { // BR，唯一出口
                int destLabelID = endInst.getOp3().getId();
                int destBlockID = labelBlockMap.get(destLabelID);
                BasicBlock destBlock = blockList.get(destBlockID);
                curBlock.addSuccessor(destBlock);
                destBlock.addPredecessor(curBlock);
            } else if (endInst.getType() == IRElem.BZ ||
                    endInst.getType() == IRElem.BNZ) { // BZ、BNZ，跳转目标和下一块
                int destLabelID = endInst.getOp3().getId();
                int destBlockID = labelBlockMap.get(destLabelID);
                BasicBlock destBlock = blockList.get(destBlockID);
                curBlock.addSuccessor(destBlock);
                destBlock.addPredecessor(curBlock); // 跳转目标设置
                destBlock = blockList.get(i + 1);
                curBlock.addSuccessor(destBlock);
                destBlock.addPredecessor(curBlock); // 非跳转目标设置
            } else if (endInst.getType() == IRElem.RET ||
                    endInst.getType() == IRElem.EXIT) { // RET、EXIT，无后继
                continue;
            } else {
                if (i == blockList.size() - 1) {
                    continue;
                }
                BasicBlock destBlock = blockList.get(i + 1);
                curBlock.addSuccessor(destBlock);
                destBlock.addPredecessor(curBlock); // 下一块
            }
        }
    }

    public HashMap<Integer, Integer> buildBasicBlock(ArrayList<Integer> breakPointList,
                                                     LinkedList<IRElem> iRList) {
        ArrayList<IRElem> tempIRList = new ArrayList<>(iRList); // 转换为Arraylist加速访问
        ArrayList<BasicBlock> blockList = new ArrayList<>();
        HashMap<Integer, Integer> labelBlockIdMap = new HashMap<>();
        int start, end;
        for (int i = 1; i < breakPointList.size(); i++) {
            start = breakPointList.get(i - 1);
            end = breakPointList.get(i);
            if (end > iRList.size()) {
                break;
            }
            BasicBlock block = new BasicBlock(i - 1);
            block.addIR(tempIRList, start, end);
            for (int j = start; tempIRList.get(j).getType() == IRElem.LABEL ||
                    tempIRList.get(j).getType() == IRElem.FUNC; j++) {
                if (tempIRList.get(j).getType() == IRElem.LABEL) {
                    int labelID = tempIRList.get(j).getOp3().getId();
                    labelBlockIdMap.put(labelID, i - 1);
                }
            }
            blockList.add(block);
        }
        this.blockList = blockList;
        return labelBlockIdMap;
    }

    public static ArrayList<Integer> buildBasicBlockBreakpoint(LinkedList<IRElem> iRList) { // 得到所有基本块起点的位置
        int i = 0;
        HashSet<Integer> breakpointSet = new HashSet<>();
        HashMap<Integer, Integer> labelLineMap = new HashMap<>();
        HashSet<Integer> entrySet = new HashSet<>();
        while (i < iRList.size()) {
            if (iRList.get(i).getType() == IRElem.FUNC) {
                breakpointSet.add(i); // 函数开始作为基本块起点
                int i0 = i; // 放在一起的Label全部看作一个地址
                i += 1; // 从函数的下一句开始，应该是一个Label，统一算作该函数的起点位置
                while (i < iRList.size() && iRList.get(i).getType() == IRElem.LABEL) {
                    labelLineMap.put(iRList.get(i).getOp3().getId(), i0);
                    i += 1;
                }
            } else if (iRList.get(i).getType() == IRElem.LABEL) {
                //breakpointSet.add(i);
                int i0 = i; // 放在一起的Label全部看作一个地址
                while (i < iRList.size() && iRList.get(i).getType() == IRElem.LABEL) {
                    labelLineMap.put(iRList.get(i).getOp3().getId(), i0);
                    i += 1;
                }
            } else if (iRList.get(i).getType() == IRElem.BR ||
                    iRList.get(i).getType() == IRElem.BZ ||
                    iRList.get(i).getType() == IRElem.BNZ) {
                int labelID = iRList.get(i).getOp3().getId();
                breakpointSet.add(i + 1); // 跳转语句的下一条作为基本块起点
                entrySet.add(labelID); // 跳转目标Label编号
                i += 1;
            } else if (iRList.get(i).getType() == IRElem.RET || iRList.get(i).getType() == IRElem.EXIT) {
                breakpointSet.add(i + 1); // 函数结束的下一句作为基本块起点
                i += 1;
            } else {
                i += 1;
            }
        }
        for (int labelID : entrySet) {
            breakpointSet.add(labelLineMap.get(labelID));
        }
        breakpointSet.add(0);
        breakpointSet.add(iRList.size());
        ArrayList<Integer> breakPointList = new ArrayList<>(breakpointSet);
        Collections.sort(breakPointList);
        return breakPointList;
    }

    public void basicBlockToIRList() {
        LinkedList<IRElem> optedIRList = new LinkedList<>();
        for (BasicBlock block : blockList) {
            optedIRList.addAll(block.getBlockOptInstList());
        }
        iRList = optedIRList;
        iRPackage.setiRList(optedIRList);
    }

    public LinkedList<IRElem> doJumpOptimize() {
        JumpOpt jumpOptimizer = new JumpOpt(iRList);
        iRList = jumpOptimizer.doJumpOpt();
        return iRList;
    }

    public void constInstCal() {
        for (int i = 0; i < iRList.size(); i++) {
            IRElem inst = iRList.get(i);
            if (inst.getType() == IRElem.ADD) {
                if (inst.getOp1() instanceof IRImmSymbol && inst.getOp2() instanceof IRImmSymbol) {
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(),
                            new IRImmSymbol(((IRImmSymbol) inst.getOp1()).getValue() +
                                    ((IRImmSymbol) inst.getOp2()).getValue())));
                }
            } else if (inst.getType() == IRElem.MINU) {
                if (inst.getOp1() instanceof IRImmSymbol && inst.getOp2() instanceof IRImmSymbol) {
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(),
                            new IRImmSymbol(((IRImmSymbol) inst.getOp1()).getValue() -
                                    ((IRImmSymbol) inst.getOp2()).getValue())));
                }
            } else if (inst.getType() == IRElem.MULT) {
                if (inst.getOp1() instanceof IRImmSymbol && inst.getOp2() instanceof IRImmSymbol) {
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(),
                            new IRImmSymbol(((IRImmSymbol) inst.getOp1()).getValue() *
                                    ((IRImmSymbol) inst.getOp2()).getValue())));
                }
            } else if (inst.getType() == IRElem.DIV) {
                if (inst.getOp1() instanceof IRImmSymbol && inst.getOp2() instanceof IRImmSymbol) {
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(),
                            new IRImmSymbol(((IRImmSymbol) inst.getOp1()).getValue() /
                                    ((IRImmSymbol) inst.getOp2()).getValue())));
                }
            } else if (inst.getType() == IRElem.MOD) {
                if (inst.getOp1() instanceof IRImmSymbol && inst.getOp2() instanceof IRImmSymbol) {
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(),
                            new IRImmSymbol(((IRImmSymbol) inst.getOp1()).getValue() %
                                    ((IRImmSymbol) inst.getOp2()).getValue())));
                }
            } else if (inst.getType() == IRElem.LSHIFT) {
                if (inst.getOp1() instanceof IRImmSymbol && inst.getOp2() instanceof IRImmSymbol) {
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(),
                            new IRImmSymbol(((IRImmSymbol) inst.getOp1()).getValue() <<
                                    ((IRImmSymbol) inst.getOp2()).getValue())));
                }
            } else if (inst.getType() == IRElem.RSHIFT) {
                if (inst.getOp1() instanceof IRImmSymbol && inst.getOp2() instanceof IRImmSymbol) {
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(),
                            new IRImmSymbol(((IRImmSymbol) inst.getOp1()).getValue() >>
                                    ((IRImmSymbol) inst.getOp2()).getValue())));
                }
            } else if (inst.getType() == IRElem.RASHIFT) {
                if (inst.getOp1() instanceof IRImmSymbol && inst.getOp2() instanceof IRImmSymbol) {
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(),
                            new IRImmSymbol(((IRImmSymbol) inst.getOp1()).getValue() >>>
                                    ((IRImmSymbol) inst.getOp2()).getValue())));
                }
            } else if (inst.getType() == IRElem.AND) {
                if (inst.getOp1() instanceof IRImmSymbol && inst.getOp2() instanceof IRImmSymbol) {
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(),
                            new IRImmSymbol(((IRImmSymbol) inst.getOp1()).getValue() &
                                    ((IRImmSymbol) inst.getOp2()).getValue())));
                }
            } else if (inst.getType() == IRElem.GRE) {
                if (inst.getOp1() instanceof IRImmSymbol && inst.getOp2() instanceof IRImmSymbol) {
                    int res = 0;
                    if (((IRImmSymbol) inst.getOp1()).getValue() > ((IRImmSymbol) inst.getOp2()).getValue()) {
                        res = 1;
                    }
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(),
                            new IRImmSymbol(res)));
                }
            } else if (inst.getType() == IRElem.GEQ) {
                if (inst.getOp1() instanceof IRImmSymbol && inst.getOp2() instanceof IRImmSymbol) {
                    int res = 0;
                    if (((IRImmSymbol) inst.getOp1()).getValue() >= ((IRImmSymbol) inst.getOp2()).getValue()) {
                        res = 1;
                    }
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(),
                            new IRImmSymbol(res)));
                }
            } else if (inst.getType() == IRElem.LSS) {
                if (inst.getOp1() instanceof IRImmSymbol && inst.getOp2() instanceof IRImmSymbol) {
                    int res = 0;
                    if (((IRImmSymbol) inst.getOp1()).getValue() < ((IRImmSymbol) inst.getOp2()).getValue()) {
                        res = 1;
                    }
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(),
                            new IRImmSymbol(res)));
                }
            } else if (inst.getType() == IRElem.LEQ) {
                if (inst.getOp1() instanceof IRImmSymbol && inst.getOp2() instanceof IRImmSymbol) {
                    int res = 0;
                    if (((IRImmSymbol) inst.getOp1()).getValue() <= ((IRImmSymbol) inst.getOp2()).getValue()) {
                        res = 1;
                    }
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(),
                            new IRImmSymbol(res)));
                }
            } else if (inst.getType() == IRElem.EQL) {
                if (inst.getOp1() instanceof IRImmSymbol && inst.getOp2() instanceof IRImmSymbol) {
                    int res = 0;
                    if (((IRImmSymbol) inst.getOp1()).getValue() == ((IRImmSymbol) inst.getOp2()).getValue()) {
                        res = 1;
                    }
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(),
                            new IRImmSymbol(res)));
                }
            } else if (inst.getType() == IRElem.NEQ) {
                if (inst.getOp1() instanceof IRImmSymbol && inst.getOp2() instanceof IRImmSymbol) {
                    int res = 0;
                    if (((IRImmSymbol) inst.getOp1()).getValue() != ((IRImmSymbol) inst.getOp2()).getValue()) {
                        res = 1;
                    }
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(),
                            new IRImmSymbol(res)));
                }
            }
        }
    }

    public ArrayList<BasicBlock> getBlockList() {
        return blockList;
    }

    public StringBuilder printBasicBlock(boolean isOpted, ArrayList<BasicBlock> blockList) {
        StringBuilder outStr = new StringBuilder(".text:\n");
        for (BasicBlock block : blockList) {
            ArrayList<BasicBlock> predecessorList = block.getPredecessors();
            outStr.append("Block ").append(block.getBlockID()).append(": Predecessors: ");
            for (BasicBlock block1 : predecessorList) {
                outStr.append(block1.getBlockID()).append(" ");
            }
            outStr.append("\n");
            LinkedList<IRElem> irList;
            if (!isOpted) {
                irList = block.getBlockIRList();
            } else {
                irList = block.getBlockOptInstList();
            }
            for (IRElem inst : irList) {
                outStr.append(inst).append("\n");
            }
            ArrayList<BasicBlock> successorList = block.getSuccessors();
            outStr.append("Successors: ");
            for (BasicBlock block1 : successorList) {
                outStr.append(block1.getBlockID()).append(" ");
            }
            outStr.append("\n");
            HashSet<IRSymbol> useSet = block.getUseSet();
            outStr.append("use set: ");
            for (IRSymbol symbol : useSet) {
                outStr.append("#").append(symbol.getId()).append(' ');
            }
            outStr.append("\n");
            HashSet<IRSymbol> defSet = block.getDefSet();
            outStr.append("def set: ");
            for (IRSymbol symbol : defSet) {
                outStr.append("#").append(symbol.getId()).append(' ');
            }
            outStr.append("\n");
            HashSet<IRSymbol> outSetLVA = block.getOutSetLVA();
            outStr.append("LVA out set: ");
            for (IRSymbol symbol : outSetLVA) {
                outStr.append("#").append(symbol.getId()).append(' ');
            }
            outStr.append("\n");
            HashSet<IRSymbol> inSetLVA = block.getInSetLVA();
            outStr.append("LVA in set: ");
            for (IRSymbol symbol : inSetLVA) {
                outStr.append("#").append(symbol.getId()).append(' ');
            }
            outStr.append("\n");
            outStr.append("Gen set: ");
            HashSet<DefUseNetElem> genSet = block.getGenSet();
            for (DefUseNetElem elem : genSet) {
                outStr.append(elem).append(' ');
            }
            outStr.append("\n");
            outStr.append("Kill set: ");
            HashSet<DefUseNetElem> killSet = block.getKillSet();
            for (DefUseNetElem elem : killSet) {
                outStr.append(elem).append(' ');
            }
            outStr.append("\n");
            outStr.append("DG in set: ");
            HashSet<DefUseNetElem> inSetDG = block.getInSetDG();
            for (DefUseNetElem elem : inSetDG) {
                outStr.append(elem).append(' ');
            }
            outStr.append("\n");
            outStr.append("DG out set: ");
            HashSet<DefUseNetElem> outSetDG = block.getOutSetDG();
            for (DefUseNetElem elem : outSetDG) {
                outStr.append(elem).append(' ');
            }
            outStr.append("\n");
            outStr.append("\n");
        }
        return outStr;
    }

}
