package IR;

import IR.IRArrSymbol;
import IR.IRElem;
import IR.IRFuncSymbol;
import IR.IRImmSymbol;
import IR.IRLabelManager;
import IR.IRLabelSymbol;
import IR.IRSymbol;
import Symbols.FuncSymbol;
import Symbols.SymbolTable;
import Symbols.VarSymbol;
import SyntaxClasses.ConstIntToken;
import SyntaxClasses.FormatStringToken;
import SyntaxClasses.SyntaxClass;
import SyntaxClasses.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

public class IRTranslater {
    private SyntaxClass compUnit;
    private HashMap<VarSymbol, IRSymbol> constantArrMap; // 常量数组无法消干净
    private HashMap<VarSymbol, IRSymbol> globalArrMap; // 全局数组
    private HashMap<IRSymbol, String> formatStrMap; // 格式字符串作常量存
    private HashMap<String, IRFuncSymbol> funcMap; // 函数名和函数标签对应
    private LinkedList<IRElem> iRList;
    private int globalVarID;
    private IRLabelManager iRLabelManager;
    private IRSymbol mainFunc;

    public IRTranslater(SyntaxClass compUnit) {
        this.compUnit = compUnit;
        constantArrMap = new HashMap<>();
        globalArrMap = new HashMap<>();
        formatStrMap = new HashMap<>();
        funcMap = new HashMap<>();
        iRList = new LinkedList<>();
        globalVarID = 0;
        iRLabelManager = IRLabelManager.getIRLabelManager();
        mainFunc = null;
    }

    public void setiRList(LinkedList<IRElem> iRList) {
        this.iRList = iRList;
    }

    public SyntaxClass getCompUnit() {
        return compUnit;
    }

    public IRSymbol getMainFunc() {
        return mainFunc;
    }

    public IRLabelManager getIRLabelManager() {
        return iRLabelManager;
    }

    public LinkedList<IRElem> getIRList() {
        return iRList;
    }

    public HashMap<IRSymbol, String> getFormatStrMap() {
        return formatStrMap;
    }

    public HashMap<VarSymbol, IRSymbol> getConstantArrMap() {
        return constantArrMap;
    }

    public HashMap<VarSymbol, IRSymbol> getGlobalArrMap() {
        return globalArrMap;
    }

    public void constDeclTrans(SyntaxClass constDecl) {
        ArrayList<SyntaxClass> sonList = constDecl.getSonNodeList();
        for (int i = 2; i < sonList.size() - 1; i += 2) {
            constDefTrans(sonList.get(i));
        }
    }

    public void compUnitTrans() {
        ArrayList<SyntaxClass> sonList = compUnit.getSonNodeList();
        for (SyntaxClass syntaxClass : sonList) {
            if (syntaxClass.getSyntaxType() == SyntaxClass.DECL) {
                declTrans(syntaxClass, true);
            } else if (syntaxClass.getSyntaxType() == SyntaxClass.FUNCDEF) {
                funcDefTrans(syntaxClass);
            } else {
                mainFunc = mainFuncDefTrans(syntaxClass);
                /*IRSymbol funcIRSymbol = funcMap.get("main");
                IRSymbol retSymbol = iRLabelManager.allocSymbol();
                ArrayList<IRSymbol> paramList = new ArrayList<>();
                IRElem progEnter = new IRElem(IRElem.CALL, retSymbol, funcIRSymbol, paramList);*/
                IRElem progEnter = new IRElem(IRElem.BR, mainFunc);
                iRList.addFirst(progEnter);
            }
        }
    }

    public void constDefTrans(SyntaxClass constDef) {
        ArrayList<SyntaxClass> sonList = constDef.getSonNodeList();
        Token ident = (Token) sonList.get(0);
        SymbolTable curEnv = constDef.getCurEnv();
        VarSymbol constSymbol = curEnv.varGlobalLookup(ident.getTokenContext());
        if (constSymbol.getDimType() != 0) {
            IRLabelSymbol constIRLabel = iRLabelManager.allocSymbol();
            constantArrMap.put(constSymbol, constIRLabel);
            curEnv.setVarRef(constSymbol, constIRLabel); // 常值也需要标记起始地址！翻译时再单独处理！
        }
    }

    public void varDeclTrans(SyntaxClass varDecl, boolean isGlobal) {
        ArrayList<SyntaxClass> sonList = varDecl.getSonNodeList();
        for (int i = 1; i < sonList.size() - 1; i += 2) {
            varDefTrans(sonList.get(i), isGlobal);
        }
    }

