package SyntaxClasses;

import Exceptions.SyntaxException;
import Symbols.SymbolTable;

import java.util.LinkedList;

public class SyntaxClass {
    public static final int COMPUNIT = 0, DECL = 1, CONSTDECL = 2, BTYPE = 3, CONSTDEF = 4,
            CONSTINITVAL = 5, VARDECL = 6, VARDEF = 7, INITVAL = 8, FUNCDEF = 9, FUNCTYPE = 10,
            FUNCFPARAMS = 11, FUNCFPARAM = 12, BLOCK = 13, BLOCKITEM = 14, STMT = 15,
            EXP = 16, COND = 17, LVAL = 18, PRIMARYEXP = 19, NUMBER = 20, UNARYEXP = 21,
            UNARYOP = 22, FUNCRPARAMS = 23, MULEXP = 24, ADDEXP = 25, RELEXP = 26, EQEXP = 27,
            LANDEXP = 28, LOREXP = 29, CONSTEXP = 30, MAINFUNCDEF = 31,
            TOKEN = 32;
    public static final String[] syntaxNames = new String[]{"CompUnit", "Decl", "ConstDecl", "BType", "ConstDef",
            "ConstInitVal", "VarDecl", "VarDef", "InitVal", "FuncDef", "FuncType",
            "FuncFParams", "FuncFParam", "Block", "BlockItem", "Stmt",
            "Exp", "Cond", "LVal", "PrimaryExp", "Number", "UnaryExp",
            "UnaryOp", "FuncRParams", "MulExp", "AddExp", "RelExp", "EqExp",
            "LAndExp", "LOrExp", "ConstExp", "MainFuncDef"};
    private int lineNo;
    private int syntaxType;
    private LinkedList<SyntaxClass> sonNodeList;
    private SymbolTable curEnv; // 当前符号表
    private int constValue;
    private boolean calculated;

//    public SyntaxClass() {
//
//    }

    public SyntaxClass(int syntaxType) {
        this.syntaxType = syntaxType;
        this.sonNodeList = new LinkedList<>();
        calculated = false;
    }

    public SyntaxClass(int lineNum, int typeNum) {
        this.lineNo = lineNum;
        this.syntaxType = typeNum;
        this.sonNodeList = new LinkedList<>();
    }

    public void setCalculated(boolean isCalculated) {
        calculated = isCalculated;
    }

    public int getConstValue() throws SyntaxException {
        if (!calculated) {
            if (syntaxType == CONSTEXP) {
                constValue = sonNodeList.get(0).getConstValue();
                calculated = true;
                return constValue;
            } else if (syntaxType == ADDEXP) {
                if (sonNodeList.size() == 1) {
                    constValue = sonNodeList.get(0).getConstValue();
                } else {
                    if (((Token) sonNodeList.get(1)).getTokenType() == Token.PLUS) {
                        constValue = sonNodeList.get(0).getConstValue() + sonNodeList.get(2).getConstValue();
                    } else {
                        constValue = sonNodeList.get(0).getConstValue() - sonNodeList.get(2).getConstValue();
                    }
                }
                calculated = true;
                return constValue;
            } else if (syntaxType == MULEXP) {
                if (sonNodeList.size() == 1) {
                    constValue = sonNodeList.get(0).getConstValue();
                } else {
                    if (((Token) sonNodeList.get(1)).getTokenType() == Token.MULT) {
                        constValue = sonNodeList.get(0).getConstValue() * sonNodeList.get(2).getConstValue();
                    } else {
                        constValue = sonNodeList.get(0).getConstValue() / sonNodeList.get(2).getConstValue();
                    }
                }
                calculated = true;
                return constValue;
            } else if (syntaxType == UNARYEXP) {
                if (sonNodeList.get(0).getSyntaxType() == SyntaxClass.PRIMARYEXP) {
                    constValue = sonNodeList.get(0).getConstValue();
                } else if (sonNodeList.get(0).getSyntaxType() == SyntaxClass.UNARYOP) {
                    SyntaxClass unaryOp = sonNodeList.get(0);
                    if (((Token) unaryOp).getTokenType() == Token.PLUS) {
                        constValue = sonNodeList.get(1).getConstValue();
                    } else if (((Token) unaryOp).getTokenType() == Token.MINU){
                        constValue = -sonNodeList.get(1).getConstValue();
                    } else { // !，条件运算，逻辑取反
                        constValue = (sonNodeList.get(1).getConstValue()) == 0 ? 1 : 0;
                    }
                } else { // 函数，无法计算
                    throw new SyntaxException();
                }
                calculated = true;
                return constValue;
            } else if (syntaxType == PRIMARYEXP) {
                if (sonNodeList.get(0).getSyntaxType() == SyntaxClass.LVAL) {
                    constValue = sonNodeList.get(0).getConstValue();
                } else if (sonNodeList.get(0).getSyntaxType() == SyntaxClass.NUMBER){
                    constValue = sonNodeList.get(0).getConstValue();
                } else { // (Exp)
                    constValue = sonNodeList.get(1).getConstValue();
                }
                calculated = true;
                return constValue;
            } else if (syntaxType == EXP) {
                constValue = sonNodeList.get(0).getConstValue();
                calculated = true;
                return constValue;
            } else if (syntaxType == NUMBER) {
                constValue = sonNodeList.get(0).getConstValue();
                calculated = true;
                return constValue;
            } else if (syntaxType == LVAL) {
                // TODO: 左值求值
                SymbolTable lValEnv = getCurEnv();
                Token ident = (Token) sonNodeList.get(0);

            }
        }
        return 0;
    }

    public void setConstValue(int constValue) {
        this.constValue = constValue;
    }

//    public int getConstValue() {
//        return constValue;
//    }

    public LinkedList<SyntaxClass> getSonNodeList() {
        return sonNodeList;
    }

    public void setCurEnv(SymbolTable curEnv) {
        this.curEnv = curEnv;
    }

    public SymbolTable getCurEnv() {
        return curEnv;
    }

    public void appendSonNode(SyntaxClass sonNode) {
        this.sonNodeList.addLast(sonNode);
    }

    public int getSyntaxType() {
        return syntaxType;
    }

    public int getLineNo() {
        return lineNo;
    }

    public void setLineNo(int lineNo) {
        this.lineNo = lineNo;
    }

    public void setSyntaxType(int syntaxType) {
        this.syntaxType = syntaxType;
    }

    public void setFirstAsLineNo() {
        this.lineNo = this.sonNodeList.get(0).getLineNo();
    }

    public static void constExpCal(SyntaxClass constExp) {

    }

    public static void constAddExpCal(SyntaxClass constAddExp) {
    }

    @Override
    public String toString() {
        StringBuilder outStringBuilder = new StringBuilder("");
        for (SyntaxClass syntax : this.sonNodeList) {
            outStringBuilder.append(syntax.toString());
        }
        if (this.syntaxType != SyntaxClass.BLOCKITEM &&
                this.syntaxType != SyntaxClass.DECL &&
                this.syntaxType != SyntaxClass.BTYPE) {
            outStringBuilder.append("<");
            outStringBuilder.append(SyntaxClass.syntaxNames[this.syntaxType]);
            outStringBuilder.append(">");
            if (this.syntaxType != SyntaxClass.COMPUNIT) {
                outStringBuilder.append("\n");
            }
        }
        return outStringBuilder.toString();
    }
}
