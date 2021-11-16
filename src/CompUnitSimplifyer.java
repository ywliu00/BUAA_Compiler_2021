import Symbols.SymbolTable;
import Symbols.VarSymbol;
import SyntaxClasses.ConstIntToken;
import SyntaxClasses.SyntaxClass;
import SyntaxClasses.Token;

import java.util.ArrayList;
import java.util.LinkedList;

public class CompUnitSimplifyer {
    private SyntaxClass compUnit;

    public CompUnitSimplifyer(SyntaxClass compUnit) {
        this.compUnit = compUnit;
    }

    public static void compUnitSimplify(SyntaxClass compUnit) {
        ArrayList<SyntaxClass> sonList = compUnit.getSonNodeList();
        for (SyntaxClass syntaxClass : sonList) {
            if (syntaxClass.getSyntaxType() == SyntaxClass.DECL) {
                globalDeclTableFill(syntaxClass);
            } else {
                constCal(syntaxClass);
            }
        }
    }

    public static void constCal(SyntaxClass curUnit) { // 符号表常量填充+常量折叠
        ArrayList<SyntaxClass> sonList = curUnit.getSonNodeList();
        for (SyntaxClass syntaxClass : sonList) {
            /*if (syntaxClass.getSyntaxType() == SyntaxClass.DECL) {
                declTableFill(syntaxClass);
            }*/
            if (syntaxClass.getSyntaxType() == SyntaxClass.CONSTDEF) {
                constDefTableFill(syntaxClass);
            } else if (syntaxClass.getSyntaxType() == SyntaxClass.VARDEF) {
                varDefTableFill(syntaxClass);
            } else if (syntaxClass.getSyntaxType() == SyntaxClass.CONSTEXP) {
                constExpCal(syntaxClass);
            } else if (syntaxClass.getSyntaxType() == SyntaxClass.FUNCFPARAM) {
                fParamTableFill(syntaxClass);
            } else if (syntaxClass.getSyntaxType() == SyntaxClass.EXP) {
                expCal(syntaxClass);
            } else if (syntaxClass.getSyntaxType() == SyntaxClass.ADDEXP) {
                addCal(syntaxClass);
            } else {
                constCal(syntaxClass);
            }
        }
    }

    public static void globalDeclTableFill(SyntaxClass decl) {
        SyntaxClass subDecl = decl.getSonNodeList().get(0);
        ArrayList<SyntaxClass> sonList = subDecl.getSonNodeList();
        int sonListLen = sonList.size();
        if (subDecl.getSyntaxType() == SyntaxClass.CONSTDECL) {
            for (int i = 2; i < sonListLen - 1; i += 2) { // 最后有分号，长度要减1
                constDefTableFill(sonList.get(i));
            }
        } else { // 全局变量
            for (int i = 1; i < sonListLen - 1; i += 2) { // 最后有分号，长度要减1
                globalVarDefTableFill(sonList.get(i));
            }
        }
    }

    public static void globalVarDefTableFill(SyntaxClass varDef) {
        ArrayList<SyntaxClass> sonList = varDef.getSonNodeList();
        Token ident = (Token) sonList.get(0);
        VarSymbol identSymbol = (VarSymbol) varDef.getCurEnv().
                globalLookup(ident.getTokenContext(), 0);
        if (identSymbol.getDimType() == 0) { // 单独常数
            if (sonList.size() == 1) { // 默认为0
                identSymbol.set0DimVarValue(0);
            } else {
                SyntaxClass constInitVal = sonList.get(2);
                SyntaxClass constExp = constInitVal.getSonNodeList().get(0);
                constExpCal(constExp);
                identSymbol.set0DimVarValue(constExp.getConstValue());
            }
        } else { // 数组
            SyntaxClass dim1LengthExp = sonList.get(2);
            constExpCal(dim1LengthExp);
            int dim1Length = dim1LengthExp.getConstValue();
            if (identSymbol.getDimType() == 1) { // 一维
                identSymbol.setDimLengthByDim(0, dim1Length); // 设置长度
                ArrayList<Integer> constInitValArr;
                if (sonList.size() == 4) { // 无初始化，全部置0
                    constInitValArr = new ArrayList<>();
                    for (int i = 0; i < dim1Length; ++i) {
                        constInitValArr.add(0);
                    }
                } else { // 有初始化
                    SyntaxClass constInitVal = sonList.get(5);
                    constInitValArr = oneDimConstInitValArr(constInitVal);
                }
                identSymbol.set1DimVarValue(constInitValArr, dim1Length);
            } else { // 二维
                SyntaxClass dim0LengthExp = sonList.get(5);
                constExpCal(dim0LengthExp);
                int dim0Length = dim0LengthExp.getConstValue();
                identSymbol.setDimLengthByDim(1, dim1Length); // 设置长度
                identSymbol.setDimLengthByDim(0, dim0Length);
                ArrayList<ArrayList<Integer>> constInitValArr = new ArrayList<>();
                if (sonList.size() == 7) { // 无初始化
                    for (int i = 0; i < dim1Length; ++i) {
                        ArrayList<Integer> globalInitArr = new ArrayList<>();
                        for (int j = 0; j < dim0Length; ++j) {
                            globalInitArr.add(0);
                        }
                        constInitValArr.add(globalInitArr);
                    }
                } else {
                    SyntaxClass constInitVal = sonList.get(8);
                    ArrayList<SyntaxClass> initSonList = constInitVal.getSonNodeList();
                    for (int i = 1; i < initSonList.size() - 1; i += 2) {
                        constInitValArr.add(oneDimConstInitValArr(initSonList.get(i)));
                    }
                }
                identSymbol.set2DimVarValue(constInitValArr, dim1Length, dim0Length);
            }
        }
    }

