package SyntaxClasses;

import java.util.LinkedList;

public class ConstDecl extends SyntaxClass{
    private Token constToken;
    private BType bType;
    private LinkedList<ConstDef> constDefList;
    private Token semicnToken;

    public ConstDecl(int lineNo, Token constToken, BType bType,
                     LinkedList<ConstDef> constDefList, Token semicnToken) {
        super(lineNo);
        this.constToken = constToken;
        this.bType = bType;
        this.constDefList = constDefList;
        this.semicnToken = semicnToken;
    }

    public String constDefListToString() {
        String outStr = "";
        for (ConstDef constDef : this.constDefList) {
            outStr += constDef.toString() + "\n";
        }
        return outStr;
    }

    @Override
    public String toString() {
        return constToken.toString() + "\n" +
                bType.toString() + "\n" +
                this.constDefListToString() +
                this.semicnToken + "\n" +
                "<ConstDecl>";
    }
}
