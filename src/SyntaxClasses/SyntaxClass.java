package SyntaxClasses;

import java.util.LinkedList;

public class SyntaxClass {
    public static final int COMPUNIT = 0, DECL = 1, CONSTDECL = 2, BTYPE = 3, CONSTDEF = 4,
            CONSTINITVAL = 5, VARDECL = 6, VARDEF = 7, INITVAL = 8, FUNCDEF = 9, FUNCTYPE = 10,
            FUNCFPARAMS = 11, FUNCFPARAM = 12, BLOCK = 13, BLOCKITEM = 14, STMT = 15,
            EXP = 16, COND = 17, LVAL = 18, PRIMARYEXP = 19, NUMBER = 20, UNARYEXP = 21,
            UNARYOP = 22, FUNCPARAMS = 23, MULEXP = 24, ADDEXP = 25, RELEXP = 26, EQEXP = 27,
            LANDEXP = 28, LOREXP = 29, CONSTEXP = 30, MAINFUNCDEF = 31,
            TOKEN = 32;
    public static final String[] syntaxNames = new String[]{"CompUnit", "Decl", "ConstDecl", "Btype", "ConstDef",
            "ConstInitVal", "VarDecl", "VarDef", "InitVal", "FuncDef", "FuncType",
            "FuncFParams", "FuncFParam", "Block", "BlockItem", "Stmt",
            "Exp", "Cond", "LVal", "PrimaryExp", "Number", "UnaryExp",
            "UnaryOp", "FuncParams", "MulExp", "AddExp", "RelExp", "EqExp",
            "LAndExp", "LOrExp", "ConstExp", "MainFuncDef"};
    private int lineNo;
    private int syntaxType;
    private LinkedList<SyntaxClass> sonNodeList;

    public SyntaxClass() {

    }

    public SyntaxClass(int syntaxType) {
        this.syntaxType = syntaxType;
        this.sonNodeList = new LinkedList<>();
    }

    public SyntaxClass(int lineNum, int typeNum) {
        this.lineNo = lineNum;
        this.syntaxType = typeNum;
        this.sonNodeList = new LinkedList<>();
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

    @Override
    public String toString() {
        StringBuilder outStringBuilder = new StringBuilder("");
        for (SyntaxClass syntax : this.sonNodeList) {
            outStringBuilder.append(syntax.toString());
            outStringBuilder.append("");
        }
        if (this.syntaxType != SyntaxClass.BLOCKITEM &&
                this.syntaxType != SyntaxClass.DECL &&
                this.syntaxType != SyntaxClass.BTYPE) {
            outStringBuilder.append("<");
            outStringBuilder.append(SyntaxClass.syntaxNames[this.syntaxType]);
            outStringBuilder.append(">\n");
        }
        return outStringBuilder.toString();
    }
}