    public void varDefTrans(SyntaxClass varDef, boolean isGlobal) {
        ArrayList<SyntaxClass> sonList = varDef.getSonNodeList();
        Token ident = (Token) sonList.get(0);
        SymbolTable curEnv = varDef.getCurEnv();
        VarSymbol varSymbol = curEnv.varGlobalLookup(ident.getTokenContext());
        if (varSymbol.getDimType() == 0) { // 单变量，考虑是否初始化
            if (sonList.size() > 1) { // 有初始化
                IRSymbol initValSymbol = singleInitValTrans(sonList.get(2)); // 初始值算出来赋值到一个临时变量上
                IRLabelSymbol varIRLabel = iRLabelManager.allocSymbol(); // 给新定义的变量申请符号
                curEnv.setVarRef(varSymbol, varIRLabel); // 设置引用
                if (isGlobal) {
                    globalArrMap.put(varSymbol, varIRLabel);

                    varIRLabel.setGlobal(true);
                } else {
                    IRElem varInitIRElem = new IRElem(IRElem.ASSIGN, varIRLabel, initValSymbol); // 赋值
                    iRList.add(varInitIRElem);
                }
            } else if (isGlobal) { // 全局变量，无初始化，需置0
                IRLabelSymbol varIRSymbol = iRLabelManager.allocSymbol(); // 申请符号
                varIRSymbol.setGlobal(true);
                curEnv.setVarRef(varSymbol, varIRSymbol);
                globalArrMap.put(varSymbol, varIRSymbol);
            } else { //局部变量，无初始化，需要申请符号设置引用关系，以备后续使用
                IRLabelSymbol varIRSymbol = iRLabelManager.allocSymbol();
                curEnv.setVarRef(varSymbol, varIRSymbol);
            }
        } else { // 定义数组时申请空间
            int memSize = 0;
            if (varSymbol.getDimType() == 1) { // 一维
                memSize = varSymbol.getDimLength(0);
            } else { // 二维
                memSize = varSymbol.getDimLength(1) * varSymbol.getDimLength(0);
            }
            IRLabelSymbol irLabel = iRLabelManager.allocSymbol(); // 申请中间变量符号
            irLabel.setGlobal(isGlobal);
            curEnv.setVarRef(varSymbol, irLabel); // 设置当前变量引用关系
            if (isGlobal) { // 全局数组直接设置引用关系，空间已经在数据区分配好
                globalArrMap.put(varSymbol, irLabel);
            } else { // 非全局数组再申请空间
                IRElem identIRElem = new IRElem(IRElem.ALLOCA, irLabel, new IRImmSymbol(memSize * 4));
                iRList.add(identIRElem);
            }
            // 全局数组初始化求值已在前面的Simplify处理完成
            if (varSymbol.getDimType() == 1 && sonList.size() > 4 && !isGlobal) { // 局部一维数组初始化
                SyntaxClass arrInitVal = sonList.get(5);
                ArrayList<SyntaxClass> initValList = arrInitVal.getSonNodeList();
                for (int i = 0; i < varSymbol.getDimLength(0); ++i) {
                    IRSymbol initSymbol = singleInitValTrans(initValList.get(2 * i + 1)); // 计算要赋的值
                    IRElem initElem = new IRElem(IRElem.STORE, initSymbol, irLabel,
                            new IRImmSymbol(i * 4));
                    iRList.add(initElem);
                }
            } else if (varSymbol.getDimType() == 2 && sonList.size() > 7 && !isGlobal) { // 局部二维数组初始化
                SyntaxClass arrInitVal = sonList.get(8);
                ArrayList<SyntaxClass> initValListList = arrInitVal.getSonNodeList();
                for (int i = 0; i < varSymbol.getDimLength(1); ++i) {
                    ArrayList<SyntaxClass> initValList = initValListList.get(2 * i + 1).getSonNodeList();
                    for (int j = 0; j < varSymbol.getDimLength(0); ++j) {
                        IRSymbol initSymbol = singleInitValTrans(initValList.get(2 * j + 1)); // 计算要赋的值
                        IRElem initElem = new IRElem(IRElem.STORE, initSymbol, irLabel,
                                new IRImmSymbol((i * varSymbol.getDimLength(0) + j) * 4));
                        iRList.add(initElem);
                    }
                }
            }
        }
    }

    public IRSymbol singleInitValTrans(SyntaxClass initVal) { // InitVal -> Exp
        SyntaxClass exp = initVal.getSonNodeList().get(0);
        IRSymbol expIRSymbol;
        if (exp.getSyntaxType() == SyntaxClass.CONSTEXP) {
            expIRSymbol = constExpTrans(exp);
        } else {
            expIRSymbol = expTrans(exp);
        }
        return expIRSymbol;
    }

    public IRSymbol constExpTrans(SyntaxClass constExp) {
        if (!constExp.isCalculated()) {
            CompUnitSimplifyer.constExpCal(constExp);
        }
        return new IRImmSymbol(constExp.getConstValue());
    }

    public IRSymbol expTrans(SyntaxClass exp) {
        if (exp.isCalculated()) {
            return new IRImmSymbol(exp.getConstValue());
        }
        return addExpTrans(exp.getSonNodeList().get(0));
    }

    public IRSymbol addExpTrans(SyntaxClass addExp) {
        if (addExp.isCalculated()) {
            return new IRImmSymbol(addExp.getConstValue());
        }
        IRSymbol symbol0;
        SyntaxClass subExp0 = addExp.getSonNodeList().get(0);
        if (subExp0.getSyntaxType() == SyntaxClass.MULEXP) {
            symbol0 = mulExpTrans(subExp0);
        } else {
            symbol0 = addExpTrans(subExp0);
        }
        if (addExp.getSonNodeList().size() > 1) { // Exp0 +/- Exp1
            SyntaxClass subExp1 = addExp.getSonNodeList().get(2);
            IRSymbol symbol1;
            if (subExp1.getSyntaxType() == SyntaxClass.MULEXP) {
                symbol1 = mulExpTrans(subExp1);
            } else {
                symbol1 = addExpTrans(subExp1);
            }
            Token mathToken = (Token) addExp.getSonNodeList().get(1);
            IRLabelSymbol resSymbol = iRLabelManager.allocSymbol(); // 申请新符号
            IRElem exprIR;
            if (mathToken.getTokenType() == Token.PLUS) {
                exprIR = new IRElem(IRElem.ADD, resSymbol, symbol0, symbol1);
            } else {
                exprIR = new IRElem(IRElem.MINU, resSymbol, symbol0, symbol1);
            }
            iRList.add(exprIR);
            return resSymbol;
        } else {
            return symbol0;
        }
    }

    public IRSymbol mulExpTrans(SyntaxClass mulExp) {
        if (mulExp.isCalculated()) {
            return new IRImmSymbol(mulExp.getConstValue());
        }
        IRSymbol symbol0;
        SyntaxClass subExp0 = mulExp.getSonNodeList().get(0);
        if (subExp0.getSyntaxType() == SyntaxClass.UNARYEXP) {
            symbol0 = unaryExpTrans(subExp0);
        } else {
            symbol0 = mulExpTrans(subExp0);
        }
        if (mulExp.getSonNodeList().size() > 1) { // Exp0 * or / Exp1
            SyntaxClass subExp1 = mulExp.getSonNodeList().get(2);
            IRSymbol symbol1;
            if (subExp1.getSyntaxType() == SyntaxClass.UNARYEXP) {
                symbol1 = unaryExpTrans(subExp1);
            } else {
                symbol1 = mulExpTrans(subExp1);
            }
            Token mathToken = (Token) mulExp.getSonNodeList().get(1);
            IRLabelSymbol resSymbol = iRLabelManager.allocSymbol(); // 申请新符号
            IRElem exprIR;
            if (mathToken.getTokenType() == Token.MULT) {
                exprIR = new IRElem(IRElem.MULT, resSymbol, symbol0, symbol1);
            } else if (mathToken.getTokenType() == Token.DIV) {
                exprIR = new IRElem(IRElem.DIV, resSymbol, symbol0, symbol1);
            } else {
                exprIR = new IRElem(IRElem.MOD, resSymbol, symbol0, symbol1);
            }
            iRList.add(exprIR);
            return resSymbol;
        } else {
            return symbol0;
        }
    }

