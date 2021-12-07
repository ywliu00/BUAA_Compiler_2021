package Optimizer;

import IR.IRElem;
import IR.IRImmSymbol;
import IR.IRSymbol;
import IR.IRTranslater;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

public class MultDivOpt {
    private IRTranslater iRPackage;
    private HashMap<Integer, Integer> shiftBits;

    public MultDivOpt(IRTranslater iRPackage) {
        this.iRPackage = iRPackage;
        this.shiftBits = new HashMap<>();
        for (int i = 0, j = 1; i < 31; ++i) {
            this.shiftBits.put(j, i);
            j = j * 2;
        }
    }

    public void powOfTwoOpt() {
        IRElem inst;
        ArrayList<IRElem> iRList = new ArrayList<>(iRPackage.getIRList());
        for (int i = 0; i < iRList.size(); i++) {
            inst = iRList.get(i);
            if (inst.getType() == IRElem.MULT) {
                IRSymbol op1 = inst.getOp1();
                IRSymbol op2 = inst.getOp2();
                if (op1 instanceof IRImmSymbol && op2 instanceof IRImmSymbol) {
                    IRSymbol result = new IRImmSymbol(((IRImmSymbol) op1).getValue() *
                            ((IRImmSymbol) op2).getValue());
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(), result));
                } else {
                    if (op1 instanceof IRImmSymbol) {
                        IRSymbol tmp = op1;
                        op1 = op2;
                        op2 = tmp;
                        iRList.set(i, new IRElem(IRElem.MULT, inst.getOp3(), op1, op2)); // 换位置成为标准形式
                    }
                    if (op2 instanceof IRImmSymbol) {
                        int shiftBit = shiftBits.getOrDefault(((IRImmSymbol) op2).getValue(), -1);
                        if (((IRImmSymbol) op2).getValue() == 0) {
                            IRSymbol result = new IRImmSymbol(0);
                            iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(), result));
                        } else if (((IRImmSymbol) op2).getValue() == 1) { // 乘1
                            iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(), op1));
                        } else if (shiftBit != -1) {
                            IRSymbol result = new IRImmSymbol(shiftBit);
                            iRList.set(i, new IRElem(IRElem.LSHIFT, inst.getOp3(), op1, result));
                        }
                    }
                }
            } else if (inst.getType() == IRElem.DIV) {
                IRSymbol op1 = inst.getOp1();
                IRSymbol op2 = inst.getOp2();
                if (op1 instanceof IRImmSymbol && op2 instanceof IRImmSymbol) {
                    IRSymbol result = new IRImmSymbol(((IRImmSymbol) op1).getValue() /
                            ((IRImmSymbol) op2).getValue());
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(), result));
                } else if (op2 instanceof IRImmSymbol) {
                    if (((IRImmSymbol) op2).getValue() == 1) { // 除以1
                        iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(), op1));
                    } else {
                        int shiftBit = shiftBits.getOrDefault(((IRImmSymbol) op2).getValue(), -1);
                        if (shiftBit != -1) {
                            IRSymbol result = new IRImmSymbol(shiftBit);
                            iRList.set(i, new IRElem(IRElem.RASHIFT, inst.getOp3(), op1, result));
                            // 此处需要算术右移
                        }
                    }
                }
            } else if (inst.getType() == IRElem.MOD) {
                IRSymbol op1 = inst.getOp1();
                IRSymbol op2 = inst.getOp2();
                if (op1 instanceof IRImmSymbol && op2 instanceof IRImmSymbol) {
                    IRSymbol result = new IRImmSymbol(((IRImmSymbol) op1).getValue() %
                            ((IRImmSymbol) op2).getValue());
                    iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(), result));
                } else if (op2 instanceof IRImmSymbol) {
                    if (((IRImmSymbol) op2).getValue() == 1) { // 模1
                        IRSymbol result = new IRImmSymbol(0);
                        iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(), result));
                    } else {
                        int shiftBit = shiftBits.getOrDefault(((IRImmSymbol) op2).getValue(), -1);
                        if (shiftBit != -1) {
                            IRSymbol result = new IRImmSymbol(((IRImmSymbol) op2).getValue() - 1);
                            iRList.set(i, new IRElem(IRElem.AND, inst.getOp3(), op1, result));
                            // 此处AND一下就行
                        }
                    }
                }
            }
        }
        iRPackage.setiRList(new LinkedList<>(iRList));
    }
}
