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
import SyntaxClasses.FormatStringToken;
import SyntaxClasses.SyntaxClass;
import SyntaxClasses.Token;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

public class IRTranslater {
    private SyntaxClass compUnit;
    private HashMap<VarSymbol, Integer> constantArrMap; // 常量数组无法消干净
    private HashMap<VarSymbol, Integer> globalArrMap; // 全局数组
    private ArrayList<FormatStringToken> formatStrArr; // 格式字符串作常量存
    private LinkedList<IRElem> iRList;
    int globalVarID;
    IRLabelManager iRLabelManager;

    public IRTranslater(SyntaxClass compUnit) {
        this.compUnit = compUnit;
        constantArrMap = new HashMap<>();
        formatStrArr = new ArrayList<>();
        iRList = new LinkedList<>();
        globalVarID = 0;
        iRLabelManager = IRLabelManager.getIRLabelManager();
    }

    public void constDeclTrans(SyntaxClass constDecl) {
        ArrayList<SyntaxClass> sonList = constDecl.getSonNodeList();
        for (int i = 2; i < sonList.size() - 1; i += 2) {
            constDefTrans(sonList.get(i));
        }
    }

    public void constDefTrans(SyntaxClass constDef) {
        ArrayList<SyntaxClass> sonList = constDef.getSonNodeList();
        Token ident = (Token) sonList.get(0);
        SymbolTable curEnv = constDef.getCurEnv();
        VarSymbol constSymbol = curEnv.varGlobalLookup(ident.getTokenContext());
        if (constSymbol.getDimType() != 0) {
            int constID = constantArrMap.size();
            constantArrMap.put(constSymbol, constID);
            IRLabelSymbol constIRLabel = iRLabelManager.allocSymbol();
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
        if (varSymbol.getDimType() == 0) { // 单变量，仅考虑是否初始化
            if (sonList.size() > 1) { // 有初始化
                IRSymbol initValSymbol = singleInitValTrans(sonList.get(2)); // 初始值算出来赋值到一个临时变量上
                IRLabelSymbol varIRLabel = iRLabelManager.allocSymbol(); // 给新定义的变量申请符号
                curEnv.setVarRef(varSymbol, varIRLabel); // 设置引用
                IRElem varInitIRElem = new IRElem(IRElem.ASSIGN, varIRLabel, initValSymbol); // 赋值
                iRList.add(varInitIRElem);
            } else if (isGlobal) { // 全局变量，无初始化，需置0
                IRLabelSymbol varIRSymbol = iRLabelManager.allocSymbol(); // 申请符号
                curEnv.setVarRef(varSymbol, varIRSymbol);
                IRElem globalInitElem = new IRElem(IRElem.ASSIGN, varIRSymbol, IRImmSymbol.ZERO);
                iRList.add(globalInitElem);
            }
        } else { // 定义数组时申请空间
            int memSize = 0;
            if (varSymbol.getDimType() == 1) { // 一维
                memSize = varSymbol.getDimLength(0);
            } else { // 二维
                memSize = varSymbol.getDimLength(1) * varSymbol.getDimLength(0);
            }
            IRLabelSymbol irLabel = iRLabelManager.allocSymbol(); // 申请中间变量符号
            curEnv.setVarRef(varSymbol, irLabel); // 设置当前变量引用关系
            if (isGlobal) { // 全局数组直接设置引用关系，空间已经在数据区分配好
                globalArrMap.put(varSymbol, irLabel.getId());
                // TODO: 思考一下globalArrMap是应该VarSymbol -> int 还是反过来会更好？
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
            } else if (varSymbol.getDimType() == 2 && sonList.size() > 4 && !isGlobal) { // 局部二维数组初始化
                SyntaxClass arrInitVal = sonList.get(9);
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
            } else {
                exprIR = new IRElem(IRElem.DIV, resSymbol, symbol0, symbol1);
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
            Token mathSymbol = (Token) sonList.get(0);
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
        LinkedList<IRSymbol> paramLinkedList = new LinkedList<>();
        if (funcRParams.getSyntaxType() == SyntaxClass.FUNCRPARAMS) { // 从右往左计算各参数
            ArrayList<SyntaxClass> realParamList = funcRParams.getSonNodeList();
            for (int i = realParamList.size() - 1; i >= 0; i -= 2) {
                IRSymbol paramSymbol = expTrans(realParamList.get(i));
                paramLinkedList.addFirst(paramSymbol);
            }
        }
        ArrayList<IRSymbol> paramList = new ArrayList<>(paramLinkedList);
        IRSymbol funcIRSymbol = new IRFuncSymbol(funcIdent.getTokenContext());
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
            return new IRImmSymbol(subExp.getConstValue());
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
            IRSymbol lValVarSymbol = curEnv.getLastVarRef(identSymbol);
            if (lValVarSymbol == null) {
                lValVarSymbol = iRLabelManager.allocSymbol();
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
            return new IRArrSymbol(baseSymbol, dim1Symbol);
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
                offset = new IRImmSymbol(((IRImmSymbol) dim1OffSymbol).getValue() +
                        ((IRImmSymbol) dim0Symbol).getValue());
            } else {
                offset = iRLabelManager.allocSymbol();
                IRElem offsetCal = new IRElem(IRElem.ADD, offset, dim1OffSymbol, dim0Symbol);
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
        IRSymbol orResSymbol = lOrExpTrans(sonList.get(0));
        if (orResSymbol instanceof IRImmSymbol) { // 第一部分是常数
            if (((IRImmSymbol) orResSymbol).getValue() == 0) {
                return lAndExpTrans(sonList.get(2));
            } else {
                return orResSymbol;
            }
        }
        IRSymbol andResSymbol = lAndExpTrans(sonList.get(2));
        if (andResSymbol instanceof IRImmSymbol) { // 第二部分是常数
            if (((IRImmSymbol) andResSymbol).getValue() == 0) {
                return orResSymbol;
            } else {
                return andResSymbol;
            }
        }
        // 都不是常数
        IRSymbol resSymbol = iRLabelManager.allocSymbol();
        IRElem lOrElem = new IRElem(IRElem.OR, resSymbol, orResSymbol, andResSymbol);
        iRList.add(lOrElem);
        return resSymbol;
    }

    public IRSymbol lAndExpTrans(SyntaxClass lAndExp) {
        ArrayList<SyntaxClass> sonList = lAndExp.getSonNodeList();
        if (sonList.size() == 1) { // LAnd -> Eq
            return eqExpTrans(sonList.get(0));
        }
        // LAnd -> LAnd && Eq
        IRSymbol andResSymbol = lAndExpTrans(sonList.get(0));
        if (andResSymbol instanceof IRImmSymbol) { // 第一部分是常数
            if (((IRImmSymbol) andResSymbol).getValue() != 0) {
                return eqExpTrans(sonList.get(2));
            } else {
                return andResSymbol;
            }
        }
        IRSymbol eqResSymbol = eqExpTrans(sonList.get(2));
        if (eqResSymbol instanceof IRImmSymbol) { // 第二部分是常数
            if (((IRImmSymbol) eqResSymbol).getValue() != 0) {
                return andResSymbol;
            } else {
                return eqResSymbol;
            }
        }
        // 都不是常数
        IRSymbol resSymbol = iRLabelManager.allocSymbol();
        IRElem lAndElem = new IRElem(IRElem.AND, resSymbol, andResSymbol, eqResSymbol);
        iRList.add(lAndElem);
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
            if (addSymbol instanceof IRImmSymbol) { // Add为常数，则直接判0返回
                if (((IRImmSymbol) addSymbol).getValue() == 0) {
                    return addSymbol;
                } else {
                    return new IRImmSymbol(1);
                }
            }
            // Add非常数，则执行判0指令
            IRSymbol resSymbol = iRLabelManager.allocSymbol();
            IRElem relElem = new IRElem(IRElem.NEQ, resSymbol, addSymbol, IRImmSymbol.ZERO); // 用 NEQ 0 判断是否非0
            iRList.add(relElem);
            return resSymbol;
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
            eqElem = new IRElem(IRElem.GRE, resSymbol, relResSymbol, addResSymbol);
        } else if (((Token) sonList.get(1)).getTokenType() == Token.LEQ) { // <=
            eqElem = new IRElem(IRElem.LEQ, resSymbol, relResSymbol, addResSymbol);
        } else { // >=
            eqElem = new IRElem(IRElem.GEQ, resSymbol, relResSymbol, addResSymbol);
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
        IRElem funcDefElem = new IRElem(IRElem.LABEL, funcLabelSymbol);
        iRList.add(funcDefElem); // 插入函数定义标签
        IRSymbol funcRetSymbol = iRLabelManager.allocSymbol(); // 申请函数统一返回出口标签
        funcSymbol.setReturnSymbol(funcRetSymbol); // 设置返回标签
        ArrayList<IRSymbol> fParamSymbols;
        SyntaxClass block;
        if (sonList.size() == 6) { // 有形参
            fParamSymbols = fParamsTrans(sonList.get(3));
            block = sonList.get(5);
        } else {
            block = sonList.get(4);
        }
        blockTrans(block, false);
        IRElem returnLabel = new IRElem(IRElem.LABEL, funcRetSymbol);
        iRList.add(returnLabel);
        IRElem voidReturnElem = new IRElem(IRElem.RET);
        iRList.add(voidReturnElem);
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
                    if (disableSSA) { // 撤销SSA，直接使用已有符号
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
            blockTrans(firstItem, disableSSA);
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
                IRSymbol startWhile = iRLabelManager.allocSymbol();
                IRSymbol endWhile = iRLabelManager.allocSymbol();
                curEnv.setCycleStartEnd(startWhile, endWhile);
                IRElem startLabelElem = new IRElem(IRElem.LABEL, startWhile);
                iRList.add(startLabelElem);
                IRSymbol condRes = condTrans(sonList.get(2));
                IRElem condJudge = new IRElem(IRElem.BZ, endWhile, condRes);
                iRList.add(condJudge);
                stmtTrans(sonList.get(4), true);
                IRElem endLabelElem = new IRElem(IRElem.LABEL, endWhile);
                iRList.add(endLabelElem);
            } else if (firstItemToken.getTokenType() == Token.BREAKTK) { // break
                IRSymbol endWhile = curEnv.getCycleEnd();
                IRElem breakElem = new IRElem(IRElem.BR, endWhile);
                iRList.add(breakElem);
            } else if (firstItemToken.getTokenType() == Token.CONTINUETK) { // continue
                IRSymbol startWhile = curEnv.getCycleStart();
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
                // TODO: printf
            }
        }
    }
}