    public static void constDefTableFill(SyntaxClass constDef) {
        ArrayList<SyntaxClass> sonList = constDef.getSonNodeList();
        Token ident = (Token) sonList.get(0);
        VarSymbol identSymbol = (VarSymbol) constDef.getCurEnv().
                globalLookup(ident.getTokenContext(), 0);
        if (identSymbol.getDimType() == 0) { // 单独常数
            SyntaxClass constInitVal = sonList.get(2);
            SyntaxClass constExp = constInitVal.getSonNodeList().get(0);
            constExpCal(constExp);
            identSymbol.set0DimConstValue(constExp.getConstValue());
        } else { // 数组
            SyntaxClass dim1LengthExp = sonList.get(2);
            constExpCal(dim1LengthExp);
            int dim1Length = dim1LengthExp.getConstValue();
            if (identSymbol.getDimType() == 1) { // 一维
                SyntaxClass constInitVal = sonList.get(5);
                identSymbol.setDimLengthByDim(0, dim1Length); // 设置长度
                ArrayList<Integer> constInitValArr = oneDimConstInitValArr(constInitVal);
                identSymbol.set1DimConstValue(constInitValArr, dim1Length);
            } else { // 二维
                SyntaxClass dim0LengthExp = sonList.get(5);
                constExpCal(dim0LengthExp);
                int dim0Length = dim0LengthExp.getConstValue();
                identSymbol.setDimLengthByDim(1, dim1Length); // 设置长度
                identSymbol.setDimLengthByDim(0, dim0Length);
                SyntaxClass constInitVal = sonList.get(8);
                ArrayList<ArrayList<Integer>> constInitValArr = new ArrayList<>();
                ArrayList<SyntaxClass> initSonList = constInitVal.getSonNodeList();
                for (int i = 1; i < initSonList.size() - 1; i += 2) {
                    constInitValArr.add(oneDimConstInitValArr(initSonList.get(i)));
                }
                identSymbol.set2DimConstValue(constInitValArr, dim1Length, dim0Length);
            }
        }
    }

    public static ArrayList<Integer> oneDimConstInitValArr(SyntaxClass constInitVal) {
        ArrayList<SyntaxClass> sonList = constInitVal.getSonNodeList();
        ArrayList<Integer> initValArr = new ArrayList<>();
        for (int i = 1; i < sonList.size() - 1; i += 2) {
            SyntaxClass sonInitVal = sonList.get(i);
            SyntaxClass sonConstExp = sonInitVal.getSonNodeList().get(0);
            constExpCal(sonConstExp);
            initValArr.add(sonConstExp.getConstValue());
        }
        return initValArr;
    }

