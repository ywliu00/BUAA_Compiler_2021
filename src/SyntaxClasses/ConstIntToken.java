package SyntaxClasses;

public class ConstIntToken extends Token{
    int myValue;
    public ConstIntToken(int lineNum, String context) {
        super(Token.INTCON, lineNum, context);
        myValue = Integer.parseInt(context);
    }

    public int getMyValue() {
        return myValue;
    }
}
