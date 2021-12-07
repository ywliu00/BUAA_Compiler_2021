import IR.IRElem;
import IR.IRFuncSymbol;
import IR.IRImmSymbol;
import IR.IRLabelManager;
import IR.IRLabelSymbol;
import IR.IRSymbol;
import IR.IRTranslater;
import Symbols.VarSymbol;
import SyntaxClasses.SyntaxClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

public class MIPSTranslater {
    private SyntaxClass compUnit;
    private HashMap<VarSymbol, IRSymbol> constantArrMap; // 常量数组无法消干净
    private HashMap<VarSymbol, IRSymbol> globalArrMap; // 全局数组
    private HashMap<IRSymbol, String> formatStrMap; // 格式字符串作常量存
    private LinkedList<IRElem> iRList;
    private IRLabelManager iRLabelManager;
    private IRSymbol mainFunc;
    private HashMap<IRSymbol, Integer> iRAddrMap; //
    private HashSet<IRSymbol> globalLabelSet; // 全局标签集合
    private int curPos;
    private HashMap<String, FunctionTemplate> functionTemplateTable;
    private FunctionTemplate curFunc;

    public MIPSTranslater(IRTranslater irTranslater) {
        this.compUnit = irTranslater.getCompUnit();
        this.constantArrMap = irTranslater.getConstantArrMap();
        this.globalArrMap = irTranslater.getGlobalArrMap();
        this.formatStrMap = irTranslater.getFormatStrMap();
        this.iRList = irTranslater.getIRList();
        this.iRLabelManager = IRLabelManager.getIRLabelManager();
        this.mainFunc = irTranslater.getMainFunc();
        this.globalLabelSet = new HashSet<>();
        this.functionTemplateTable = new HashMap<>();

        this.iRAddrMap = new HashMap<>();
        this.curPos = 0;
        this.curFunc = FunctionTemplate.GLOBAL;

        this.globalLabelSet.addAll(formatStrMap.keySet());
        this.globalLabelSet.addAll(constantArrMap.values());
        this.globalLabelSet.addAll(globalArrMap.values());
    }

    public void buildFunctionTemplate() {
        for (IRElem inst : iRList) {
            if (inst.getType() == IRElem.FUNC) {
                FunctionTemplate newFuncTempl = new FunctionTemplate((IRFuncSymbol) inst.getOp3());
                functionTemplateTable.put(newFuncTempl.getFuncName(), newFuncTempl);
                this.curFunc = newFuncTempl;
            } else if (inst.getType() == IRElem.RET || inst.getType() == IRElem.EXIT) {
                this.curFunc = FunctionTemplate.GLOBAL;
            } else if (inst.getType() == IRElem.ADD || inst.getType() == IRElem.MINU ||
                    inst.getType() == IRElem.MULT || inst.getType() == IRElem.DIV ||
                    inst.getType() == IRElem.MOD || inst.getType() == IRElem.AND ||
                    inst.getType() == IRElem.LSHIFT || inst.getType() == IRElem.RSHIFT ||
                    inst.getType() == IRElem.RASHIFT ||
                    inst.getType() == IRElem.GRE || inst.getType() == IRElem.GEQ ||
                    inst.getType() == IRElem.LSS || inst.getType() == IRElem.LEQ ||
                    inst.getType() == IRElem.EQL || inst.getType() == IRElem.NEQ ||
                    inst.getType() == IRElem.LOAD || inst.getType() == IRElem.STORE) { // 算术指令
                checkSymbol(inst.getOp3());
                checkSymbol(inst.getOp1());
                checkSymbol(inst.getOp2());
            } else if (inst.getType() == IRElem.ASSIGN) { // assign指令
                checkSymbol(inst.getOp3());
                checkSymbol(inst.getOp1());
            } else if (inst.getType() == IRElem.ALLOCA) { // alloca指令
                checkSymbol(inst.getOp3());
                int size = ((IRImmSymbol) inst.getOp1()).getValue(); // 这里已经是字节数了
                curFunc.alloca(round(size)); // 按4字节取整
            } else if (inst.getType() == IRElem.BZ || inst.getType() == IRElem.BNZ) {
                checkSymbol(inst.getOp1());
            } else if (inst.getType() == IRElem.SETRET) {
                checkSymbol(inst.getOp3());
            } else if (inst.getType() == IRElem.CALL) {
                checkSymbol(inst.getOp3());
                for (IRSymbol symbol : inst.getSymbolList()) {
                    checkSymbol(symbol);
                }
            } else if (inst.getType() == IRElem.GETINT || inst.getType() == IRElem.PRINTI) {
                checkSymbol(inst.getOp3());
            }
        }
    }