    public static void varDefTableFill(SyntaxClass varDef) {
        ArrayList<SyntaxClass> sonList = varDef.getSonNodeList();
        Token ident = (Token) sonList.get(0);
        VarSymbol identSymbol = (VarSymbol) varDef.getCurEnv().
                globalLookup(ident.getTokenContext(), 0);
        if (identSymbol.getDimType() > 0) {
            SyntaxClass dim1LengthExp = sonList.get(2);
            constExpCal(dim1LengthExp);
            int dim1Length = dim1LengthExp.getConstValue();
            if (identSymbol.getDimType() == 1) { // 一维
                identSymbol.setDimLengthByDim(0, dim1Length); // 设置长度
            } else { // 二维
                SyntaxClass dim0LengthExp = sonList.get(5);
                constExpCal(dim0LengthExp);
                int dim0Length = dim0LengthExp.getConstValue();
                identSymbol.setDimLengthByDim(0, dim0Length);
                identSymbol.setDimLengthByDim(1, dim1Length);
            }
        }
        for (SyntaxClass subClass : sonList) {
            if (subClass.getSyntaxType() == SyntaxClass.INITVAL) {
                constCal(subClass);
            }
        }
    }

    // 形参长度设置
    public static void fParamTableFill(SyntaxClass funcFParam) {
        ArrayList<SyntaxClass> sonList = funcFParam.getSonNodeList();
        Token ident = (Token) sonList.get(1);
        VarSymbol identSymbol = (VarSymbol) funcFParam.getCurEnv().
                globalLookup(ident.getTokenContext(), 0);
        if (identSymbol.getDimType() == 2) {
            SyntaxClass dim0LengthExp = sonList.get(5); // BType Ident [][Exp]
            constExpCal(dim0LengthExp);
            int dim0Length = dim0LengthExp.getConstValue();
            identSymbol.setDimLengthByDim(0, dim0Length);
        }
    }

    // Exp，不一定是Const的
    public static void expCal(SyntaxClass exp) {
        if (exp.isCalculated()) {
            return;
        }
        ArrayList<SyntaxClass> expSonNodeList = exp.getSonNodeList();
        SyntaxClass sonAddExp = expSonNodeList.get(0);
        addCal(sonAddExp);
        if (sonAddExp.isCalculated()) {
            exp.setConstValue(sonAddExp.getConstValue());
            exp.setCalculated(true);
        }
    }

    public static void addCal(SyntaxClass addExp) {
        if (addExp.isCalculated()) {
            return;
        }
        ArrayList<SyntaxClass> expSonNodeList = addExp.getSonNodeList();
        if (expSonNodeList.size() == 1) {
            SyntaxClass sonMulExp = expSonNodeList.get(0);
            if (sonMulExp.getSyntaxType() == SyntaxClass.MULEXP) {
                mulCal(sonMulExp);
            } else {
                addCal(sonMulExp);
            }
            if (sonMulExp.isCalculated()) {
                addExp.setConstValue(sonMulExp.getConstValue());
                addExp.setCalculated(true);
            }
        } else {
            SyntaxClass sonAddExp = expSonNodeList.get(0);
            addCal(sonAddExp);
            SyntaxClass sonMulExp = expSonNodeList.get(2);
            if (sonMulExp.getSyntaxType() == SyntaxClass.MULEXP) {
                mulCal(sonMulExp);
            } else {
                addCal(sonMulExp);
            }
            if (sonAddExp.isCalculated() && sonMulExp.isCalculated()) {
                Token symbolToken = (Token) expSonNodeList.get(1);
                if (symbolToken.getTokenType() == Token.PLUS) {
                    addExp.setConstValue(sonAddExp.getConstValue() + sonMulExp.getConstValue());
                } else {
                    addExp.setConstValue(sonAddExp.getConstValue() - sonMulExp.getConstValue());
                }
                addExp.setCalculated(true);
            }
        }
    }

    public static void mulCal(SyntaxClass mulExp) {
        if (mulExp.isCalculated()) {
            return;
        }
        ArrayList<SyntaxClass> expSonNodeList = mulExp.getSonNodeList();
        if (expSonNodeList.size() == 1) {
            SyntaxClass sonUnaryExp = expSonNodeList.get(0);
            unaryCal(sonUnaryExp);
            if (sonUnaryExp.isCalculated()) {
                mulExp.setConstValue(sonUnaryExp.getConstValue());
                mulExp.setCalculated(true);
            }
        } else {
            SyntaxClass sonMulExp = expSonNodeList.get(0);
            mulCal(sonMulExp);
            SyntaxClass sonUnaryExp = expSonNodeList.get(2);
            unaryCal(sonUnaryExp);
            if (sonMulExp.isCalculated() && sonUnaryExp.isCalculated()) {
                Token symbolToken = (Token) expSonNodeList.get(1);
                if (symbolToken.getTokenType() == Token.MULT) {
                    mulExp.setConstValue(sonMulExp.getConstValue() * sonUnaryExp.getConstValue());
                } else if (symbolToken.getTokenType() == Token.DIV) {
                    mulExp.setConstValue(sonMulExp.getConstValue() / sonUnaryExp.getConstValue());
                } else {
                    mulExp.setConstValue(sonMulExp.getConstValue() % sonUnaryExp.getConstValue());
                }
                mulExp.setCalculated(true);
            }
        }
    }

