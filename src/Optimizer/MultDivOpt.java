package Optimizer;

import IR.IRElem;
import IR.IRImmSymbol;
import IR.IRLabelManager;
import IR.IRSymbol;
import IR.IRTranslater;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

public class MultDivOpt {
    private IRTranslater iRPackage;
    private HashMap<Integer, Integer> shiftBits;
    private HashMap<Integer, ArrayList<IRElem>> addList;
    private IRLabelManager labelManager;

    public MultDivOpt(IRTranslater iRPackage) {
        this.iRPackage = iRPackage;
        this.shiftBits = new HashMap<>();
        for (int i = 0, j = 1; i < 31; ++i) {
            this.shiftBits.put(j, i);
            j = j * 2;
        }
        this.addList = new HashMap<>();
        this.labelManager = IRLabelManager.getIRLabelManager();
    }

    public LinkedList<IRElem> powOfTwoOpt() {
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
                            //iRList.set(i, new IRElem(IRElem.RASHIFT, inst.getOp3(), op1, result));
                            // 此处需要算术右移

                            IRSymbol judgeSymbol = labelManager.allocSymbol();
                            ArrayList<IRElem> replaceInstList = new ArrayList<>();
                            replaceInstList.add(new IRElem(IRElem.GEQ, judgeSymbol, op1, IRImmSymbol.ZERO));
                            IRSymbol leqZero = labelManager.allocSymbol();
                            IRSymbol endSymbol = labelManager.allocSymbol();
                            replaceInstList.add(new IRElem(IRElem.BZ, leqZero, judgeSymbol)); // 不大于0则跳转到原来的地方
                            replaceInstList.add(new IRElem(IRElem.RASHIFT, inst.getOp3(), op1, result)); // 大于等于0，可以算术右移
                            replaceInstList.add(new IRElem(IRElem.BR, endSymbol)); // 算完结束
                            replaceInstList.add(new IRElem(IRElem.LABEL, leqZero)); // 小于0从此开始
                            replaceInstList.add(inst); // 按原指令算
                            replaceInstList.add(new IRElem(IRElem.LABEL, endSymbol)); // 结束标签
                            addList.put(i, replaceInstList);
                        }
                    }
                } else if (op1 instanceof IRImmSymbol) {
                    if (((IRImmSymbol) op1).getValue() == 0) { // 0除以任何数
                        iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(), op1));
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
                            //iRList.set(i, new IRElem(IRElem.AND, inst.getOp3(), op1, result));
                            // 此处AND一下就行
                            IRSymbol judgeSymbol = labelManager.allocSymbol();
                            ArrayList<IRElem> replaceInstList = new ArrayList<>();
                            replaceInstList.add(new IRElem(IRElem.GEQ, judgeSymbol, op1, IRImmSymbol.ZERO));
                            IRSymbol leqZero = labelManager.allocSymbol();
                            IRSymbol endSymbol = labelManager.allocSymbol();
                            replaceInstList.add(new IRElem(IRElem.BZ, leqZero, judgeSymbol)); // 不大于0则跳转到原来的地方
                            replaceInstList.add(new IRElem(IRElem.AND, inst.getOp3(), op1, result)); // 大于等于0，可以直接AND
                            replaceInstList.add(new IRElem(IRElem.BR, endSymbol)); // 算完结束
                            replaceInstList.add(new IRElem(IRElem.LABEL, leqZero)); // 小于0从此开始
                            replaceInstList.add(inst); // 按原指令算
                            replaceInstList.add(new IRElem(IRElem.LABEL, endSymbol)); // 结束标签
                            addList.put(i, replaceInstList);
                        }
                    }
                } else if (op1 instanceof IRImmSymbol) {
                    if (((IRImmSymbol) op1).getValue() == 0) { // 0 mod任何数
                        iRList.set(i, new IRElem(IRElem.ASSIGN, inst.getOp3(), op1));
                    }
                }
            }
        }
        LinkedList<IRElem> newIRList = new LinkedList<>();
        for (int i = 0; i < iRList.size(); i++) {
            ArrayList<IRElem> replaceList = addList.getOrDefault(i, null);
            if (replaceList != null) {
                newIRList.addAll(replaceList);
            } else {
                newIRList.add(iRList.get(i));
            }
        }
        //iRPackage.setiRList(new LinkedList<>(iRList));
        iRPackage.setiRList(newIRList);
        return newIRList;
    }
}
