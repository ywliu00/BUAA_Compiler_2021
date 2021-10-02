package SyntaxClasses;

public class BType extends SyntaxClass {
    Token intToken;

    public BType(int lineNo, Token intToken) {
        super(lineNo);
        this.intToken = intToken;
    }

    @Override
    public String toString() {
        return intToken.toString();
    }
}