    public static void unaryCal(SyntaxClass unaryExp) {
        // 需要考虑PrimaryExp和 UnaryOp UnaryExp和函数
        if (unaryExp.isCalculated()) {
            return;
        }
        ArrayList<SyntaxClass> expSonNodeList = unaryExp.getSonNodeList();
        SyntaxClass sonExp = expSonNodeList.get(0);
        if (sonExp.getSyntaxType() == SyntaxClass.PRIMARYEXP) {
            primaryCal(sonExp);
            if (sonExp.isCalculated()) {
                unaryExp.setConstValue(sonExp.getConstValue());
                unaryExp.setCalculated(true);
            }
        } else if (sonExp.getSyntaxType() == SyntaxClass.UNARYOP) {
            SyntaxClass unaryOp = sonExp;
            sonExp = expSonNodeList.get(1);
            unaryCal(sonExp);
            if (sonExp.isCalculated()) {
                Token unaryOpToken = (Token) unaryOp.getSonNodeList().get(0);
                int constValue;
                if (unaryOpToken.getTokenType() == Token.MINU) {
                    constValue = -sonExp.getConstValue();
                } else if (unaryOpToken.getTokenType() == Token.PLUS) {
                    constValue = sonExp.getConstValue();
                } else {
                    constValue = (sonExp.getConstValue() == 0) ? 1 : 0;
                }
                unaryExp.setConstValue(constValue);
                unaryExp.setCalculated(true);
            }
        }
    }

    public static void primaryCal(SyntaxClass primaryExp) {
        if (primaryExp.isCalculated()) {
            return;
        }
        ArrayList<SyntaxClass> expSonNodeList = primaryExp.getSonNodeList();
        if (expSonNodeList.size() == 1) {
            SyntaxClass sonExp = expSonNodeList.get(0);
            if (sonExp.getSyntaxType() == SyntaxClass.NUMBER) {
                numberCal(sonExp);
                primaryExp.setConstValue(sonExp.getConstValue());
                primaryExp.setCalculated(true);
            } else { // LVal
                lValCal(sonExp);
                if (sonExp.isCalculated()) {
                    primaryExp.setConstValue(sonExp.getConstValue());
                    primaryExp.setCalculated(true);
                }
            }
            primaryExp.setConstValue(sonExp.getConstValue());
        } else { // (Exp)
            SyntaxClass sonExp = expSonNodeList.get(1);
            expCal(sonExp);
            if (sonExp.isCalculated()) {
                primaryExp.setConstValue(sonExp.getConstValue());
                primaryExp.setCalculated(true);
            }
        }
    }

    public static void lValCal(SyntaxClass lVal) {
        if (lVal.isCalculated()) {
            return;
        }
        ArrayList<SyntaxClass> expSonNodeList = lVal.getSonNodeList();
        Token ident = (Token) expSonNodeList.get(0);
        SymbolTable lValEnv = lVal.getCurEnv();
        VarSymbol identSymbol = (VarSymbol) lValEnv.globalLookup(ident.getTokenContext(), 0);
        if (!identSymbol.hasConstValue()) { // 本层后面覆盖了该LVal的定义，此处应向前找上一次定义
            identSymbol = (VarSymbol) lValEnv.varLocalLookup(ident.getTokenContext(), lVal.getCurVarListPos());
        }
        if (identSymbol == null || !identSymbol.hasConstValue()) { // 还没找到，说明ident不是常量
            return;
        }
        // 以下处理ident是常量的情况
        if (expSonNodeList.size() == 1) {
            lVal.setConstValue(identSymbol.constGetValue());
        } else {
            SyntaxClass exp1 = expSonNodeList.get(2);
            expCal(exp1);
            if (!exp1.isCalculated()) { // 维度1不是常量，放弃
                return;
            }
            int dim1 = exp1.getConstValue();
            if (expSonNodeList.size() == 4) {// 一维常量数组
                lVal.setConstValue(identSymbol.constGetValue(dim1));
            } else { // 二维常量数组
                SyntaxClass exp2 = expSonNodeList.get(5);
                expCal(exp2);
                if (!exp2.isCalculated()) { // 维度2不是常量，放弃
                    return;
                }
                int dim0 = exp2.getConstValue();
                lVal.setConstValue(identSymbol.constGetValue(dim1, dim0));
            }
        }
        lVal.setCalculated(true);
    }