    public void checkSymbol(IRSymbol symbol) {
        if (symbol instanceof IRImmSymbol) {
            return;
        }
        if (!curFunc.isLocalSymbol(symbol) && !globalLabelSet.contains(symbol)) {
            curFunc.addLocalSymbol(symbol);
        }
    }

    public StringBuilder iRTranslate() {
        buildFunctionTemplate();
        StringBuilder outStr = outputDataSegment();
        outStr.append(instTranslate());
        return outStr;
    }

    public StringBuilder instTranslate() {
        StringBuilder outStr = new StringBuilder("\n\n.text\n");
        for (IRElem inst : iRList) {
            if (inst.getType() == IRElem.FUNC) {
                curFunc = functionTemplateTable.get(((IRFuncSymbol) inst.getOp3()).getFunc());
                outStr.append("Func_").append(((IRFuncSymbol) inst.getOp3()).getFunc()).append(":\n");
                outStr.append("sw $ra, 0($sp)\n"); // 保存返回位置
            } else if (inst.getType() == IRElem.ADD || inst.getType() == IRElem.MINU ||
                    inst.getType() == IRElem.MULT || inst.getType() == IRElem.DIV ||
                    inst.getType() == IRElem.MOD || inst.getType() == IRElem.LSHIFT ||
                    inst.getType() == IRElem.RSHIFT || inst.getType() == IRElem.RASHIFT ||
                    inst.getType() == IRElem.AND) {
                outStr.append(arithmeticTranslate(inst));
            } else if (inst.getType() == IRElem.GRE || inst.getType() == IRElem.GEQ ||
                    inst.getType() == IRElem.LSS || inst.getType() == IRElem.LEQ ||
                    inst.getType() == IRElem.EQL || inst.getType() == IRElem.NEQ) {
                outStr.append(logicalTranslate(inst));
            } else if (inst.getType() == IRElem.ASSIGN) {
                outStr.append(assignTranslate(inst));
            } else if (inst.getType() == IRElem.BR || inst.getType() == IRElem.BZ ||
                    inst.getType() == IRElem.BNZ || inst.getType() == IRElem.SETRET ||
                    inst.getType() == IRElem.RET || inst.getType() == IRElem.CALL ||
                    inst.getType() == IRElem.EXIT) {
                outStr.append(controlTranslate(inst));
                if (inst.getType() == IRElem.RET || inst.getType() == IRElem.EXIT) {
                    curFunc = FunctionTemplate.GLOBAL;
                }
            } else if (inst.getType() == IRElem.LOAD || inst.getType() == IRElem.STORE ||
                    inst.getType() == IRElem.ALLOCA) {
                outStr.append(memInstTranslate(inst));
            } else if (inst.getType() == IRElem.GETINT || inst.getType() == IRElem.PRINTI ||
                    inst.getType() == IRElem.PRINTS) {
                outStr.append(ioInstTranslate(inst));
            } else if (inst.getType() == IRElem.LABEL) {
                outStr.append("L").append(inst.getOp3().getId()).append(":\n");
            }
        }
        return outStr;
    }

    public StringBuilder loadSymbolToRegister(IRSymbol irSymbol, String regName) {
        StringBuilder outStr = new StringBuilder();
        if (globalLabelSet.contains(irSymbol)) {
            outStr.append("la $").append(regName).append(", L").
                    append(irSymbol.getId()).append("\n");
        } else if (irSymbol instanceof IRImmSymbol) {
            outStr.append("li $").append(regName).append(", ").
                    append(((IRImmSymbol) irSymbol).getValue()).append("\n");
        } else {
            /*int addr = iRAddrMap.getOrDefault(irSymbol, -1);
            if (addr != -1) {
                outStr.append("lw $").append(regName).append(", ").append(-addr).append("($sp)\n");
            } else {
                curPos += 4;
                iRAddrMap.put(irSymbol, curPos);
                outStr.append("lw $").append(regName).append(", ").append(-curPos).append("($sp)\n");
            }*/
            int addr = curFunc.getSymbolOffset(irSymbol);
            outStr.append("lw $").append(regName).append(", ").append(-addr).append("($sp)\n");
        }
        return outStr;
    }

