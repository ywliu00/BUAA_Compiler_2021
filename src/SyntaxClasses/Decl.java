package SyntaxClasses;

public class Decl extends SyntaxClass {
    private SyntaxClass declBody;

    public Decl(int lineNo, SyntaxClass declBody) {
        super(lineNo);
        this.declBody = declBody;
    }

    @Override
    public String toString() {
        return declBody.toString();
    }
}
