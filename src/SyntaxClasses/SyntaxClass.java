package SyntaxClasses;

public class SyntaxClass {
    public static final int COMPUNIT = 0, DECL = 1, CONSTDECL = 2, BTYPE = 3, CONSTDEF = 4,
            CONSTINITVAL = 5, VARDECL = 6, VARDEF = 7, INITVAL = 8, FUNCDEF = 9, FUNCTYPE = 10,
            FUNCFPARAMS = 11, FUNCFPARAM = 12, BLOCK = 13, BLOCKITEM = 14, STMT = 15,
            EXP = 16, COND = 17, LVAL = 18, PRIMARYEXP = 19, NUMBER = 20, UNARYEXP = 21,
            UNARYOP = 22, FUNCPARAMS = 23, MULEXP = 24, ADDEXP = 25, RELEXP = 26, EQEXP = 27,
            LANDEXP = 28, LOREXP = 29, CONSTEXP = 30;
    public static final String[] syntaxNames = new String[]{"CompUnit", "Decl", "ConstDecl", "Btype", "ConstDef",
            "ConstInitVal", "VarDecl", "VarDef", "InitVal", "FuncDef", "FuncType",
            "FuncFParams", "FuncFParam", "Block", "BlockItem", "Stmt",
            "Exp", "Cond", "LVal", "PrimaryExp", "Number", "UnaryExp",
            "UnaryOp", "FuncParams", "MulExp", "AddExp", "RelExp", "EqExp",
            "LAndExp", "LOrExp", "ConstExp"};
    private int lineNo;

    public SyntaxClass(int lineNum) {
        this.lineNo = lineNum;
    }

    public int getLineNo() {
        return lineNo;
    }
}