    // 已经知道这是一个ConstExp
    public static void constExpCal(SyntaxClass constExp) {
        if (constExp.isCalculated()) {
            return;
        }
        ArrayList<SyntaxClass> expSonNodeList = constExp.getSonNodeList();
        SyntaxClass sonAddExp = expSonNodeList.get(0);
        constAddCal(sonAddExp);
        constExp.setConstValue(sonAddExp.getConstValue());
        constExp.setCalculated(true);
    }

    public static void constAddCal(SyntaxClass constAddExp) {
        if (constAddExp.isCalculated()) {
            return;
        }
        ArrayList<SyntaxClass> expSonNodeList = constAddExp.getSonNodeList();
        if (expSonNodeList.size() == 1) {
            SyntaxClass sonMulExp = expSonNodeList.get(0);
            constMulCal(sonMulExp);
            constAddExp.setConstValue(sonMulExp.getConstValue());
        } else {
            SyntaxClass sonAddExp = expSonNodeList.get(0);
            constAddCal(sonAddExp);
            SyntaxClass sonMulExp = expSonNodeList.get(2);
            constMulCal(sonMulExp);
            Token symbolToken = (Token) expSonNodeList.get(1);
            if (symbolToken.getTokenType() == Token.PLUS) {
                constAddExp.setConstValue(sonAddExp.getConstValue() + sonMulExp.getConstValue());
            } else {
                constAddExp.setConstValue(sonAddExp.getConstValue() - sonMulExp.getConstValue());
            }
        }
        constAddExp.setCalculated(true);
    }

    public static void constMulCal(SyntaxClass constMulExp) {
        if (constMulExp.isCalculated()) {
            return;
        }
        ArrayList<SyntaxClass> expSonNodeList = constMulExp.getSonNodeList();
        if (expSonNodeList.size() == 1) {
            SyntaxClass sonUnaryExp = expSonNodeList.get(0);
            constUnaryCal(sonUnaryExp);
            constMulExp.setConstValue(sonUnaryExp.getConstValue());
        } else {
            SyntaxClass sonMulExp = expSonNodeList.get(0);
            constMulCal(sonMulExp);
            SyntaxClass sonUnaryExp = expSonNodeList.get(2);
            constUnaryCal(sonUnaryExp);
            Token symbolToken = (Token) expSonNodeList.get(1);
            if (symbolToken.getTokenType() == Token.MULT) {
                constMulExp.setConstValue(sonMulExp.getConstValue() * sonUnaryExp.getConstValue());
            } else if (symbolToken.getTokenType() == Token.DIV) {
                constMulExp.setConstValue(sonMulExp.getConstValue() / sonUnaryExp.getConstValue());
            } else {
                constMulExp.setConstValue(sonMulExp.getConstValue() % sonUnaryExp.getConstValue());
            }
        }
        constMulExp.setCalculated(true);
    }

    public static void constUnaryCal(SyntaxClass constUnaryExp) {
        // 常数，右边不会是函数，只需要考虑PrimaryExp和 UnaryOp UnaryExp
        if (constUnaryExp.isCalculated()) {
            return;
        }
        ArrayList<SyntaxClass> expSonNodeList = constUnaryExp.getSonNodeList();
        SyntaxClass sonExp = expSonNodeList.get(0);
        if (sonExp.getSyntaxType() == SyntaxClass.PRIMARYEXP) {
            constPrimaryCal(sonExp);
            constUnaryExp.setConstValue(sonExp.getConstValue());
        } else {
            SyntaxClass unaryOp = sonExp;
            sonExp = expSonNodeList.get(1);
            constUnaryCal(sonExp);
            Token unaryOpToken = (Token) unaryOp.getSonNodeList().get(0);
            int constValue;
            if (unaryOpToken.getTokenType() == Token.MINU) {
                constValue = -sonExp.getConstValue();
            } else if (unaryOpToken.getTokenType() == Token.PLUS) {
                constValue = sonExp.getConstValue();
            } else {
                constValue = (sonExp.getConstValue() == 0) ? 1 : 0;
            }
            constUnaryExp.setConstValue(constValue);
        }
        constUnaryExp.setCalculated(true);
    }

