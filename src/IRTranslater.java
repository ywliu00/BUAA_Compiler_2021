import IR.IRElem;
import IR.IRImmSymbol;
import IR.IRLabelManager;
import IR.IRLabelSymbol;
import IR.IRSymbol;
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
        }
    }

    public void varDeclTrans(SyntaxClass varDecl) {
        ArrayList<SyntaxClass> sonList = varDecl.getSonNodeList();
        for (int i = 1; i < sonList.size() - 1; i += 2) {
            varDefTrans(sonList.get(i));
        }
    }

    public void varDefTrans(SyntaxClass varDef) {
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
            IRElem identIRElem = new IRElem(IRElem.ALLOCA, irLabel, new IRImmSymbol(memSize * 4));
            iRList.add(identIRElem);

            if (varSymbol.getDimType() == 1 && sonList.size() > 4) { // 一维数组初始化
                SyntaxClass arrInitVal = sonList.get(5);
                ArrayList<SyntaxClass> initValList = arrInitVal.getSonNodeList();
                for (int i = 0; i < varSymbol.getDimLength(0); ++i) {
                    IRSymbol initSymbol = singleInitValTrans(initValList.get(2 * i + 1)); // 计算要赋的值
                    IRElem initElem = new IRElem(IRElem.STORE, initSymbol, irLabel,
                            new IRImmSymbol(i * 4));
                    iRList.add(initElem);
                }
            } else if (varSymbol.getDimType() == 2 && sonList.size() > 4) { // 二维数组初始化
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

    public IRSymbol singleInitValTrans(SyntaxClass initVal) {
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
            if (mathSymbol.getTokenType() == Token.MINU) {
                resSymbol = iRLabelManager.allocSymbol();
                IRElem exprIR = new IRElem(IRElem.MINU, resSymbol,
                        IRImmSymbol.ZERO, subExpSymbol);
                iRList.add(exprIR);
            } else if (mathSymbol.getTokenType() == Token.PLUS) {
                resSymbol = subExpSymbol;
            } else {
                resSymbol = iRLabelManager.allocSymbol();
                IRElem exprIR = new IRElem(IRElem.EQL, resSymbol,
                        IRImmSymbol.ZERO, subExpSymbol);
                iRList.add(exprIR);
            }
            return resSymbol;
        } else {
            // TODO: 函数调用
        }
    }

    public IRSymbol primaryExpTrans(SyntaxClass primaryExp) {
        if (primaryExp.isCalculated()) {
            return new IRImmSymbol(primaryExp.getConstValue());
        }
        SyntaxClass subExp = primaryExp.getSonNodeList().get(0);
        if (subExp.getSyntaxType() == SyntaxClass.NUMBER) {
            return new IRImmSymbol(subExp.getConstValue());
        } else if (subExp.getSyntaxType() == SyntaxClass.LVAL) {
            // TODO: LVAL
        } else {
            subExp = primaryExp.getSonNodeList().get(1);
            return expTrans(subExp);
        }
    }
}
