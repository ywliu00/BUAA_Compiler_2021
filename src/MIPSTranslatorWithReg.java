import Exceptions.NoRegToUseException;
import IR.IRElem;
import IR.IRFuncSymbol;
import IR.IRImmSymbol;
import IR.IRLabelManager;
import IR.IRLabelSymbol;
import IR.IRSymbol;
import IR.IRTranslater;
import Optimizer.IROptimizer;
import Symbols.VarSymbol;
import SyntaxClasses.SyntaxClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

public class MIPSTranslatorWithReg {
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
    private ArrayList<Integer> breakpointList;
    private HashSet<String> freeRegPool;
    private HashSet<String> usedRegPool;
    private String reservedReg;
    private String immReg;
    private HashMap<IRSymbol, String> regUseMap;

    private boolean testVar = false;


    public MIPSTranslatorWithReg(IRTranslater irTranslater) {
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

        this.breakpointList = IROptimizer.buildBasicBlockBreakpoint(this.iRList);
        regPoolInit();
    }

    public void regPoolInit() {
        this.freeRegPool = new HashSet<>();
        this.usedRegPool = new HashSet<>();
        this.regUseMap = new HashMap<>();
        for (int i = 0; i < 8; i++) {
            freeRegPool.add("s" + i);
            freeRegPool.add("t" + i);
        }
        this.reservedReg = "t9";
        this.immReg = "t8";
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
        int blockID = 1;
        int nextEnd = breakpointList.get(blockID);
        for (int i = 0; i < iRList.size(); i++) {
            IRElem inst = iRList.get(i);
            if (inst.getType() == IRElem.FUNC) {
                curFunc = functionTemplateTable.get(((IRFuncSymbol) inst.getOp3()).getFunc());
                outStr.append("Func_").append(((IRFuncSymbol) inst.getOp3()).getFunc()).append(":\n");
                outStr.append("sw $ra, 0($sp)\n"); // 保存返回位置
            } else if (inst.getType() == IRElem.ADD || inst.getType() == IRElem.MINU ||
                    inst.getType() == IRElem.MULT || inst.getType() == IRElem.DIV ||
                    inst.getType() == IRElem.MOD || inst.getType() == IRElem.LSHIFT ||
                    inst.getType() == IRElem.RSHIFT || inst.getType() == IRElem.RASHIFT ||
                    inst.getType() == IRElem.AND) {
                outStr.append(arithmeticTranslate(inst, i, nextEnd));
                if (i == nextEnd - 1) {
                    outStr.append(flushRegPool());
                    nextEnd = breakpointList.get(++blockID);
                }
            } else if (inst.getType() == IRElem.GRE || inst.getType() == IRElem.GEQ ||
                    inst.getType() == IRElem.LSS || inst.getType() == IRElem.LEQ ||
                    inst.getType() == IRElem.EQL || inst.getType() == IRElem.NEQ) {
                outStr.append(logicalTranslate(inst, i, nextEnd));
                if (i == nextEnd - 1) {
                    outStr.append(flushRegPool());
                    nextEnd = breakpointList.get(++blockID);
                }
            } else if (inst.getType() == IRElem.ASSIGN) {
                outStr.append(assignTranslate(inst, i, nextEnd));
                if (i == nextEnd - 1) {
                    outStr.append(flushRegPool());
                    nextEnd = breakpointList.get(++blockID);
                }
            } else if (inst.getType() == IRElem.BR || inst.getType() == IRElem.BZ ||
                    inst.getType() == IRElem.BNZ || inst.getType() == IRElem.SETRET ||
                    inst.getType() == IRElem.RET || inst.getType() == IRElem.CALL ||
                    inst.getType() == IRElem.EXIT) {
                if (i == nextEnd - 1 && inst.getType() != IRElem.EXIT) {
                    outStr.append(flushRegPool());
                    nextEnd = breakpointList.get(++blockID);
                }
                outStr.append(controlTranslate(inst, i, nextEnd));
                if (inst.getType() == IRElem.RET || inst.getType() == IRElem.EXIT) {
                    curFunc = FunctionTemplate.GLOBAL;
                }
            } else if (inst.getType() == IRElem.LOAD || inst.getType() == IRElem.STORE ||
                    inst.getType() == IRElem.ALLOCA) {
                outStr.append(memInstTranslate(inst, i, nextEnd));
                if (i == nextEnd - 1) {
                    outStr.append(flushRegPool());
                    nextEnd = breakpointList.get(++blockID);
                }
            } else if (inst.getType() == IRElem.GETINT || inst.getType() == IRElem.PRINTI ||
                    inst.getType() == IRElem.PRINTS) {
                outStr.append(ioInstTranslate(inst, i, nextEnd));
                if (i == nextEnd - 1) {
                    outStr.append(flushRegPool());
                    nextEnd = breakpointList.get(++blockID);
                }
            } else if (inst.getType() == IRElem.LABEL) {
                outStr.append("L").append(inst.getOp3().getId()).append(":\n");
                if (inst.getOp3().getId() == 52) {
                    System.out.println("你在演我");
                }
            }
        }
        return outStr;
    }

    public StringBuilder regAllocForVar(IRSymbol irSymbol, int instPos, int blockEnd) throws NoRegToUseException {
        if (!freeRegPool.isEmpty()) {
            String regName = null;
            for (String str : freeRegPool) {
                regName = str;
                break;
            }
            freeRegPool.remove(regName);
            usedRegPool.add(regName);
            regUseMap.put(irSymbol, regName);
            return loadSymbolToRegister(irSymbol, regName);
        } else {
            HashSet<IRSymbol> usedSymbol = new HashSet<>();
            IRElem currentInst = iRList.get(instPos);
            for (int i = instPos; i < blockEnd; i++) {
                IRElem inst = iRList.get(i);
                if (inst.getType() != IRElem.CALL) {
                    IRSymbol op3 = inst.getOp3();
                    IRSymbol op1 = inst.getOp1();
                    IRSymbol op2 = inst.getOp2();
                    if (op3 != null && regUseMap.getOrDefault(op3, null) != null) {
                        usedSymbol.add(op3);
                    }
                    if (op1 != null && regUseMap.getOrDefault(op1, null) != null) {
                        usedSymbol.add(op1);
                    }
                    if (op2 != null && regUseMap.getOrDefault(op2, null) != null) {
                        usedSymbol.add(op2);
                    }
                } else {
                    ArrayList<IRSymbol> symbolList = inst.getSymbolList();
                    for (IRSymbol symbol : symbolList) {
                        if (symbol != null && regUseMap.getOrDefault(symbol, null) != null) {
                            usedSymbol.add(symbol);
                        }
                    }
                }
            }
            IRSymbol used = null, free = null;
            HashSet<IRSymbol> onUseSet = new HashSet<>(regUseMap.keySet());
            onUseSet.remove(currentInst.getOp3());
            onUseSet.remove(currentInst.getOp1());
            onUseSet.remove(currentInst.getOp2());
            if (currentInst.getSymbolList() != null) {
                onUseSet.removeAll(currentInst.getSymbolList());
            }
            for (IRSymbol symbol : onUseSet) {
                if (usedSymbol.contains(symbol)) {
                    used = symbol;
                } else {
                    free = symbol;
                    break;
                }
            }
            StringBuilder outStr = new StringBuilder();
            if (free != null) { // 有后面没用到的变量
                String regName = regUseMap.get(free);
                if (!globalLabelSet.contains(free)) {
                    outStr.append(storeRegisterToSymbol(free, regName));
                }
                regUseMap.remove(free);
                regUseMap.put(irSymbol, regName);
                outStr.append(loadSymbolToRegister(irSymbol, regName));
            } else if (used != null) { // 没有后面没用到的变量，但有可以换出的变量
                String regName = regUseMap.get(used);
                if (!globalLabelSet.contains(used)) {
                    outStr.append(storeRegisterToSymbol(used, regName));
                }
                regUseMap.remove(used);
                regUseMap.put(irSymbol, regName);
                outStr.append(loadSymbolToRegister(irSymbol, regName));
            } else { // 所有变量当前都要使用
                throw new NoRegToUseException();
            }
            return outStr;
        }
    }