    public IRSymbol unaryExpTrans(SyntaxClass unaryExp) {
        if (unaryExp.isCalculated()) {
            return new IRImmSymbol(unaryExp.getConstValue());
        }
        ArrayList<SyntaxClass> sonList = unaryExp.getSonNodeList();
        if (sonList.size() == 1) { // PrimaryExp
            return primaryExpTrans(sonList.get(0));
        } else if (sonList.size() == 2) { // UnaryOp UnaryExp
            IRSymbol resSymbol;
            IRSymbol subExpSymbol = unaryExpTrans(sonList.get(1));
            Token mathSymbol = (Token) sonList.get(0).getSonNodeList().get(0);
            if (mathSymbol.getTokenType() == Token.MINU) { // -
                resSymbol = iRLabelManager.allocSymbol();
                IRElem exprIR = new IRElem(IRElem.MINU, resSymbol,
                        IRImmSymbol.ZERO, subExpSymbol);
                iRList.add(exprIR);
            } else if (mathSymbol.getTokenType() == Token.PLUS) { // +
                resSymbol = subExpSymbol;
            } else { // !
                resSymbol = iRLabelManager.allocSymbol();
                IRElem exprIR = new IRElem(IRElem.EQL, resSymbol,
                        IRImmSymbol.ZERO, subExpSymbol); // 用 EQL 0 来逻辑取反
                iRList.add(exprIR);
            }
            return resSymbol;
        } else {
            return funcCallTrans(unaryExp);
        }
    }

    public IRSymbol funcCallTrans(SyntaxClass funcCallUnaryExp) {
        ArrayList<SyntaxClass> funcCallList = funcCallUnaryExp.getSonNodeList();
        Token funcIdent = (Token) funcCallList.get(0);
        SymbolTable curEnv = funcCallUnaryExp.getCurEnv();
        FuncSymbol funcSymbol = curEnv.funcGlobalLookup(funcIdent.getTokenContext());
        SyntaxClass funcRParams = funcCallList.get(2);
        ArrayList<IRSymbol> paramList = new ArrayList<>();
        if (funcRParams.getSyntaxType() == SyntaxClass.FUNCRPARAMS) { // 从左往右计算各参数
            ArrayList<SyntaxClass> realParamList = funcRParams.getSonNodeList();
            for (int i = 0; i < realParamList.size(); i += 2) {
                IRSymbol paramSymbol = expTrans(realParamList.get(i));
                paramList.add(paramSymbol);
            }
        }
        IRSymbol funcIRSymbol = funcMap.get(funcIdent.getTokenContext());
        IRSymbol retSymbol = iRLabelManager.allocSymbol();
        IRElem funcCalling = new IRElem(IRElem.CALL, retSymbol, funcIRSymbol, paramList);
        iRList.add(funcCalling);
        return retSymbol;
    }

    public IRSymbol primaryExpTrans(SyntaxClass primaryExp) {
        if (primaryExp.isCalculated()) {
            return new IRImmSymbol(primaryExp.getConstValue());
        }
        SyntaxClass subExp = primaryExp.getSonNodeList().get(0);
        if (subExp.getSyntaxType() == SyntaxClass.NUMBER) {
            return numberTrans(subExp);
        } else if (subExp.getSyntaxType() == SyntaxClass.LVAL) {
            // 此处LVal只会取值不会存值
            IRSymbol lValSymbol = lValTrans(subExp);
            if (lValSymbol instanceof IRArrSymbol) { // 数组
                IRSymbol valueSymbol = iRLabelManager.allocSymbol();
                IRElem loadElem = new IRElem(IRElem.LOAD, valueSymbol,
                        ((IRArrSymbol) lValSymbol).getBaseAddr(),
                        ((IRArrSymbol) lValSymbol).getOffset());
                iRList.add(loadElem);
                return valueSymbol;
            } else {
                return lValSymbol;
            }
        } else {
            subExp = primaryExp.getSonNodeList().get(1);
            return expTrans(subExp);
        }
    }

    public IRSymbol numberTrans(SyntaxClass number) {
        if (number.isCalculated()) {
            return new IRImmSymbol(number.getConstValue());
        }
        ConstIntToken subExp = (ConstIntToken) number.getSonNodeList().get(0);
        return new IRImmSymbol(subExp.getMyValue());
    }

    public IRSymbol lValTrans(SyntaxClass lVal) {
        if (lVal.isCalculated()) {
            return new IRImmSymbol(lVal.getConstValue());
        }
        ArrayList<SyntaxClass> sonList = lVal.getSonNodeList();
        Token identToken = (Token) sonList.get(0);
        SymbolTable curEnv = lVal.getCurEnv();
        VarSymbol identSymbol = curEnv.varGlobalLookup(
                identToken.getTokenContext(), lVal.getCurVarListPos());
        if (identSymbol.getDimType() == 0) {
            return lVal0DimTrans(lVal);
        } else {
            return lValArrTrans(lVal);
        }
    }

