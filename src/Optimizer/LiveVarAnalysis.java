package Optimizer;

import IR.IRElem;
import IR.IRFuncSymbol;
import IR.IRLabelSymbol;
import IR.IRSymbol;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;

public class LiveVarAnalysis {
    private ArrayList<BasicBlock> blockList;

    public LiveVarAnalysis(ArrayList<BasicBlock> blockList) {
        this.blockList = blockList;
    }

    public void liveVarAnalysis() { // 活跃变量分析
        blockSetCal(); // 先计算use和def集合
        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = blockList.size() - 1; i >= 0; i--) {
                BasicBlock curBlock = blockList.get(i);
                HashSet<IRSymbol> outSet = new HashSet<>();
                for (BasicBlock succBlock : curBlock.getSuccessors()) {
                    outSet.addAll(succBlock.getInSetLVA());
                }
                curBlock.setOutSetLVA(outSet);
                HashSet<IRSymbol> inSet = new HashSet<>(outSet);
                inSet.removeAll(curBlock.getDefSet());
                inSet.addAll(curBlock.getUseSet());
                if (!inSet.equals(curBlock.getInSetLVA())) { // 若有更新则in和out均更新
                    changed = true;
                    curBlock.setInSetLVA(inSet);
                }

            }
        }
    }

    public void blockSetCal() { // 计算各基本块内def和use集合
        for (BasicBlock curBlock : blockList) {
            HashSet<IRSymbol> useSet = new HashSet<>();
            HashSet<IRSymbol> defSet = new HashSet<>();
            LinkedList<IRElem> iRList = curBlock.getBlockIRList();
            for (IRElem irElem : iRList) {
                fillDefAndUseSet(irElem, useSet, defSet);
            }
            curBlock.setUseAndDefSet(useSet, defSet);
        }
    }

    public void fillDefAndUseSet(IRElem inst, HashSet<IRSymbol> useSet,
                                 HashSet<IRSymbol> defSet) {
        if (inst.getType() == IRElem.FUNC) {
            defSet.addAll(inst.getSymbolList());
        } else if (inst.getType() == IRElem.ADD || inst.getType() == IRElem.MINU ||
                inst.getType() == IRElem.MULT || inst.getType() == IRElem.DIV ||
                inst.getType() == IRElem.MOD || inst.getType() == IRElem.LSHIFT ||
                inst.getType() == IRElem.RSHIFT || inst.getType() == IRElem.GRE ||
                inst.getType() == IRElem.GEQ || inst.getType() == IRElem.LSS ||
                inst.getType() == IRElem.LEQ || inst.getType() == IRElem.EQL ||
                inst.getType() == IRElem.NEQ || inst.getType() == IRElem.LOAD) {
            IRSymbol op1 = inst.getOp1();
            IRSymbol op2 = inst.getOp2();
            IRSymbol op3 = inst.getOp3();
            if ((op1 instanceof IRLabelSymbol) && !defSet.contains(op1)) {
                useSet.add(op1);
            }
            if ((op2 instanceof IRLabelSymbol) && !defSet.contains(op2)) {
                useSet.add(op2);
            }
            if ((op3 instanceof IRLabelSymbol) && !useSet.contains(op3)) {
                defSet.add(op3);
            }
        } else if (inst.getType() == IRElem.ASSIGN) {
            IRSymbol op1 = inst.getOp1();
            IRSymbol op3 = inst.getOp3();
            if ((op1 instanceof IRLabelSymbol) && !defSet.contains(op1)) {
                useSet.add(op1);
            }
            if ((op3 instanceof IRLabelSymbol) && !useSet.contains(op3)) {
                defSet.add(op3);
            }
        } else if (inst.getType() == IRElem.BR || inst.getType() == IRElem.SETRET) {
            IRSymbol op3 = inst.getOp3();
            if ((op3 instanceof IRLabelSymbol) && !defSet.contains(op3)) {
                useSet.add(op3);
            }
        } else if (inst.getType() == IRElem.BZ || inst.getType() == IRElem.BNZ) {
            IRSymbol op1 = inst.getOp1();
            IRSymbol op3 = inst.getOp3();
            if ((op1 instanceof IRLabelSymbol) && !defSet.contains(op1)) {
                useSet.add(op1);
            }
            if ((op3 instanceof IRLabelSymbol) && !defSet.contains(op3)) {
                useSet.add(op3);
            }
        } else if (inst.getType() == IRElem.CALL) {
            IRSymbol op3 = inst.getOp3();
            if ((op3 instanceof IRLabelSymbol) && !useSet.contains(op3)) {
                defSet.add(op3);
            }
            ArrayList<IRSymbol> symbolList = inst.getSymbolList();
            for (IRSymbol symbol : symbolList) {
                if ((symbol instanceof IRLabelSymbol) && !defSet.contains(symbol)) {
                    useSet.add(symbol);
                }
            }
        } else if (inst.getType() == IRElem.STORE) {
            IRSymbol op1 = inst.getOp1();
            IRSymbol op2 = inst.getOp2();
            IRSymbol op3 = inst.getOp3();
            if ((op1 instanceof IRLabelSymbol) && !defSet.contains(op1)) {
                useSet.add(op1);
            }
            if ((op2 instanceof IRLabelSymbol) && !defSet.contains(op2)) {
                useSet.add(op2);
            }
            if ((op3 instanceof IRLabelSymbol) && !defSet.contains(op3)) {
                useSet.add(op3);
            }
        } else if (inst.getType() == IRElem.ALLOCA) {
            IRSymbol op1 = inst.getOp1();
            IRSymbol op3 = inst.getOp3();
            if ((op1 instanceof IRLabelSymbol) && !defSet.contains(op1)) {
                useSet.add(op1);
            }
            if ((op3 instanceof IRLabelSymbol) && !useSet.contains(op3)) {
                defSet.add(op3);
            }
        } else if (inst.getType() == IRElem.GETINT || inst.getType() == IRElem.LABEL) {
            IRSymbol op3 = inst.getOp3();
            if ((op3 instanceof IRLabelSymbol) && !useSet.contains(op3)) {
                defSet.add(op3);
            }
        } else if (inst.getType() == IRElem.PRINTI ||
                inst.getType() == IRElem.PRINTS) {
            IRSymbol op3 = inst.getOp3();
            if ((op3 instanceof IRLabelSymbol) && !defSet.contains(op3)) {
                useSet.add(op3);
            }
        }
    }
}