    public StringBuilder regAllocForVar(IRSymbol irSymbol, int instPos, int blockEnd, boolean withLoad) throws NoRegToUseException {
        if (!freeRegPool.isEmpty()) {
            String regName = null;
            for (String str : freeRegPool) {
                regName = str;
                break;
            }
            freeRegPool.remove(regName);
            usedRegPool.add(regName);
            regUseMap.put(irSymbol, regName);
            if (withLoad) {
                return loadSymbolToRegister(irSymbol, regName);
            } else {
                return new StringBuilder();
            }
        } else {
            HashSet<IRSymbol> usedSymbol = new HashSet<>();
            IRElem currentInst = iRList.get(instPos);
            for (int i = instPos; i < blockEnd; i++) {
                IRElem inst = iRList.get(i);
                if (inst.getType() != IRElem.CALL) {
                    IRSymbol op3 = inst.getOp3();
                    IRSymbol op1 = inst.getOp1();
                    IRSymbol op2 = inst.getOp2();
                    if (op3 != null && regUseMap.getOrDefault(op3, null) != null) {
                        usedSymbol.add(op3);
                    }
                    if (op1 != null && regUseMap.getOrDefault(op1, null) != null) {
                        usedSymbol.add(op1);
                    }
                    if (op2 != null && regUseMap.getOrDefault(op2, null) != null) {
                        usedSymbol.add(op2);
                    }
                } else {
                    ArrayList<IRSymbol> symbolList = inst.getSymbolList();
                    for (IRSymbol symbol : symbolList) {
                        if (symbol != null && regUseMap.getOrDefault(symbol, null) != null) {
                            usedSymbol.add(symbol);
                        }
                    }
                }
            }
            IRSymbol used = null, free = null;
            HashSet<IRSymbol> onUseSet = new HashSet<>(regUseMap.keySet());
            onUseSet.remove(currentInst.getOp3());
            onUseSet.remove(currentInst.getOp1());
            onUseSet.remove(currentInst.getOp2());
            if (currentInst.getSymbolList() != null) {
                onUseSet.removeAll(currentInst.getSymbolList());
            }
            for (IRSymbol symbol : onUseSet) {
                if (usedSymbol.contains(symbol)) {
                    used = symbol;
                } else {
                    free = symbol;
                    break;
                }
            }
            StringBuilder outStr = new StringBuilder();
            if (free != null) { // 有后面没用到的变量
                String regName = regUseMap.get(free);
                if (!globalLabelSet.contains(free)) {
                    outStr.append(storeRegisterToSymbol(free, regName));
                }
                regUseMap.remove(free);
                regUseMap.put(irSymbol, regName);
                if (withLoad) {
                    outStr.append(loadSymbolToRegister(irSymbol, regName));
                }
            } else if (used != null) { // 没有后面没用到的变量，但有可以换出的变量
                String regName = regUseMap.get(used);
                if (!globalLabelSet.contains(used)) {
                    outStr.append(storeRegisterToSymbol(used, regName));
                }
                regUseMap.remove(used);
                regUseMap.put(irSymbol, regName);
                if (withLoad) {
                    outStr.append(loadSymbolToRegister(irSymbol, regName));
                }
            } else { // 所有变量当前都要使用
                throw new NoRegToUseException();
            }
            return outStr;
        }
    }

    public StringBuilder regAllocForVarNew(IRSymbol irSymbol, int instPos, int blockEnd) throws NoRegToUseException {
        if (!freeRegPool.isEmpty()) {
            String regName = null;
            for (String str : freeRegPool) {
                regName = str;
                break;
            }
            freeRegPool.remove(regName);
            usedRegPool.add(regName);
            regUseMap.put(irSymbol, regName);
            return loadSymbolToRegister(irSymbol, regName);
        } else {
            HashSet<IRSymbol> usedSymbol = new HashSet<>();
            IRElem currentInst = iRList.get(instPos);
            for (int i = instPos; i < blockEnd; i++) {
                IRElem inst = iRList.get(i);
                if (inst.getType() != IRElem.CALL) {
                    IRSymbol op3 = inst.getOp3();
                    IRSymbol op1 = inst.getOp1();
                    IRSymbol op2 = inst.getOp2();
                    if (op3 != null && regUseMap.getOrDefault(op3, null) != null) {
                        usedSymbol.add(op3);
                    }
                    if (op1 != null && regUseMap.getOrDefault(op1, null) != null) {
                        usedSymbol.add(op1);
                    }
                    if (op2 != null && regUseMap.getOrDefault(op2, null) != null) {
                        usedSymbol.add(op2);
                    }
                } else {
                    ArrayList<IRSymbol> symbolList = inst.getSymbolList();
                    for (IRSymbol symbol : symbolList) {
                        if (symbol != null && regUseMap.getOrDefault(symbol, null) != null) {
                            usedSymbol.add(symbol);
                        }
                    }
                }
            }
            IRSymbol used = null, free = null;
            HashSet<IRSymbol> onUseSet = new HashSet<>(regUseMap.keySet());
            onUseSet.remove(currentInst.getOp3());
            onUseSet.remove(currentInst.getOp1());
            onUseSet.remove(currentInst.getOp2());
            if (currentInst.getSymbolList() != null) {
                onUseSet.removeAll(currentInst.getSymbolList());
            }
            for (IRSymbol symbol : onUseSet) {
                if (usedSymbol.contains(symbol)) {
                    used = symbol;
                } else {
                    free = symbol;
                    break;
                }
            }
            StringBuilder outStr = new StringBuilder();
            if (free != null) { // 有后面没用到的变量
                String regName = regUseMap.get(free);
                if (!globalLabelSet.contains(free)) {
                    outStr.append(storeRegisterToSymbol(free, regName));
                }
                regUseMap.remove(free);
                regUseMap.put(irSymbol, regName);
                //outStr.append(loadSymbolToRegister(irSymbol, regName));
            } else if (used != null) { // 没有后面没用到的变量，但有可以换出的变量
                String regName = regUseMap.get(used);
                if (!globalLabelSet.contains(used)) {
                    outStr.append(storeRegisterToSymbol(used, regName));
                }
                regUseMap.remove(used);
                regUseMap.put(irSymbol, regName);
                //outStr.append(loadSymbolToRegister(irSymbol, regName));
            } else { // 所有变量当前都要使用
                throw new NoRegToUseException();
            }
            return outStr;
        }
    }