    public IRSymbol lVal0DimTrans(SyntaxClass lVal) { // 单常量/变量解析
        if (lVal.isCalculated()) {
            return new IRImmSymbol(lVal.getConstValue());
        }
        ArrayList<SyntaxClass> sonList = lVal.getSonNodeList();
        Token identToken = (Token) sonList.get(0);
        SymbolTable curEnv = lVal.getCurEnv();
        VarSymbol identSymbol = curEnv.varGlobalLookup(
                identToken.getTokenContext(), lVal.getCurVarListPos());
        if (identSymbol.isVar()) {
            IRLabelSymbol lValVarSymbol = curEnv.getLastVarRef(identSymbol);
            if (lValVarSymbol == null) {
                lValVarSymbol = iRLabelManager.allocSymbol();
                // TODO: 刚刚修改的LVal新申请符号时加入记录
                curEnv.setVarRef(identSymbol, lValVarSymbol);
            } else if (lValVarSymbol.isGlobal()) { // 全局变量，视作数组
                return new IRArrSymbol(lValVarSymbol, IRImmSymbol.ZERO);
            }
            return lValVarSymbol;
        } else {
            return new IRImmSymbol(identSymbol.constGetValue());
        }
    }

    public IRSymbol lValArrTrans(SyntaxClass lVal) { // 数组解析
        if (lVal.isCalculated()) {
            return new IRImmSymbol(lVal.getConstValue());
        }
        ArrayList<SyntaxClass> sonList = lVal.getSonNodeList();
        Token identToken = (Token) sonList.get(0);
        SymbolTable curEnv = lVal.getCurEnv();
        VarSymbol identSymbol = curEnv.varGlobalLookup(
                identToken.getTokenContext(), lVal.getCurVarListPos());
        IRSymbol baseSymbol = curEnv.getLastVarRef(identSymbol);
        if (sonList.size() == 1) {
            // 明明是数组，但只传下来一个单独的Ident，说明这里是要地址
            return baseSymbol;
        }
        SyntaxClass dim1Exp = sonList.get(2);
        IRSymbol dim1Symbol = expTrans(dim1Exp);
        if (identSymbol.getDimType() == 1) { // 一维数组
            IRSymbol trueOffset = iRLabelManager.allocSymbol();
            IRElem offsetCal = new IRElem(IRElem.MULT, trueOffset, dim1Symbol, new IRImmSymbol(4));
            iRList.add(offsetCal);
            return new IRArrSymbol(baseSymbol, trueOffset);
        } else { // 二维数组
            IRSymbol dim1OffSymbol;
            if (dim1Symbol instanceof IRImmSymbol) { // 第1维是常数，直接算
                dim1OffSymbol = new IRImmSymbol(
                        ((IRImmSymbol) dim1Symbol).getValue() * identSymbol.getDimLength(0));
            } else { // 第1维是变量，需要带变量执行计算
                dim1OffSymbol = iRLabelManager.allocSymbol();
                IRElem dim1OffCal = new IRElem(IRElem.MULT, dim1OffSymbol,
                        dim1Symbol, new IRImmSymbol(identSymbol.getDimLength(0)));
                iRList.add(dim1OffCal);
            }
            if (sonList.size() == 4) {
                // 二维数组，一维形式，说明这里是要地址
                // 先计算按字节的偏移
                IRSymbol trueOffSymbol;
                if (dim1OffSymbol instanceof IRImmSymbol) { // 偏移元素数是常数，直接给结果
                    trueOffSymbol = new IRImmSymbol(
                            ((IRImmSymbol) dim1OffSymbol).getValue() * 4);
                } else { // 偏移元素数是变量，需要带变量执行计算
                    trueOffSymbol = iRLabelManager.allocSymbol();
                    IRElem retAddrCal = new IRElem(IRElem.MULT, trueOffSymbol,
                            dim1OffSymbol, new IRImmSymbol(4));
                    iRList.add(retAddrCal);
                }
                IRSymbol retAddrSymbol = iRLabelManager.allocSymbol();
                IRElem addrCal = new IRElem(IRElem.ADD, retAddrSymbol,
                        baseSymbol, trueOffSymbol);
                iRList.add(addrCal);
                return retAddrSymbol;
            }
            SyntaxClass dim0Exp = sonList.get(5);
            IRSymbol dim0Symbol = expTrans(dim0Exp);
            IRSymbol offset;
            if ((dim1OffSymbol instanceof IRImmSymbol) && (dim0Symbol instanceof IRImmSymbol)) {
                // 两个维度都是常数，直接出结果
                offset = new IRImmSymbol((((IRImmSymbol) dim1OffSymbol).getValue() +
                        ((IRImmSymbol) dim0Symbol).getValue()) * 4);
            } else {
                IRSymbol intOffsetCal = iRLabelManager.allocSymbol();
                IRElem intOffsetCalElem = new IRElem(IRElem.ADD, intOffsetCal, dim1OffSymbol, dim0Symbol);
                iRList.add(intOffsetCalElem);
                offset = iRLabelManager.allocSymbol();
                IRElem offsetCal = new IRElem(IRElem.MULT, offset, intOffsetCal, new IRImmSymbol(4));
                iRList.add(offsetCal);
            }
            return new IRArrSymbol(baseSymbol, offset);
        }
    }

    public IRSymbol condTrans(SyntaxClass cond) {
        return lOrExpTrans(cond.getSonNodeList().get(0));
    }