    public StringBuilder storeRegisterToSymbol(IRSymbol irSymbol, String regName) {
        StringBuilder outStr = new StringBuilder();
        if (globalLabelSet.contains(irSymbol)) {
            outStr.append("sw $").append(regName).append(", L").append(
                    irSymbol.getId()).append("\n");
        } else if (irSymbol instanceof IRImmSymbol) {
            outStr.append("sw $").append(regName).append(", ").append(
                    ((IRImmSymbol) irSymbol).getValue()).append("\n");
        } else {
            /*int addr = iRAddrMap.getOrDefault(irSymbol, -1);
            if (addr != -1) {
                outStr.append("sw $").append(regName).append(", ").append(-addr).append("($sp)\n");
            } else {
                curPos += 4;
                iRAddrMap.put(irSymbol, curPos);
                outStr.append("sw $").append(regName).append(", ").append(-curPos).append("($sp)\n");
            }*/
            int addr = curFunc.getSymbolOffset(irSymbol);
            outStr.append("sw $").append(regName).append(", ").append(-addr).append("($sp)\n");
        }
        return outStr;
    }

    public StringBuilder loadOp3Symbol(IRSymbol irSymbol) {
        return loadSymbolToRegister(irSymbol, "t0");
    }

    public StringBuilder loadOp1Symbol(IRSymbol irSymbol) {
        return loadSymbolToRegister(irSymbol, "t1");
    }

    public StringBuilder loadOp2Symbol(IRSymbol irSymbol) {
        return loadSymbolToRegister(irSymbol, "t2");
    }

    public StringBuilder storeOp3Symbol(IRSymbol irSymbol) { // 把$t0存了
        return storeRegisterToSymbol(irSymbol, "t0");
    }

    public StringBuilder arithmeticTranslate(IRElem arthInst) {
        StringBuilder outInst = new StringBuilder();
        outInst.append(loadOp1Symbol(arthInst.getOp1()));
        outInst.append(loadOp2Symbol(arthInst.getOp2()));
        switch (arthInst.getType()) {
            case IRElem.ADD:
                outInst.append("addu $t0, $t1, $t2");
                break;
            case IRElem.MINU:
                outInst.append("subu $t0, $t1, $t2");
                break;
            case IRElem.MULT:
                outInst.append("mult $t1, $t2").append("\n");
                outInst.append("mflo $t0");
                break;
            case IRElem.DIV:
                outInst.append("div $t1, $t2").append("\n");
                outInst.append("mflo $t0");
                break;
            case IRElem.MOD:
                outInst.append("div $t1, $t2").append("\n");
                outInst.append("mfhi $t0");
                break;
            case IRElem.LSHIFT:
                outInst.append("sllv $t0, $t1, $t2");
                break;
            case IRElem.RSHIFT:
                outInst.append("srlv $t0, $t1, $t2");
                break;
            case IRElem.RASHIFT:
                outInst.append("srav $t0, $t1, $t2");
                break;
            case IRElem.AND:
                outInst.append("and $t0, $t1, $t2");
                break;
        }
        outInst.append("\n");
        outInst.append(storeOp3Symbol(arthInst.getOp3()));
        return outInst;
    }

    public StringBuilder assignTranslate(IRElem assignInst) {
        StringBuilder outInst = new StringBuilder();
        IRSymbol op1 = assignInst.getOp1();
        if (op1 instanceof IRImmSymbol) {
            outInst.append("li $t0, ").append(((IRImmSymbol) op1).getValue());
        } else {
            outInst.append(loadOp1Symbol(op1));
            outInst.append("move $t0, $t1");
        }
        outInst.append("\n");
        outInst.append(storeOp3Symbol(assignInst.getOp3()));
        return outInst;
    }

    public StringBuilder logicalTranslate(IRElem logicalInst) {
        StringBuilder outInst = new StringBuilder();
        outInst.append(loadOp1Symbol(logicalInst.getOp1()));
        outInst.append(loadOp2Symbol(logicalInst.getOp2()));
        switch (logicalInst.getType()) {
            case IRElem.GRE:
                outInst.append("sgt $t0, $t1, $t2");
                break;
            case IRElem.GEQ:
                outInst.append("sge $t0, $t1, $t2");
                break;
            case IRElem.LSS:
                outInst.append("slt $t0, $t1, $t2");
                break;
            case IRElem.LEQ:
                outInst.append("sle $t0, $t1, $t2");
                break;
            case IRElem.EQL:
                outInst.append("seq $t0, $t1, $t2");
                break;
            case IRElem.NEQ:
                outInst.append("sne $t0, $t1, $t2");
                break;
        }
        outInst.append("\n");
        outInst.append(storeOp3Symbol(logicalInst.getOp3()));
        return outInst;
    }

