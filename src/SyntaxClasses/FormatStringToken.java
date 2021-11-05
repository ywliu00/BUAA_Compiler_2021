package SyntaxClasses;

public class FormatStringToken extends Token{
    private int formatCharNum;

    public FormatStringToken(int lineNum, String context) {
        super(Token.STRCON, lineNum, context);
        this.formatCharNum = 0;
    }

    public void setFormatCharNum(int formatCharNum) {
        this.formatCharNum = formatCharNum;
    }

    public int getFormatCharNum() {
        return formatCharNum;
    }
}