    public IRSymbol lOrExpTrans(SyntaxClass lOrExp) {
        ArrayList<SyntaxClass> sonList = lOrExp.getSonNodeList();
        if (sonList.size() == 1) { // LOr -> LAnd
            return lAndExpTrans(sonList.get(0));
        }
        // LOr -> LOr || LAnd
        IRSymbol end0 = iRLabelManager.allocSymbol();
        IRSymbol end1 = iRLabelManager.allocSymbol();
        IRSymbol endAll = iRLabelManager.allocSymbol();
        IRSymbol orResSymbol = lOrExpTrans(sonList.get(0));
        if (orResSymbol instanceof IRImmSymbol) { // 第一部分是常数
            if (((IRImmSymbol) orResSymbol).getValue() == 0) {
                return lAndExpTrans(sonList.get(2));
            } else {
                return orResSymbol;
            }
        }
        /* 短路求值操作：
        LOr
        BNZ(End1)
        LAnd
        BNZ(End1)
        #mark = 0
        BR EndAll
        End1:
        #mark = 1
        EndAll:
        * */

        IRElem lOrResTrue = new IRElem(IRElem.BNZ, end1, orResSymbol);
        iRList.add(lOrResTrue);

        IRSymbol andResSymbol = lAndExpTrans(sonList.get(2));
        IRElem lAndResTrue = new IRElem(IRElem.BNZ, end1, andResSymbol);
        iRList.add(lAndResTrue);

        IRSymbol resSymbol = iRLabelManager.allocSymbol();
        IRElem resAssign0 = new IRElem(IRElem.ASSIGN, resSymbol, IRImmSymbol.ZERO);
        iRList.add(resAssign0);
        IRElem endAllElem = new IRElem(IRElem.BR, endAll);
        iRList.add(endAllElem);

        IRElem end1Label = new IRElem(IRElem.LABEL, end1);
        iRList.add(end1Label);
        IRElem resAssign1 = new IRElem(IRElem.ASSIGN, resSymbol, new IRImmSymbol(1));
        iRList.add(resAssign1);
        IRElem endAllLabel = new IRElem(IRElem.LABEL, endAll);
        iRList.add(endAllLabel);
        /*if (andResSymbol instanceof IRImmSymbol) { // 第二部分是常数
            if (((IRImmSymbol) andResSymbol).getValue() == 0) {
                return orResSymbol;
            } else {
                return andResSymbol;
            }
        }*/
        // 都不是常数
        //IRElem lOrElem = new IRElem(IRElem.OR, resSymbol, orResSymbol, andResSymbol);
        //iRList.add(lOrElem);
        return resSymbol;
    }

    public IRSymbol lAndExpTrans(SyntaxClass lAndExp) {
        ArrayList<SyntaxClass> sonList = lAndExp.getSonNodeList();
        if (sonList.size() == 1) { // LAnd -> Eq
            return eqExpTrans(sonList.get(0));
        }
        // LAnd -> LAnd && Eq
        IRSymbol end0 = iRLabelManager.allocSymbol();
        IRSymbol end1 = iRLabelManager.allocSymbol();
        IRSymbol endAll = iRLabelManager.allocSymbol();
        IRSymbol andResSymbol = lAndExpTrans(sonList.get(0));
        if (andResSymbol instanceof IRImmSymbol) { // 第一部分是常数
            if (((IRImmSymbol) andResSymbol).getValue() != 0) {
                return eqExpTrans(sonList.get(2));
            } else {
                return andResSymbol;
            }
        }
        /* 短路求值操作：
        LAnd
        BZ(End0)
        Eq
        BZ(End0)
        #mark = 1
        BR ENDALL
        End0:
        #mark = 0
        ENDALL:
        * */
        IRElem lAndFalse = new IRElem(IRElem.BZ, end0, andResSymbol);
        iRList.add(lAndFalse);

        IRSymbol eqResSymbol = eqExpTrans(sonList.get(2));
        IRElem eqFalse = new IRElem(IRElem.BZ, end0, eqResSymbol);
        iRList.add(eqFalse);

        IRSymbol resSymbol = iRLabelManager.allocSymbol();
        IRElem resAssign1 = new IRElem(IRElem.ASSIGN, resSymbol, new IRImmSymbol(1));
        iRList.add(resAssign1);
        IRElem endAllElem = new IRElem(IRElem.BR, endAll);
        iRList.add(endAllElem);

        IRElem end0Label = new IRElem(IRElem.LABEL, end0);
        iRList.add(end0Label);
        IRElem resAssign0 = new IRElem(IRElem.ASSIGN, resSymbol, IRImmSymbol.ZERO);
        iRList.add(resAssign0);
        IRElem endAllLabel = new IRElem(IRElem.LABEL, endAll);
        iRList.add(endAllLabel);
        /*if (eqResSymbol instanceof IRImmSymbol) { // 第二部分是常数
            if (((IRImmSymbol) eqResSymbol).getValue() != 0) {
                return andResSymbol;
            } else {
                return eqResSymbol;
            }
        }*/
        // 都不是常数
        //IRElem lAndElem = new IRElem(IRElem.AND, resSymbol, andResSymbol, eqResSymbol);
        //iRList.add(lAndElem);
        return resSymbol;
    }

    public IRSymbol eqExpTrans(SyntaxClass eqExp) { // 须保证结果仅为1或0，否则在AND部分可能出现算术错误
        ArrayList<SyntaxClass> sonList = eqExp.getSonNodeList();
        if (sonList.size() == 1) { // Eq -> Rel
            return relExpTrans(sonList.get(0));
        }
        // Eq -> Eq (== or !=) Rel
        IRSymbol eqResSymbol = eqExpTrans(sonList.get(0));
        IRSymbol relResSymbol = relExpTrans(sonList.get(2));
        // 是不是常数都得算
        IRSymbol resSymbol = iRLabelManager.allocSymbol();
        IRElem eqElem;
        if (((Token) sonList.get(1)).getTokenType() == Token.EQL) { // ==
            eqElem = new IRElem(IRElem.EQL, resSymbol, eqResSymbol, relResSymbol);
        } else { // !=
            eqElem = new IRElem(IRElem.NEQ, resSymbol, eqResSymbol, relResSymbol);
        }
        iRList.add(eqElem);
        return resSymbol;
    }

