package SyntaxClasses;

public class ConstInitValConst extends ConstInitVal{
    private ConstExp constExp;

    public ConstInitValConst(int lineNo, ConstExp constExp) {
        super(lineNo);
        this.constExp = constExp;
    }

    public ConstInitValConst(int lineNo) {
        super(lineNo);
    }

    public void setConstExp(ConstExp constExp) {
        this.constExp = constExp;
    }

    @Override
    public String toString() {
        return constExp.toString() + "\n" +
                "<ConstInitVal>";
    }
}