    public StringBuilder flushRegPool() {
        StringBuilder outStr = new StringBuilder();
        HashSet<IRSymbol> onUseSet = new HashSet<>(regUseMap.keySet());
        for (IRSymbol symbol : onUseSet) {
            if (!globalLabelSet.contains(symbol)) {
                outStr.append(storeRegisterToSymbol(symbol, regUseMap.get(symbol)));
            }
            regUseMap.remove(symbol);
        }
        freeRegPool.addAll(usedRegPool);
        usedRegPool.clear();
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

    public StringBuilder arithmeticTranslate(IRElem arthInst, int instPos, int nextEnd) {
        IRSymbol op3 = arthInst.getOp3();
        IRSymbol op1 = arthInst.getOp1();
        IRSymbol op2 = arthInst.getOp2();
        if (op1 instanceof IRImmSymbol && op2 instanceof IRImmSymbol) {
            return new StringBuilder("你在演我\n");
        } else if (op1 instanceof IRImmSymbol) {
            if (arthInst.getType() == IRElem.ADD || arthInst.getType() == IRElem.AND ||
                    arthInst.getType() == IRElem.MULT) { // 可交换
                return arithmeticTranslateImmVar(arthInst, instPos, nextEnd, op3, op2, ((IRImmSymbol) op1).getValue());
            } else {
                if (((IRImmSymbol) op1).getValue() == 0) {
                    return arithmeticTranslateOneVar(arthInst, instPos, nextEnd, op3, "0", op2);
                } else {
                    StringBuilder outStr = new StringBuilder();
                    outStr.append("li $" + immReg + ", " + ((IRImmSymbol) op1).getValue() + "\n");
                    outStr.append(arithmeticTranslateOneVar(arthInst, instPos, nextEnd, op3, immReg, op2));
                    return outStr;
                }
            }
        } else if (op2 instanceof IRImmSymbol) {
            return arithmeticTranslateImmVar(arthInst, instPos, nextEnd, op3, op1, ((IRImmSymbol) op2).getValue());
        } else {
            return arithmeticTranslateTwoVar(arthInst, instPos, nextEnd, op3, op1, op2);
        }
    }

    public StringBuilder arithmeticTranslateImmVar(IRElem arthInst, int instPos, int nextEnd,
                                                   IRSymbol op3, IRSymbol op1, int op2Value) {
        StringBuilder outInst = new StringBuilder();
        boolean isResultReserved = false;
        String op1Reg = regUseMap.getOrDefault(op1, null);
        if (op1Reg == null) {
            try {
                if (testVar) {
                    //regAllocForVar(op1, instPos, nextEnd);
                    outInst.append(regAllocForVarNew(op1, instPos, nextEnd));
                    op1Reg = regUseMap.get(op1);
                    outInst.append(loadSymbolToRegister(op1, op1Reg));
                } else {
                    outInst.append(regAllocForVar(op1, instPos, nextEnd));
                }
                op1Reg = regUseMap.get(op1);
                //outInst.append(loadSymbolToRegister(op1, op1Reg));
            } catch (NoRegToUseException e) {
                op1Reg = reservedReg;
                outInst.append(loadSymbolToRegister(op1, op1Reg));
            }
        }
        String op3Reg = regUseMap.getOrDefault(op3, null);
        if (op3Reg == null) {
            try {
                if (testVar) {
                    //regAllocForVar(op3, instPos, nextEnd);
                    outInst.append(regAllocForVarNew(op3, instPos, nextEnd));
                    //outInst.append(loadSymbolToRegister(op3, op3Reg));
                } else {
                    outInst.append(regAllocForVar(op3, instPos, nextEnd, false));
                }
                op3Reg = regUseMap.get(op3);
            } catch (NoRegToUseException e) {
                op3Reg = reservedReg;
                isResultReserved = true;
                outInst.append(loadSymbolToRegister(op3, op3Reg));
            }
        }
        switch (arthInst.getType()) {
            case IRElem.ADD:
                if (!op3Reg.equals(op1Reg) || op2Value != 0) {
                    outInst.append("addiu $" + op3Reg + ", $" + op1Reg + ", " + op2Value);
                }
                break;
            case IRElem.MINU:
                if (!op3Reg.equals(op1Reg) || op2Value != 0) {
                    outInst.append("subiu $" + op3Reg + ", $" + op1Reg + ", " + op2Value);
                }
                break;
            case IRElem.MULT:
                if (op2Value == 0) {
                    outInst.append("li $" + op3Reg + ", 0\n");
                } else if (op2Value == 1) {
                    outInst.append("move $" + op3Reg + ", " + op1Reg + "\n");
                } else {
                    outInst.append("li $" + immReg + ", " + op2Value + "\n");
                    outInst.append("mult $" + op1Reg + ", $" + immReg + "\n");
                    outInst.append("mflo $" + op3Reg);
                }
                break;
            case IRElem.DIV:
                if (op2Value == 1) {
                    outInst.append("move $" + op3Reg + ", " + op1Reg + "\n");
                } else {
                    outInst.append("li $" + immReg + ", " + op2Value + "\n");
                    outInst.append("div $" + op1Reg + ", $" + immReg + "\n");
                    outInst.append("mflo $" + op3Reg);
                }
                break;
            case IRElem.MOD:
                outInst.append("li $" + immReg + ", " + op2Value + "\n");
                outInst.append("div $" + op1Reg + ", $" + immReg + "\n");
                outInst.append("mfhi $" + op3Reg);
                break;
            case IRElem.LSHIFT:
                outInst.append("sll $" + op3Reg + ", $" + op1Reg + ", " + op2Value);
                break;
            case IRElem.RSHIFT:
                outInst.append("srl $" + op3Reg + ", $" + op1Reg + ", " + op2Value);
                break;
            case IRElem.RASHIFT:
                outInst.append("sra $" + op3Reg + ", $" + op1Reg + ", " + op2Value);
                break;
            case IRElem.AND:
                outInst.append("andi $" + op3Reg + ", $" + op1Reg + ", " + op2Value);
                break;
        }
        outInst.append("\n");
        if (isResultReserved) {
            outInst.append(storeRegisterToSymbol(op3, reservedReg));
        }
        return outInst;
    }

    public StringBuilder arithmeticTranslateOneVar(IRElem arthInst, int instPos, int nextEnd,
                                                   IRSymbol op3, String op1Reg, IRSymbol op2) {
        StringBuilder outInst = new StringBuilder();
        boolean isResultReserved = false;
        String op2Reg = regUseMap.getOrDefault(op2, null);
        if (op2Reg == null) {
            try {
                if (testVar) {
                    //regAllocForVar(op2, instPos, nextEnd);
                    outInst.append(regAllocForVarNew(op2, instPos, nextEnd));
                    op2Reg = regUseMap.get(op2);
                    outInst.append(loadSymbolToRegister(op2, op2Reg));
                } else {
                    outInst.append(regAllocForVar(op2, instPos, nextEnd));
                }
                //outInst.append(regAllocForVar(op2, instPos, nextEnd));
                op2Reg = regUseMap.get(op2);
                //outInst.append(loadSymbolToRegister(op2, op2Reg));
            } catch (NoRegToUseException e) {
                op2Reg = reservedReg;
                outInst.append(loadSymbolToRegister(op2, op2Reg));
            }
        }
        String op3Reg = regUseMap.getOrDefault(op3, null);
        if (op3Reg == null) {
            try {
                if (testVar) {
                    //regAllocForVar(op3, instPos, nextEnd);
                    outInst.append(regAllocForVarNew(op3, instPos, nextEnd));
                    //outInst.append(loadSymbolToRegister(op3, op3Reg));
                } else {
                    outInst.append(regAllocForVar(op3, instPos, nextEnd, false));
                }
                //outInst.append(regAllocForVar(op3, instPos, nextEnd));
                op3Reg = regUseMap.get(op3);
            } catch (NoRegToUseException e) {
                op3Reg = reservedReg;
                isResultReserved = true;
            }
        }
        switch (arthInst.getType()) {
            case IRElem.ADD:
                outInst.append("addu $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
            case IRElem.MINU:
                outInst.append("subu $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
            case IRElem.MULT:
                outInst.append("mult $" + op1Reg + ", $" + op2Reg).append("\n");
                outInst.append("mflo $" + op3Reg);
                break;
            case IRElem.DIV:
                outInst.append("div $" + op1Reg + ", $" + op2Reg).append("\n");
                outInst.append("mflo $" + op3Reg);
                break;
            case IRElem.MOD:
                outInst.append("div $" + op1Reg + ", $" + op2Reg).append("\n");
                outInst.append("mfhi $" + op3Reg);
                break;
            case IRElem.LSHIFT:
                outInst.append("sllv $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
            case IRElem.RSHIFT:
                outInst.append("srlv $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
            case IRElem.RASHIFT:
                outInst.append("srav $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
            case IRElem.AND:
                outInst.append("and $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
        }
        outInst.append("\n");
        if (isResultReserved) {
            outInst.append(storeRegisterToSymbol(op3, reservedReg));
        }
        return outInst;
    }

    public StringBuilder arithmeticTranslateTwoVar(IRElem arthInst, int instPos, int nextEnd,
                                                   IRSymbol op3, IRSymbol op1, IRSymbol op2) {
        StringBuilder outInst = new StringBuilder();
        //outInst.append(loadOp1Symbol(arthInst.getOp1()));
        //outInst.append(loadOp2Symbol(arthInst.getOp2()));
        boolean isResultReserved = false;
        String op1Reg = regUseMap.getOrDefault(op1, null);
        if (op1Reg == null) {
            try {
                if (testVar) {
                    //regAllocForVar(op1, instPos, nextEnd);
                    outInst.append(regAllocForVarNew(op1, instPos, nextEnd));
                    op1Reg = regUseMap.get(op1);
                    outInst.append(loadSymbolToRegister(op1, op1Reg));
                } else {
                    outInst.append(regAllocForVar(op1, instPos, nextEnd));
                }
                //outInst.append(regAllocForVar(op1, instPos, nextEnd));
                op1Reg = regUseMap.get(op1);
                //outInst.append(loadSymbolToRegister(op1, op1Reg));
            } catch (NoRegToUseException e) {
                op1Reg = reservedReg;
                outInst.append(loadSymbolToRegister(op1, op1Reg));
            }
        }
        String op2Reg = regUseMap.getOrDefault(op2, null);
        if (op2Reg == null) {
            try {
                if (testVar) {
                    //regAllocForVar(op2, instPos, nextEnd);
                    outInst.append(regAllocForVarNew(op2, instPos, nextEnd));
                    op2Reg = regUseMap.get(op2);
                    outInst.append(loadSymbolToRegister(op2, op2Reg));
                } else {
                    outInst.append(regAllocForVar(op2, instPos, nextEnd));
                }
                //outInst.append(regAllocForVar(op2, instPos, nextEnd));
                op2Reg = regUseMap.get(op2);
                //outInst.append(loadSymbolToRegister(op2, op2Reg));
            } catch (NoRegToUseException e) {
                op2Reg = reservedReg;
                outInst.append(loadSymbolToRegister(op2, op2Reg));
            }
        }
        String op3Reg = regUseMap.getOrDefault(op3, null);
        if (op3Reg == null) {
            try {
                if (testVar) {
                    //regAllocForVar(op3, instPos, nextEnd);
                    outInst.append(regAllocForVarNew(op3, instPos, nextEnd));
                    op3Reg = regUseMap.get(op3);
                    //outInst.append(loadSymbolToRegister(op3, op3Reg));
                } else {
                    outInst.append(regAllocForVar(op3, instPos, nextEnd, false));
                }
                //outInst.append(regAllocForVar(op3, instPos, nextEnd));
                op3Reg = regUseMap.get(op3);
            } catch (NoRegToUseException e) {
                op3Reg = reservedReg;
                isResultReserved = true;
            }
        }
        switch (arthInst.getType()) {
            case IRElem.ADD:
                outInst.append("addu $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
            case IRElem.MINU:
                outInst.append("subu $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
            case IRElem.MULT:
                outInst.append("mult $" + op1Reg + ", $" + op2Reg).append("\n");
                outInst.append("mflo $" + op3Reg);
                break;
            case IRElem.DIV:
                outInst.append("div $" + op1Reg + ", $" + op2Reg).append("\n");
                outInst.append("mflo $" + op3Reg);
                break;
            case IRElem.MOD:
                outInst.append("div $" + op1Reg + ", $" + op2Reg).append("\n");
                outInst.append("mfhi $" + op3Reg);
                break;
            case IRElem.LSHIFT:
                outInst.append("sllv $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
            case IRElem.RSHIFT:
                outInst.append("srlv $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
            case IRElem.RASHIFT:
                outInst.append("srav $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
            case IRElem.AND:
                outInst.append("and $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
        }
        outInst.append("\n");
        if (isResultReserved) {
            outInst.append(storeRegisterToSymbol(op3, reservedReg));
        }
        //outInst.append(storeOp3Symbol(arthInst.getOp3()));
        return outInst;
    }

    public StringBuilder assignTranslate(IRElem assignInst, int instPos, int nextEnd) {
        StringBuilder outInst = new StringBuilder();
        IRSymbol op3 = assignInst.getOp3();
        IRSymbol op1 = assignInst.getOp1();
        boolean isResultReserved = false;
        String op3Reg = regUseMap.getOrDefault(op3, null);
        if (op3Reg == null) {
            try {
                if (testVar) {
                    //regAllocForVar(op3, instPos, nextEnd);
                    outInst.append(regAllocForVarNew(op3, instPos, nextEnd));
                    op3Reg = regUseMap.get(op3);
                    outInst.append(loadSymbolToRegister(op3, op3Reg));
                } else {
                    outInst.append(regAllocForVar(op3, instPos, nextEnd, false));
                }
                //outInst.append(regAllocForVar(op3, instPos, nextEnd));
                op3Reg = regUseMap.get(op3);
            } catch (NoRegToUseException e) {
                op3Reg = reservedReg;
                isResultReserved = true;
                outInst.append(loadSymbolToRegister(op3, op3Reg));
            }
        }
        if (op1 instanceof IRImmSymbol) {
            outInst.append("li $" + op3Reg + ", ").append(((IRImmSymbol) op1).getValue());
        } else {
            //outInst.append(loadOp1Symbol(op1));
            String op1Reg = regUseMap.getOrDefault(op1, null);
            if (op1Reg == null) {
                try {
                    if (testVar) {
                        //regAllocForVar(op1, instPos, nextEnd);
                        outInst.append(regAllocForVarNew(op1, instPos, nextEnd));
                        op1Reg = regUseMap.get(op1);
                        outInst.append(loadSymbolToRegister(op1, op1Reg));
                    } else {
                        outInst.append(regAllocForVar(op1, instPos, nextEnd));
                    }
                    //outInst.append(regAllocForVar(op1, instPos, nextEnd));
                    op1Reg = regUseMap.get(op1);
                    //outInst.append(loadSymbolToRegister(op1, op1Reg));
                } catch (NoRegToUseException e) {
                    op1Reg = reservedReg;
                    outInst.append(loadSymbolToRegister(op1, op1Reg));
                }
            }
            outInst.append("move $" + op3Reg + ", $" + op1Reg);
        }
        outInst.append("\n");
        if (isResultReserved) {
            outInst.append(storeRegisterToSymbol(op3, reservedReg));
        }
        //outInst.append(storeOp3Symbol(assignInst.getOp3()));
        return outInst;
    }

    public StringBuilder logicalTranslate(IRElem logicalInst, int instPos, int nextEnd) {
        StringBuilder outInst = new StringBuilder();
        //outInst.append(loadOp1Symbol(logicalInst.getOp1()));
        //outInst.append(loadOp2Symbol(logicalInst.getOp2()));
        IRSymbol op3 = logicalInst.getOp3();
        IRSymbol op1 = logicalInst.getOp1();
        IRSymbol op2 = logicalInst.getOp2();
        boolean isResultReserved = false;
        if (op1 instanceof IRImmSymbol && op2 instanceof IRImmSymbol) {
            return new StringBuilder("你在演我\n");
        }
        String op1Reg;
        if (op1 instanceof IRImmSymbol) {
            op1Reg = immReg;
            outInst.append("li $" + immReg + ", " + ((IRImmSymbol) op1).getValue() + "\n");
        } else {
            op1Reg = regUseMap.getOrDefault(op1, null);
            if (op1Reg == null) {
                try {
                    if (testVar) {
                        //regAllocForVar(op1, instPos, nextEnd);
                        outInst.append(regAllocForVarNew(op1, instPos, nextEnd));
                        op1Reg = regUseMap.get(op1);
                        outInst.append(loadSymbolToRegister(op1, op1Reg));
                    } else {
                        outInst.append(regAllocForVar(op1, instPos, nextEnd));
                    }
                    //outInst.append(regAllocForVar(op1, instPos, nextEnd));
                    op1Reg = regUseMap.get(op1);
                    //outInst.append(loadSymbolToRegister(op1, op1Reg));
                } catch (NoRegToUseException e) {
                    op1Reg = reservedReg;
                    outInst.append(loadSymbolToRegister(op1, op1Reg));
                }
            }
        }
        String op2Reg;
        if (op2 instanceof IRImmSymbol) {
            op2Reg = immReg;
            outInst.append("li $" + immReg + ", " + ((IRImmSymbol) op2).getValue() + "\n");
        } else {
            op2Reg = regUseMap.getOrDefault(op2, null);
            if (op2Reg == null) {
                try {
                    if (testVar) {
                        //regAllocForVar(op2, instPos, nextEnd);
                        outInst.append(regAllocForVarNew(op2, instPos, nextEnd));
                        op2Reg = regUseMap.get(op2);
                        outInst.append(loadSymbolToRegister(op2, op2Reg));
                    } else {
                        outInst.append(regAllocForVar(op2, instPos, nextEnd));
                    }
                    //outInst.append(regAllocForVar(op2, instPos, nextEnd));
                    op2Reg = regUseMap.get(op2);
                    //outInst.append(loadSymbolToRegister(op2, op2Reg));
                } catch (NoRegToUseException e) {
                    op2Reg = reservedReg;
                    outInst.append(loadSymbolToRegister(op2, op2Reg));
                }
            }
        }
        String op3Reg = regUseMap.getOrDefault(op3, null);
        if (op3Reg == null) {
            try {
                if (testVar) {
                    //regAllocForVar(op3, instPos, nextEnd);
                    outInst.append(regAllocForVarNew(op3, instPos, nextEnd));
                    op3Reg = regUseMap.get(op3);
                    outInst.append(loadSymbolToRegister(op3, op3Reg));
                } else {
                    outInst.append(regAllocForVar(op3, instPos, nextEnd, false));
                }
                //outInst.append(regAllocForVar(op3, instPos, nextEnd));
                op3Reg = regUseMap.get(op3);
            } catch (NoRegToUseException e) {
                op3Reg = reservedReg;
                isResultReserved = true;
            }
        }
        switch (logicalInst.getType()) {
            case IRElem.GRE:
                outInst.append("sgt $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
            case IRElem.GEQ:
                outInst.append("sge $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
            case IRElem.LSS:
                outInst.append("slt $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
            case IRElem.LEQ:
                outInst.append("sle $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
            case IRElem.EQL:
                outInst.append("seq $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
            case IRElem.NEQ:
                outInst.append("sne $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg);
                break;
        }
        outInst.append("\n");
        if (isResultReserved) {
            outInst.append(storeRegisterToSymbol(op3, reservedReg));
        }
        //outInst.append(storeOp3Symbol(logicalInst.getOp3()));
        return outInst;
    }

    public StringBuilder controlTranslate(IRElem controlInst, int instPos, int nextEnd) {
        StringBuilder outInst = new StringBuilder();
        IRSymbol op1 = controlInst.getOp1();
        switch (controlInst.getType()) {
            case IRElem.BR:
                IRLabelSymbol label = (IRLabelSymbol) controlInst.getOp3();
                outInst.append("j L").append(label.getId()).append("\n");
                break;
            case IRElem.BZ:
                label = (IRLabelSymbol) controlInst.getOp3();
                //outInst.append(loadOp1Symbol(controlInst.getOp1()));
                String op1Reg;
                if (op1 instanceof IRImmSymbol) {
                    op1Reg = immReg;
                    outInst.append("li $" + immReg + ", " + ((IRImmSymbol) op1).getValue() + "\n");
                } else {
                    op1Reg = regUseMap.getOrDefault(op1, null);
                    if (op1Reg == null) {
                        try {
                            if (testVar) {
                                //regAllocForVar(op1, instPos, nextEnd);
                                outInst.append(regAllocForVarNew(op1, instPos, nextEnd));
                                op1Reg = regUseMap.get(op1);
                                outInst.append(loadSymbolToRegister(op1, op1Reg));
                            } else {
                                outInst.append(regAllocForVar(op1, instPos, nextEnd));
                            }
                            //outInst.append(regAllocForVar(op1, instPos, nextEnd));
                            op1Reg = regUseMap.get(op1);
                            //outInst.append(loadSymbolToRegister(op1, op1Reg));
                        } catch (NoRegToUseException e) {
                            op1Reg = reservedReg;
                            outInst.append(loadSymbolToRegister(op1, op1Reg));
                        }
                    }
                }
                outInst.append("beqz $" + op1Reg + ", L").append(label.getId()).append("\n");
                break;
            case IRElem.BNZ:
                label = (IRLabelSymbol) controlInst.getOp3();
                //outInst.append(loadOp1Symbol(controlInst.getOp1()));
                if (op1 instanceof IRImmSymbol) {
                    op1Reg = immReg;
                    outInst.append("li $" + immReg + ", " + ((IRImmSymbol) op1).getValue() + "\n");
                } else {
                    op1Reg = regUseMap.getOrDefault(op1, null);
                    if (op1Reg == null) {
                        try {
                            if (testVar) {
                                //regAllocForVar(op1, instPos, nextEnd);
                                outInst.append(regAllocForVarNew(op1, instPos, nextEnd));
                                op1Reg = regUseMap.get(op1);
                                outInst.append(loadSymbolToRegister(op1, op1Reg));
                            } else {
                                outInst.append(regAllocForVar(op1, instPos, nextEnd));
                            }
                            //outInst.append(regAllocForVar(op1, instPos, nextEnd));
                            op1Reg = regUseMap.get(op1);
                            //outInst.append(loadSymbolToRegister(op1, op1Reg));
                        } catch (NoRegToUseException e) {
                            op1Reg = reservedReg;
                            outInst.append(loadSymbolToRegister(op1, op1Reg));
                        }
                    }
                }
                outInst.append("bnez $" + op1Reg + ", L").append(label.getId()).append("\n");
                break;
            case IRElem.SETRET:
                //outInst.append(loadOp1Symbol(controlInst.getOp3()));
                IRSymbol op3 = controlInst.getOp3();
                String op3Reg;
                if (op3 instanceof IRImmSymbol) {
                    op3Reg = immReg;
                    outInst.append("li $v0, " + ((IRImmSymbol) op3).getValue() + "\n");
                } else {
                    op3Reg = regUseMap.getOrDefault(op3, null);
                    if (op3Reg == null) {
                        /*
                        try {
                            if (testVar) {
                                //regAllocForVar(op3, instPos, nextEnd);
                                outInst.append(regAllocForVarNew(op3, instPos, nextEnd));
                                op3Reg = regUseMap.get(op3);
                                outInst.append(loadSymbolToRegister(op3, op3Reg));
                            } else {
                                outInst.append(regAllocForVar(op3, instPos, nextEnd));
                            }
                            //outInst.append(regAllocForVar(op3, instPos, nextEnd));
                            op3Reg = regUseMap.get(op3);
                            //outInst.append(loadSymbolToRegister(op3, op3Reg));
                        } catch (NoRegToUseException e) {
                            op3Reg = reservedReg;
                            outInst.append(loadSymbolToRegister(op3, op3Reg));
                        }*/
                        outInst.append(loadSymbolToRegister(op3, "v0"));
                    } else {
                        outInst.append("move $v0, $" + op3Reg + "\n");
                    }
                    //outInst.append("move $v0, $" + op3Reg + "\n");
                }
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
                // 保存所有寄存器
                outInst.append(flushRegPool());
                // 保存现场
                int templateSize = curFunc.getTemplateSize();
                //outInst.append("li $t0, ").append(templateSize).append("\n");
                //outInst.append("addu $t4, $t0, $sp\n"); // $t4存储新的$sp
                outInst.append("subiu $fp, $sp, " + templateSize + "\n"); // $fp存储新的$sp，$sp自顶向下增长
                outInst.append("sw $sp, ").append(
                        -4 * (FunctionTemplate.RET_AREA_NUM)).append("($fp)\n");  // $sp填入现场保存区第一个位置

                outInst.append(loadCallParamList(controlInst.getSymbolList(),
                        funcIR.getfParamList(), "fp", instPos, nextEnd)); // 装填参数
                outInst.append("move $sp, $fp\n"); // $sp指向新位置
//                outInst.append("jal L").append(
//                        ((IRFuncSymbol) controlInst.getOp1()).getEntry().getId()); // 跳转
                outInst.append("jal Func_").append(
                        ((IRFuncSymbol) controlInst.getOp1()).getFunc()); // 跳转
                outInst.append("\n");
                // 保存执行结果
                boolean isResultReserved = false;
                op3 = controlInst.getOp3();
                op3Reg = regUseMap.getOrDefault(op3, null);
                if (op3Reg == null) {
                    try {
                        if (testVar) {
                            //regAllocForVar(op3, instPos, nextEnd);
                            outInst.append(regAllocForVarNew(op3, instPos, nextEnd));
                            op3Reg = regUseMap.get(op3);
                            outInst.append(loadSymbolToRegister(op3, op3Reg));
                        } else {
                            outInst.append(regAllocForVar(op3, instPos, nextEnd, false));
                        }
                        //outInst.append(regAllocForVar(op3, instPos, nextEnd));
                        op3Reg = regUseMap.get(op3);
                    } catch (NoRegToUseException e) {
                        op3Reg = reservedReg;
                        isResultReserved = true;
                    }
                }
                outInst.append("move $" + op3Reg + ", $v0").append("\n");
                if (isResultReserved) {
                    outInst.append(storeRegisterToSymbol(op3, reservedReg));
                }
                break;
        }
        return outInst;
    }

    public StringBuilder memInstTranslate(IRElem memInst, int instPos, int nextEnd) {
        StringBuilder outInst = new StringBuilder();
        IRSymbol op3 = memInst.getOp3();
        IRSymbol op1 = memInst.getOp1();
        IRSymbol op2 = memInst.getOp2();
        boolean isResultReserved = false;
        String op3Reg;
        if (op3 instanceof IRImmSymbol) {
            op3Reg = immReg;
            outInst.append("li $" + immReg + ", " + ((IRImmSymbol) op3).getValue() + "\n");
        } else {
            op3Reg = regUseMap.getOrDefault(op3, null);
            if (op3Reg == null) {
                try {
                    if (testVar) {
                        //regAllocForVar(op3, instPos, nextEnd);
                        outInst.append(regAllocForVarNew(op3, instPos, nextEnd));
                        op3Reg = regUseMap.get(op3);
                        outInst.append(loadSymbolToRegister(op3, op3Reg));
                    } else {
                        if (memInst.getType() == IRElem.STORE) {
                            outInst.append(regAllocForVar(op3, instPos, nextEnd));
                        } else {
                            outInst.append(regAllocForVar(op3, instPos, nextEnd, false));
                        }
                    }
                    //outInst.append(regAllocForVar(op3, instPos, nextEnd));
                    op3Reg = regUseMap.get(op3);
                    /*if (memInst.getType() == IRElem.STORE) {
                        outInst.append(loadSymbolToRegister(op3, op3Reg));
                    }*/
                } catch (NoRegToUseException e) {
                    op3Reg = reservedReg;
                    outInst.append(loadSymbolToRegister(op3, op3Reg));
                }
            }
        }
        switch (memInst.getType()) {
            case IRElem.LOAD:
                String op1Reg = regUseMap.getOrDefault(op1, null);
                if (op1Reg == null) {
                    try {
                        if (testVar) {
                            //regAllocForVar(op1, instPos, nextEnd);
                            outInst.append(regAllocForVarNew(op1, instPos, nextEnd));
                            op1Reg = regUseMap.get(op1);
                            outInst.append(loadSymbolToRegister(op1, op1Reg));
                        } else {
                            outInst.append(regAllocForVar(op1, instPos, nextEnd));
                        }
                        //outInst.append(regAllocForVar(op1, instPos, nextEnd));
                        op1Reg = regUseMap.get(op1);
                        //outInst.append(loadSymbolToRegister(op1, op1Reg)); // 加载地址
                    } catch (NoRegToUseException e) {
                        op1Reg = reservedReg;
                        outInst.append(loadSymbolToRegister(op1, op1Reg));
                    }
                }
                if (op2 instanceof IRImmSymbol) {
                    int offset = ((IRImmSymbol) op2).getValue();
                    outInst.append("lw $" + op3Reg + ", ").append(offset).append("($" + op1Reg + ")\n");
                } else {
                    String op2Reg = regUseMap.getOrDefault(op2, null);
                    if (op2Reg == null) {
                        try {
                            if (testVar) {
                                //regAllocForVar(op2, instPos, nextEnd);
                                outInst.append(regAllocForVarNew(op2, instPos, nextEnd));
                                op2Reg = regUseMap.get(op2);
                                outInst.append(loadSymbolToRegister(op2, op2Reg));
                            } else {
                                outInst.append(regAllocForVar(op2, instPos, nextEnd));
                            }
                            //outInst.append(regAllocForVar(op2, instPos, nextEnd));
                            op2Reg = regUseMap.get(op2);
                            //outInst.append(loadSymbolToRegister(op2, op2Reg));
                        } catch (NoRegToUseException e) {
                            op2Reg = reservedReg;
                            outInst.append(loadSymbolToRegister(op2, op2Reg));
                        }
                    }
                    outInst.append("addu $" + op3Reg + ", $" + op1Reg + ", $" + op2Reg + "\n");
                    outInst.append("lw $" + op3Reg + ", 0($" + op3Reg + ")\n");
                    // 此处临时借op3的寄存器存放一下地址，随后马上覆盖
                }
                if (isResultReserved) {
                    outInst.append(storeRegisterToSymbol(op3, reservedReg));
                }
                break;
            case IRElem.STORE:
                op1Reg = regUseMap.getOrDefault(op1, null);
                if (op1Reg == null) {
                    try {
                        if (testVar) {
                            //regAllocForVar(op1, instPos, nextEnd);
                            outInst.append(regAllocForVarNew(op1, instPos, nextEnd));
                            op1Reg = regUseMap.get(op1);
                            outInst.append(loadSymbolToRegister(op1, op1Reg));
                        } else {
                            outInst.append(regAllocForVar(op1, instPos, nextEnd));
                        }
                        //outInst.append(regAllocForVar(op1, instPos, nextEnd));
                        op1Reg = regUseMap.get(op1);
                        //outInst.append(loadSymbolToRegister(op1, op1Reg)); // 加载地址
                    } catch (NoRegToUseException e) {
                        op1Reg = reservedReg;
                        outInst.append(loadSymbolToRegister(op1, op1Reg));
                    }
                }
                if (op2 instanceof IRImmSymbol) {
                    int offset = ((IRImmSymbol) op2).getValue();
                    outInst.append("sw $" + op3Reg + ", ").append(offset).append("($" + op1Reg + ")\n");
                } else {
                    String op2Reg = regUseMap.getOrDefault(op2, null);
                    if (op2Reg == null) {
                        try {
                            if (testVar) {
                                //regAllocForVar(op2, instPos, nextEnd);
                                outInst.append(regAllocForVarNew(op2, instPos, nextEnd));
                                op2Reg = regUseMap.get(op2);
                                outInst.append(loadSymbolToRegister(op2, op2Reg));
                            } else {
                                outInst.append(regAllocForVar(op2, instPos, nextEnd));
                            }
                            outInst.append(regAllocForVar(op2, instPos, nextEnd));
                            op2Reg = regUseMap.get(op2);
                            //outInst.append(loadSymbolToRegister(op2, op2Reg));
                        } catch (NoRegToUseException e) {
                            op2Reg = reservedReg;
                            outInst.append(loadSymbolToRegister(op2, op2Reg));
                        }
                    }
                    //outInst.append(loadOp2Symbol(op2));
                    outInst.append("addu $" + op1Reg + ", $" + op1Reg + ", $" + op2Reg + "\n");
                    outInst.append("sw $" + op3Reg + ", 0($" + op1Reg + ")\n");
                    outInst.append("subu $" + op1Reg + ", $" + op1Reg + ", $" + op2Reg + "\n");
                    // 恢复原状，保证后面若有使用的正确性
                }
                break;
            case IRElem.ALLOCA:
                /*
                IRImmSymbol allocaSize = (IRImmSymbol) memInst.getOp1();
                curPos += allocaSize.getValue();
                outInst.append("li $t0, ").append(curPos).append("\n");*/
                IRImmSymbol allocaSize = (IRImmSymbol) memInst.getOp1();
                int offset = curFunc.getSymbolOffset(op3);
                int size = round(allocaSize.getValue()); // 按4字节取整
                int contextOffset = offset + size;
                outInst.append("li $" + op3Reg + ", ").append(contextOffset).append("\n");
                outInst.append("subu $" + op3Reg + ", $sp, $" + op3Reg + "\n");
                if (isResultReserved) {
                    outInst.append(storeRegisterToSymbol(op3, reservedReg));
                }
                break;
        }
        return outInst;
    }

    public StringBuilder ioInstTranslate(IRElem ioInst, int instPos, int nextEnd) {
        StringBuilder outInst = new StringBuilder();
        IRSymbol op3 = ioInst.getOp3();
        boolean isResultReserved = false;
        String op3Reg;
        if (op3 instanceof IRImmSymbol) {
            op3Reg = immReg;
            outInst.append("li $" + immReg + ", " + ((IRImmSymbol) op3).getValue() + "\n");
        } else {
            op3Reg = regUseMap.getOrDefault(op3, null);
            if (op3Reg == null) {
                try {
                    if (testVar) {
                        //regAllocForVar(op3, instPos, nextEnd);
                        outInst.append(regAllocForVarNew(op3, instPos, nextEnd));
                        op3Reg = regUseMap.get(op3);
                        outInst.append(loadSymbolToRegister(op3, op3Reg));
                    } else {
                        if (ioInst.getType() == IRElem.GETINT) {
                            outInst.append(regAllocForVar(op3, instPos, nextEnd, false));
                        } else {
                            outInst.append(regAllocForVar(op3, instPos, nextEnd));
                        }
                    }
                    //outInst.append(regAllocForVar(op3, instPos, nextEnd));
                    op3Reg = regUseMap.get(op3);
                    /*if (ioInst.getType() != IRElem.GETINT) {
                        outInst.append(loadSymbolToRegister(op3, op3Reg));
                    }*/
                } catch (NoRegToUseException e) {
                    op3Reg = reservedReg;
                    outInst.append(loadSymbolToRegister(op3, op3Reg));
                }
            }
        }
        switch (ioInst.getType()) {
            case IRElem.GETINT:
                outInst.append("li $v0, 5\n");
                outInst.append("syscall\n");
                outInst.append("move $" + op3Reg + ", $v0\n");
                if (isResultReserved) {
                    outInst.append(storeRegisterToSymbol(op3, reservedReg));
                }
                //outInst.append(storeOp3Symbol(ioInst.getOp3()));
                break;
            case IRElem.PRINTS:
                //outInst.append(loadOp1Symbol(ioInst.getOp3()));
                outInst.append("li $v0, 4\n");
                outInst.append("move $a0, $" + op3Reg + "\n");
                outInst.append("syscall\n");
                break;
            case IRElem.PRINTI:
                //outInst.append(loadOp1Symbol(ioInst.getOp3()));
                outInst.append("li $v0, 1\n");
                outInst.append("move $a0, $" + op3Reg + "\n");
                outInst.append("syscall\n");
                break;
        }
        return outInst;
    }

    public StringBuilder loadCallParamList(ArrayList<IRSymbol> rParamList,
                                           ArrayList<IRSymbol> fParamList, String newSPVal,
                                           int instPos, int nextEnd) {
        StringBuilder outStr = new StringBuilder();
        int localVarOffset = curFunc.getLocalVarOffsetBytes();
        /*for (int i = 0; i < rParamList.size(); ++i) {
            outStr.append(loadOp3Symbol(rParamList.get(i)));
            outStr.append(storeOp3Symbol(fParamList.get(i)));
        }*/
        for (int i = 0; i < rParamList.size(); ++i) {
            IRSymbol paramSymbol = rParamList.get(i);
            //outStr.append(loadOp1Symbol(paramSymbol));
            String paramReg;
            if (paramSymbol instanceof IRImmSymbol) {
                paramReg = immReg;
                outStr.append("li $" + immReg + ", " + ((IRImmSymbol) paramSymbol).getValue() + "\n");
            } else {
                /*paramReg = regUseMap.getOrDefault(paramSymbol, null);
                if (paramReg == null) {
                    try {
                        regAllocForVar(paramSymbol, instPos, nextEnd);
                        paramReg = regUseMap.get(paramSymbol);
                        outStr.append(loadSymbolToRegister(paramSymbol, paramReg));
                    } catch (NoRegToUseException e) {
                        paramReg = reservedReg;
                    }
                }*/
                paramReg = reservedReg;
                outStr.append(loadSymbolToRegister(paramSymbol, paramReg));
            }
            // curPos += 4;
            if (i <= 3) {
                outStr.append("move $a").append(i).append(", $" + paramReg + "\n");
            }
            outStr.append("sw $" + paramReg + ", ").append(-(localVarOffset + i * 4)).
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