    public IRSymbol relExpTrans(SyntaxClass relExp) { // 须保证结果仅为1或0，否则在AND部分可能出现算术错误
        ArrayList<SyntaxClass> sonList = relExp.getSonNodeList();
        if (sonList.size() == 1) { // Rel -> Add
            IRSymbol addSymbol = addExpTrans(sonList.get(0));
            /*if (addSymbol instanceof IRImmSymbol) { // Add为常数，则直接判0返回
                if (((IRImmSymbol) addSymbol).getValue() == 0) {
                    return addSymbol;
                } else {
                    return new IRImmSymbol(1);
                }
            }
            // Add非常数，则执行判0指令
            IRSymbol resSymbol = iRLabelManager.allocSymbol();
            IRElem relElem = new IRElem(IRElem.NEQ, resSymbol, addSymbol, IRImmSymbol.ZERO); // 用 NEQ 0 判断是否非0
            iRList.add(relElem);*/
            return addSymbol;
        }
        // Rel -> Rel <symbol> Add
        IRSymbol relResSymbol = relExpTrans(sonList.get(0));
        IRSymbol addResSymbol = addExpTrans(sonList.get(2));
        // 是不是常数都得算
        IRSymbol resSymbol = iRLabelManager.allocSymbol();
        IRElem eqElem;
        if (((Token) sonList.get(1)).getTokenType() == Token.LSS) { // <
            eqElem = new IRElem(IRElem.LSS, resSymbol, relResSymbol, addResSymbol);
        } else if (((Token) sonList.get(1)).getTokenType() == Token.GRE) { // >
            //eqElem = new IRElem(IRElem.GRE, resSymbol, relResSymbol, addResSymbol);
            eqElem = new IRElem(IRElem.LSS, resSymbol, addResSymbol, relResSymbol);
        } else if (((Token) sonList.get(1)).getTokenType() == Token.LEQ) { // <=
            eqElem = new IRElem(IRElem.LEQ, resSymbol, relResSymbol, addResSymbol);
        } else { // >=
            //eqElem = new IRElem(IRElem.GEQ, resSymbol, relResSymbol, addResSymbol);
            eqElem = new IRElem(IRElem.LEQ, resSymbol, addResSymbol, relResSymbol);
        }
        iRList.add(eqElem);
        return resSymbol;
    }

    public IRSymbol funcDefTrans(SyntaxClass funcDef) {
        ArrayList<SyntaxClass> sonList = funcDef.getSonNodeList();
        Token funcIdent = (Token) sonList.get(1);
        SymbolTable curEnv = funcDef.getCurEnv();
        FuncSymbol funcSymbol = curEnv.funcGlobalLookup(funcIdent.getTokenContext());
        IRLabelSymbol funcLabelSymbol = iRLabelManager.allocSymbol();
        curEnv.setFuncRef(funcSymbol, funcLabelSymbol); // 保存函数引用信息
        IRSymbol funcRetSymbol = iRLabelManager.allocSymbol(); // 申请函数统一返回出口标签
        funcSymbol.setReturnSymbol(funcRetSymbol); // 设置返回标签
        ArrayList<IRSymbol> fParamSymbols;
        SyntaxClass block;
        if (sonList.size() == 6) { // 有形参
            fParamSymbols = fParamsTrans(sonList.get(3));
            block = sonList.get(5);
        } else {
            fParamSymbols = new ArrayList<>();
            block = sonList.get(4);
        }
        IRFuncSymbol funcIRSymbol = new IRFuncSymbol(funcIdent.getTokenContext());
        funcIRSymbol.setEntry(funcLabelSymbol);
        funcIRSymbol.setfParamList(fParamSymbols);
        funcMap.put(funcIdent.getTokenContext(), funcIRSymbol);
        IRElem funcDefElem = new IRElem(IRElem.FUNC, funcIRSymbol, fParamSymbols);
        iRList.add(funcDefElem);
        IRElem funcDefLabelElem = new IRElem(IRElem.LABEL, funcLabelSymbol);
        iRList.add(funcDefLabelElem); // 插入函数定义标签
        blockTrans(block, false);
        IRElem returnLabel = new IRElem(IRElem.LABEL, funcRetSymbol);
        iRList.add(returnLabel);
        IRElem voidReturnElem = new IRElem(IRElem.RET);
        iRList.add(voidReturnElem);
        return funcLabelSymbol;
    }

    public IRSymbol mainFuncDefTrans(SyntaxClass funcDef) {
        ArrayList<SyntaxClass> sonList = funcDef.getSonNodeList();
        Token funcIdent = (Token) sonList.get(1);
        SymbolTable curEnv = funcDef.getCurEnv();
        FuncSymbol funcSymbol = curEnv.funcGlobalLookup(funcIdent.getTokenContext());
        IRLabelSymbol funcLabelSymbol = iRLabelManager.allocSymbol();
        curEnv.setFuncRef(funcSymbol, funcLabelSymbol); // 保存函数引用信息
        ArrayList<IRSymbol> fParamSymbols = new ArrayList<>();
        IRFuncSymbol funcIRSymbol = new IRFuncSymbol(funcIdent.getTokenContext());
        funcIRSymbol.setEntry(funcLabelSymbol);
        funcIRSymbol.setfParamList(fParamSymbols);
        funcMap.put("main", funcIRSymbol);
        IRElem funcDefElem = new IRElem(IRElem.FUNC, funcIRSymbol, fParamSymbols);
        iRList.add(funcDefElem);
        IRElem funcLabelElem = new IRElem(IRElem.LABEL, funcLabelSymbol);
        iRList.add(funcLabelElem); // 插入函数定义标签
        IRSymbol funcRetSymbol = iRLabelManager.allocSymbol(); // 申请函数统一返回出口标签
        funcSymbol.setReturnSymbol(funcRetSymbol); // 设置返回标签
        SyntaxClass block = sonList.get(4);
        blockTrans(block, false);
        IRElem returnLabel = new IRElem(IRElem.LABEL, funcRetSymbol);
        iRList.add(returnLabel);
        IRElem mainReturnElem = new IRElem(IRElem.EXIT);
        iRList.add(mainReturnElem);
        return funcLabelSymbol;
    }

    public ArrayList<IRSymbol> fParamsTrans(SyntaxClass funcFParams) {
        ArrayList<SyntaxClass> sonList = funcFParams.getSonNodeList();
        ArrayList<IRSymbol> symbolList = new ArrayList<>();
        for (int i = 0; i < sonList.size(); i += 2) {
            symbolList.add(fParamTrans(sonList.get(i)));
        }
        return symbolList;
    }

    public IRSymbol fParamTrans(SyntaxClass fParam) {
        ArrayList<SyntaxClass> sonList = fParam.getSonNodeList();
        SymbolTable curEnv = fParam.getCurEnv();
        Token identToken = (Token) sonList.get(1);
        VarSymbol varSymbol = curEnv.varGlobalLookup(identToken.getTokenContext());
        IRLabelSymbol varLabel = iRLabelManager.allocSymbol();
        curEnv.setVarRef(varSymbol, varLabel);
        return varLabel;
    }