    public StringBuilder controlTranslate(IRElem controlInst) {
        StringBuilder outInst = new StringBuilder();
        switch (controlInst.getType()) {
            case IRElem.BR:
                IRLabelSymbol label = (IRLabelSymbol) controlInst.getOp3();
                outInst.append("j L").append(label.getId()).append("\n");
                break;
            case IRElem.BZ:
                label = (IRLabelSymbol) controlInst.getOp3();
                outInst.append(loadOp1Symbol(controlInst.getOp1()));
                outInst.append("beqz $t1, L").append(label.getId()).append("\n");
                break;
            case IRElem.BNZ:
                label = (IRLabelSymbol) controlInst.getOp3();
                outInst.append(loadOp1Symbol(controlInst.getOp1()));
                outInst.append("bnez $t1, L").append(label.getId()).append("\n");
                break;
            case IRElem.SETRET:
                outInst.append(loadOp1Symbol(controlInst.getOp3()));
                outInst.append("move $v0, $t1\n");
                break;
            case IRElem.RET:
                // Return恢复现场, $sp和$ra
                outInst.append("lw $ra, 0($sp)\n");
                outInst.append("lw $sp, ").append(-(4 * FunctionTemplate.RET_AREA_NUM)).append("($sp)\n");
                outInst.append("jr $ra\n\n");
                break;
            case IRElem.EXIT:
                outInst.append("li $v0, 10").append("\n");
                outInst.append("syscall\n");
                break;
            case IRElem.CALL:
                IRFuncSymbol funcIR = (IRFuncSymbol) controlInst.getOp1(); // 检索函数
                // 保存现场
                int templateSize = curFunc.getTemplateSize();
                outInst.append("li $t0, ").append(templateSize).append("\n");
                //outInst.append("addu $t4, $t0, $sp\n"); // $t4存储新的$sp
                outInst.append("subu $t4, $sp, $t0\n"); // $t4存储新的$sp，$sp自顶向下增长
                outInst.append("sw $sp, ").append(
                        -4 * (FunctionTemplate.RET_AREA_NUM)).append("($t4)\n");  // $sp填入现场保存区第一个位置

                outInst.append(loadCallParamList(controlInst.getSymbolList(),
                        funcIR.getfParamList(), "t4")); // 装填参数
                outInst.append("move $sp, $t4\n"); // $sp指向新位置
//                outInst.append("jal L").append(
//                        ((IRFuncSymbol) controlInst.getOp1()).getEntry().getId()); // 跳转
                outInst.append("jal Func_").append(
                        ((IRFuncSymbol) controlInst.getOp1()).getFunc()); // 跳转
                outInst.append("\n");
                outInst.append("move $t0, $v0").append("\n");
                outInst.append(storeOp3Symbol(controlInst.getOp3()));
                break;
        }
        return outInst;
    }

    public StringBuilder memInstTranslate(IRElem memInst) {
        StringBuilder outInst = new StringBuilder();
        switch (memInst.getType()) {
            case IRElem.LOAD:
                outInst.append(loadOp1Symbol(memInst.getOp1()));
                IRSymbol offsetSymbol = memInst.getOp2();
                if (offsetSymbol instanceof IRImmSymbol) {
                    int offset = ((IRImmSymbol) offsetSymbol).getValue();
                    outInst.append("lw $t0, ").append(offset).append("($t1)\n");
                } else {
                    outInst.append(loadOp2Symbol(offsetSymbol));
                    outInst.append("addu $t1, $t1, $t2\n");
                    outInst.append("lw $t0, 0($t1)\n");
                }
                outInst.append(storeOp3Symbol(memInst.getOp3()));
                break;
            case IRElem.STORE:
                outInst.append(loadOp1Symbol(memInst.getOp1()));
                outInst.append(loadOp3Symbol(memInst.getOp3()));
                offsetSymbol = memInst.getOp2();
                if (offsetSymbol instanceof IRImmSymbol) {
                    int offset = ((IRImmSymbol) offsetSymbol).getValue();
                    outInst.append("sw $t0, ").append(offset).append("($t1)\n");
                } else {
                    outInst.append(loadOp2Symbol(offsetSymbol));
                    outInst.append("addu $t1, $t1, $t2\n");
                    outInst.append("sw $t0, 0($t1)\n");
                }
                break;
            case IRElem.ALLOCA:
                /*
                IRImmSymbol allocaSize = (IRImmSymbol) memInst.getOp1();
                curPos += allocaSize.getValue();
                outInst.append("li $t0, ").append(curPos).append("\n");*/
                IRSymbol baseAddrIR = memInst.getOp3();
                IRImmSymbol allocaSize = (IRImmSymbol) memInst.getOp1();
                int offset = curFunc.getSymbolOffset(baseAddrIR);
                int size = round(allocaSize.getValue()); // 按4字节取整
                int contextOffset = offset + size;
                outInst.append("li $t0, ").append(contextOffset).append("\n");
                outInst.append("subu $t0, $sp, $t0\n");
                outInst.append(storeOp3Symbol(memInst.getOp3()));
                break;
        }
        return outInst;
    }

