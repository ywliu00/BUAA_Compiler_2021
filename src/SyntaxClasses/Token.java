package SyntaxClasses;

public class Token extends SyntaxClass{
    public static final int IDENFR = 0, INTCON = 1, STRCON = 2, MAINTK = 3, CONSTTK = 4, INTTK = 5,
            BREAKTK = 6, CONTINUETK = 7, IFTK = 8, ELSETK = 9, NOT = 10, AND = 11, OR = 12, WHILETK = 13,
            GETINTTK = 14, PRINTFTK = 15, RETURNTK = 16, PLUS = 17, MINU = 18, VOIDTK = 19,
            MULT = 20, DIV = 21, MOD = 22, LSS = 23, LEQ = 24, GRE = 25, GEQ = 26, EQL = 27, NEQ = 28, ASSIGN = 29,
            SEMICN = 30, COMMA = 31, LPARENT = 32, RPARENT = 33, LBRACK = 34, RBRACK = 35, LBRACE = 36, RBRACE = 37;
    public static final String[] typeNames = new String[]{"IDENFR", "INTCON", "STRCON", "MAINTK", "CONSTTK", "INTTK",
            "BREAKTK", "CONTINUETK", "IFTK", "ELSETK", "NOT", "AND", "OR", "WHILETK", "GETINTTK", "PRINTFTK",
            "RETURNTK", "PLUS", "MINU", "VOIDTK", "MULT", "DIV", "MOD", "LSS", "LEQ", "GRE", "GEQ", "EQL", "NEQ",
            "ASSIGN", "SEMICN", "COMMA", "LPARENT", "RPARENT", "LBRACK", "RBRACK", "LBRACE", "RBRACE"};
    private int tokenType;
    private String tokenContext;
    private int formatCharNum;

    public Token(int type, int lineNum, String context) {
        super(lineNum, SyntaxClass.TOKEN);
        this.tokenType = type;
        this.tokenContext = context;
        this.formatCharNum = -1;
    }

    public void setFormatCharNum(int formatCharNum) {
        this.formatCharNum = formatCharNum;
    }

    public int getFormatCharNum() {
        return formatCharNum;
    }

    public int getTokenType() {
        return tokenType;
    }

    public String getTokenContext() {
        return tokenContext;
    }

    @Override
    public String toString() {
        return typeNames[this.tokenType] + " " + this.tokenContext + "\n";
    }
}