    public void blockTrans(SyntaxClass block, boolean disableSSA) {
        ArrayList<SyntaxClass> sonList = block.getSonNodeList();
        for (int i = 1; i < sonList.size() - 1; ++i) {
            SyntaxClass blockItem = sonList.get(i);
            SyntaxClass subClass = blockItem.getSonNodeList().get(0);
            if (subClass.getSyntaxType() == SyntaxClass.DECL) {
                declTrans(subClass, false); // 在Block里了，肯定不是Global
            } else {
                stmtTrans(subClass, disableSSA);
            }
        }
    }

    public void declTrans(SyntaxClass decl, boolean isGlobal) {
        SyntaxClass subDecl = decl.getSonNodeList().get(0);
        if (subDecl.getSyntaxType() == SyntaxClass.CONSTDECL) {
            constDeclTrans(subDecl);
        } else {
            varDeclTrans(subDecl, isGlobal);
        }
    }

    public void stmtTrans(SyntaxClass stmt, boolean disableSSA) {
        ArrayList<SyntaxClass> sonList = stmt.getSonNodeList();
        SymbolTable curEnv = stmt.getCurEnv();
        SyntaxClass firstItem = sonList.get(0);
        if (firstItem.getSyntaxType() == SyntaxClass.LVAL) {
            SyntaxClass objItem = sonList.get(2);
            IRSymbol lValSymbol = lValTrans(firstItem);
            if (objItem.getSyntaxType() == SyntaxClass.EXP) { // LVal = Exp
                IRSymbol expSymbol = expTrans(objItem);
                if (lValSymbol instanceof IRLabelSymbol) { // LVal单变量
                    IRElem assignElem;
                    if (disableSSA || ((IRLabelSymbol) lValSymbol).isGlobal()) { // 撤销SSA，直接使用已有符号
                        assignElem = new IRElem(IRElem.ASSIGN, lValSymbol, expSymbol);
                    } else { // 使用SSA，新建符号
                        lValSymbol = iRLabelManager.allocSymbol();
                        VarSymbol varSymbol = curEnv.varGlobalLookup(
                                ((Token) firstItem.getSonNodeList().get(0)).getTokenContext());
                        curEnv.setVarRef(varSymbol, (IRLabelSymbol) lValSymbol);
                        assignElem = new IRElem(IRElem.ASSIGN, lValSymbol, expSymbol);
                    }
                    iRList.add(assignElem);
                } else { // LVal是数组
                    IRSymbol base = ((IRArrSymbol) lValSymbol).getBaseAddr();
                    IRSymbol offset = ((IRArrSymbol) lValSymbol).getOffset();
                    IRElem storeElem = new IRElem(IRElem.STORE, expSymbol, base, offset);
                    iRList.add(storeElem);
                }
            } else { // LVal = getint()
                if (lValSymbol instanceof IRLabelSymbol) { // 单变量
                    IRElem getintElem;
                    if (disableSSA) {
                        getintElem = new IRElem(IRElem.GETINT, lValSymbol);
                    } else {
                        lValSymbol = iRLabelManager.allocSymbol();
                        VarSymbol varSymbol = curEnv.varGlobalLookup(
                                ((Token) firstItem.getSonNodeList().get(0)).getTokenContext());
                        curEnv.setVarRef(varSymbol, (IRLabelSymbol) lValSymbol);
                        getintElem = new IRElem(IRElem.GETINT, lValSymbol);
                    }
                    iRList.add(getintElem);
                } else { // 数组存取
                    IRSymbol base = ((IRArrSymbol) lValSymbol).getBaseAddr();
                    IRSymbol offset = ((IRArrSymbol) lValSymbol).getOffset();
                    IRSymbol getintRes = iRLabelManager.allocSymbol();
                    IRElem getintElem = new IRElem(IRElem.GETINT, getintRes);
                    iRList.add(getintElem);
                    IRElem storeElem = new IRElem(IRElem.STORE, getintRes, base, offset);
                    iRList.add(storeElem);
                }
            }
        } else if (firstItem.getSyntaxType() == SyntaxClass.EXP) { // Exp
            expTrans(firstItem);
        } else if (firstItem.getSyntaxType() == SyntaxClass.BLOCK) { // Block
            blockTrans(firstItem, true);
        } else { // 剩下的都是Token
            Token firstItemToken = (Token) firstItem;
            if (firstItemToken.getTokenType() == Token.IFTK) { // if (Cond) Stmt
                IRSymbol condSymbol = condTrans(sonList.get(2));
                IRSymbol elseSymbol = iRLabelManager.allocSymbol();
                IRElem condJudge = new IRElem(IRElem.BZ, elseSymbol, condSymbol);
                iRList.add(condJudge);
                SyntaxClass ifStmt = sonList.get(4);
                stmtTrans(ifStmt, true);
                if (sonList.size() != 5) { // 有else
                    IRSymbol endIfSymbol = iRLabelManager.allocSymbol();
                    IRElem endIfBr = new IRElem(IRElem.BR, endIfSymbol);
                    iRList.add(endIfBr);
                    IRElem elseStart = new IRElem(IRElem.LABEL, elseSymbol);
                    iRList.add(elseStart);
                    SyntaxClass elseStmt = sonList.get(6);
                    stmtTrans(elseStmt, true);
                    IRElem endIfElem = new IRElem(IRElem.LABEL, endIfSymbol);
                    iRList.add(endIfElem);
                } else { // 无else
                    IRElem endIfElem = new IRElem(IRElem.LABEL, elseSymbol);
                    iRList.add(endIfElem);
                }
            } else if (firstItemToken.getTokenType() == Token.WHILETK) { // while (cond) stmt
                /*IRSymbol startWhile = iRLabelManager.allocSymbol();
                IRSymbol endWhile = iRLabelManager.allocSymbol();
                SyntaxClass whileStmt = sonList.get(4);
                whileStmt.getCurEnv().setCycleStartEnd(startWhile, endWhile);
                IRElem startLabelElem = new IRElem(IRElem.LABEL, startWhile);
                iRList.add(startLabelElem);
                IRSymbol condRes = condTrans(sonList.get(2));
                IRElem condJudge = new IRElem(IRElem.BZ, endWhile, condRes);
                iRList.add(condJudge);
                stmtTrans(whileStmt, true);
                IRElem backToStartElem = new IRElem(IRElem.BR, startWhile);
                iRList.add(backToStartElem);
                IRElem endLabelElem = new IRElem(IRElem.LABEL, endWhile);
                iRList.add(endLabelElem);*/

                IRSymbol startWhile = iRLabelManager.allocSymbol();
                IRSymbol stmtStartWhile = iRLabelManager.allocSymbol();
                IRSymbol endWhile = iRLabelManager.allocSymbol();
                SyntaxClass whileStmt = sonList.get(4);
                whileStmt.getCurEnv().setCycleStartEnd(startWhile, endWhile);

                IRElem startLabelElem = new IRElem(IRElem.LABEL, startWhile);
                iRList.add(startLabelElem);

                IRSymbol condFirstRes = condTrans(sonList.get(2));
                IRElem condFirstJudge = new IRElem(IRElem.BZ, endWhile, condFirstRes);
                iRList.add(condFirstJudge);

                IRElem stmtStartLabelElem = new IRElem(IRElem.LABEL, stmtStartWhile);
                iRList.add(stmtStartLabelElem);

                stmtTrans(whileStmt, true);

                IRSymbol condSecondRes = condTrans(sonList.get(2));
                IRElem condSecondJudge = new IRElem(IRElem.BNZ, stmtStartWhile, condSecondRes);
                iRList.add(condSecondJudge);

                IRElem endLabelElem = new IRElem(IRElem.LABEL, endWhile);
                iRList.add(endLabelElem);
            } else if (firstItemToken.getTokenType() == Token.BREAKTK) { // break
                IRSymbol endWhile = curEnv.findCycleEnd();
                IRElem breakElem = new IRElem(IRElem.BR, endWhile);
                iRList.add(breakElem);
            } else if (firstItemToken.getTokenType() == Token.CONTINUETK) { // continue
                IRSymbol startWhile = curEnv.findCycleStart();
                IRElem continueElem = new IRElem(IRElem.BR, startWhile);
                iRList.add(continueElem);
            } else if (firstItemToken.getTokenType() == Token.RETURNTK) { // return
                FuncSymbol curFunc = curEnv.checkCurFunc();
                IRSymbol returnSymbol = curFunc.getReturnSymbol();
                if (sonList.size() != 2) { // return Exp ;
                    SyntaxClass exp = sonList.get(1);
                    IRSymbol expSymbol = expTrans(exp);
                    IRElem setRetValue = new IRElem(IRElem.SETRET, expSymbol);
                    iRList.add(setRetValue);
                }
                IRElem retElem = new IRElem(IRElem.BR, returnSymbol);
                iRList.add(retElem);
            } else if (firstItemToken.getTokenType() == Token.PRINTFTK) { // printf
                FormatStringToken formatStr = (FormatStringToken) sonList.get(2);
                ArrayList<IRSymbol> paramSymbolList = new ArrayList<>();
                for (int i = 4; i < sonList.size() - 2; i += 2) {
                    paramSymbolList.add(expTrans(sonList.get(i)));
                }
                ArrayList<String> rawStrList = formatStr.getRawStrList();
                int i;
                for (i = 0; i < formatStr.getFormatCharNum(); ++i) {
                    IRLabelSymbol rawStrLabel = iRLabelManager.allocSymbol();
                    formatStrMap.put(rawStrLabel, rawStrList.get(i));
                    IRElem printStr = new IRElem(IRElem.PRINTS, rawStrLabel);
                    iRList.add(printStr);
                    IRElem printInt = new IRElem(IRElem.PRINTI, paramSymbolList.get(i));
                    iRList.add(printInt);
                }
                IRLabelSymbol rawStrLabel = iRLabelManager.allocSymbol();
                formatStrMap.put(rawStrLabel, rawStrList.get(i));
                IRElem printStr = new IRElem(IRElem.PRINTS, rawStrLabel);
                iRList.add(printStr);
            }
        }
    }