    public StringBuilder ioInstTranslate(IRElem ioInst) {
        StringBuilder outInst = new StringBuilder();
        switch (ioInst.getType()) {
            case IRElem.GETINT:
                outInst.append("li $v0, 5\n");
                outInst.append("syscall\n");
                outInst.append("move $t0, $v0\n");
                outInst.append(storeOp3Symbol(ioInst.getOp3()));
                break;
            case IRElem.PRINTS:
                outInst.append(loadOp1Symbol(ioInst.getOp3()));
                outInst.append("li $v0, 4\n");
                outInst.append("move $a0, $t1\n");
                outInst.append("syscall\n");
                break;
            case IRElem.PRINTI:
                outInst.append(loadOp1Symbol(ioInst.getOp3()));
                outInst.append("li $v0, 1\n");
                outInst.append("move $a0, $t1\n");
                outInst.append("syscall\n");
                break;
        }
        return outInst;
    }

    public StringBuilder loadCallParamList(ArrayList<IRSymbol> rParamList,
                                           ArrayList<IRSymbol> fParamList, String newSPVal) {
        StringBuilder outStr = new StringBuilder();
        int localVarOffset = curFunc.getLocalVarOffsetBytes();
        /*for (int i = 0; i < rParamList.size(); ++i) {
            outStr.append(loadOp3Symbol(rParamList.get(i)));
            outStr.append(storeOp3Symbol(fParamList.get(i)));
        }*/
        for (int i = 0; i < rParamList.size(); ++i) {
            outStr.append(loadOp1Symbol(rParamList.get(i)));
            // curPos += 4;
            if (i <= 3) {
                outStr.append("move $a").append(i).append(", $t1\n");
            }
            outStr.append("sw $t1, ").append(-(localVarOffset + i * 4)).
                    append("($").append(newSPVal).append(")\n"); // 这里往新$sp位置填参数

        }
        return outStr;
    }

    public StringBuilder outputDataSegment() {
        StringBuilder outStr = new StringBuilder(".data\n");
        for (VarSymbol constVarSymbol : constantArrMap.keySet()) {
            IRSymbol constSymbol = constantArrMap.get(constVarSymbol);
            outStr.append(".align 2\n");
            outStr.append("L").append(constSymbol.getId()).append(":\n.word ");
            ArrayList<Integer> constArr = constVarSymbol.constGetAllValue();
            int i;
            for (i = 0; i < constArr.size() - 1; ++i) {
                outStr.append(constArr.get(i)).append(", ");
            }
            outStr.append(constArr.get(i)).append("\n");
        }
        for (VarSymbol globalVarSymbol : globalArrMap.keySet()) {
            IRSymbol varLabelSymbol = globalArrMap.get(globalVarSymbol);
            outStr.append(".align 2\n");
            outStr.append("L").append(varLabelSymbol.getId()).append(":\n.word ");
            ArrayList<Integer> constArr = globalVarSymbol.constGetAllValue();
            int i;
            for (i = 0; i < constArr.size() - 1; ++i) {
                outStr.append(constArr.get(i)).append(", ");
            }
            outStr.append(constArr.get(i)).append("\n");
        }
        for (IRSymbol strSymbol : formatStrMap.keySet()) {
            outStr.append(".align 2\n");
            outStr.append("L").append(strSymbol.getId()).append(":\n.asciiz ");
            String rawStr = formatStrMap.get(strSymbol);
            outStr.append("\"").append(rawStr).append("\"\n");
        }
        return outStr;
    }

    public int round(int num) {
        int k = num % 4;
        if (k == 0) {
            return num;
        } else {
            return num + 4 - k;
        }
    }
}