    public static void constPrimaryCal(SyntaxClass constPrimaryExp) {
        if (constPrimaryExp.isCalculated()) {
            return;
        }
        ArrayList<SyntaxClass> expSonNodeList = constPrimaryExp.getSonNodeList();
        if (expSonNodeList.size() == 1) {
            SyntaxClass sonExp = expSonNodeList.get(0);
            if (sonExp.getSyntaxType() == SyntaxClass.NUMBER) {
                numberCal(sonExp);
            } else { // LVal
                constLValCal(sonExp);
            }
            constPrimaryExp.setConstValue(sonExp.getConstValue());
        } else { // (Exp)
            SyntaxClass sonExp = expSonNodeList.get(1);
            constExpCal(sonExp);
            constPrimaryExp.setConstValue(sonExp.getConstValue());
        }
        constPrimaryExp.setCalculated(true);
    }

    public static void constLValCal(SyntaxClass constLVal) {
        if (constLVal.isCalculated()) {
            return;
        }
        ArrayList<SyntaxClass> expSonNodeList = constLVal.getSonNodeList();
        Token ident = (Token) expSonNodeList.get(0);
        SymbolTable lValEnv = constLVal.getCurEnv();
        VarSymbol identSymbol = (VarSymbol) lValEnv.globalLookup(ident.getTokenContext(), 0);
        if (!identSymbol.hasConstValue()) { // 本层后面覆盖了该LVal的定义，此处应向前找上一次定义
            //identSymbol = (VarSymbol) lValEnv.upLevelLookup(ident.getTokenContext(), 0);
            identSymbol = lValEnv.varGlobalLookup(ident.getTokenContext(), constLVal.getCurVarListPos());
        }
        if (expSonNodeList.size() == 1) {
            constLVal.setConstValue(identSymbol.constGetValue());
        } else {
            SyntaxClass constExp1 = expSonNodeList.get(2);
            constExpCal(constExp1);
            int dim1 = constExp1.getConstValue();
            if (expSonNodeList.size() == 4) {// 一维常量数组
                constLVal.setConstValue(identSymbol.constGetValue(dim1));
            } else { // 二维常量数组
                SyntaxClass constExp2 = expSonNodeList.get(5);
                constExpCal(constExp2);
                int dim0 = constExp2.getConstValue();
                constLVal.setConstValue(identSymbol.constGetValue(dim1, dim0));
            }
        }
        constLVal.setCalculated(true);
    }

    // Number无所谓Const与否，肯定是Const的
    public static void numberCal(SyntaxClass numberExp) {
        if (numberExp.isCalculated()) {
            return;
        }
        ArrayList<SyntaxClass> expSonNodeList = numberExp.getSonNodeList();
        ConstIntToken numberToken = (ConstIntToken) expSonNodeList.get(0);
        numberExp.setConstValue(numberToken.getMyValue());
        numberExp.setCalculated(true);
    }

    // 输出测试
    public static StringBuilder printUnit(SyntaxClass curUnit) {
        if (curUnit.getSyntaxType() == SyntaxClass.TOKEN) {
            if (((Token) curUnit).getTokenType() == Token.INTCON) {
                return new StringBuilder(Integer.toString(((ConstIntToken) curUnit).getMyValue())).append(" ");
            } else {
                StringBuilder returnSB = new StringBuilder(((Token) curUnit).getTokenContext()).append(" ");
                if (((Token) curUnit).getTokenType() == Token.SEMICN ||
                        ((Token) curUnit).getTokenType() == Token.LBRACE) {
                    returnSB.append("\n");
                }
                return returnSB;
            }
        }
        if (curUnit.isCalculated()) {
            return new StringBuilder(Integer.toString(curUnit.getConstValue()));
        }
        StringBuilder stringBuilder = new StringBuilder();
        ArrayList<SyntaxClass> sonList = curUnit.getSonNodeList();
        for (SyntaxClass unit : sonList) {
            stringBuilder.append(printUnit(unit));
            if (unit.getSyntaxType() == SyntaxClass.BLOCK) {
                stringBuilder.append("\n");
            }
            //stringBuilder.append(" ");
        }
        return stringBuilder;
    }
}