    public StringBuilder outputIR(LinkedList<IRElem> iRList) {
        StringBuilder outStr = new StringBuilder(".data\n");
        for (VarSymbol constVarSymbol : constantArrMap.keySet()) {
            IRSymbol constSymbol = constantArrMap.get(constVarSymbol);
            outStr.append(".align 2\n");
            outStr.append(constSymbol.toString()).append(":\n.word ");
            ArrayList<Integer> constArr = constVarSymbol.constGetAllValue();
            int i;
            for (i = 0; i < constArr.size() - 1; ++i) {
                outStr.append(constArr.get(i)).append(", ");
            }
            outStr.append(constArr.get(i)).append("\n");
        }
        for (VarSymbol globalVarSymbol : globalArrMap.keySet()) {
            IRSymbol constSymbol = globalArrMap.get(globalVarSymbol);
            outStr.append(".align 2\n");
            outStr.append(constSymbol.toString()).append(":\n.word ");
            ArrayList<Integer> constArr = globalVarSymbol.constGetAllValue();
            int i;
            for (i = 0; i < constArr.size() - 1; ++i) {
                outStr.append(constArr.get(i)).append(", ");
            }
            outStr.append(constArr.get(i)).append("\n");
        }
        for (IRSymbol strSymbol : formatStrMap.keySet()) {
            outStr.append(".align 2\n");
            outStr.append(strSymbol.toString()).append(":\n.asciiz ");
            String rawStr = formatStrMap.get(strSymbol);
            outStr.append("\"").append(rawStr).append("\"\n");
        }

        outStr.append("\n.text\n");
        for (IRElem irElem : iRList) {
            outStr.append(irElem.toString()).append("\n");
        }

        return outStr;
    }
}
