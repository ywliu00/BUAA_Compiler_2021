package SyntaxClasses;

import java.util.LinkedList;

public class ConstDef extends SyntaxClass {
    private Ident ident;
    private LinkedList<Token> lBrackList;
    private LinkedList<ConstExp> constExpList;
    private LinkedList<Token> rBrackList;
    private Token eqToken;
    private ConstInitVal constInitVal;
    private Token semicnToken;

    public ConstDef(int lineNo, Ident ident, LinkedList<ConstExp> constExpList,
                    Token eqToken, ConstInitVal constInitVal, Token semicnToken) {
        super(lineNo);
        this.ident = ident;
        this.lBrackList = new LinkedList<>();
        this.rBrackList = new LinkedList<>();
        this.constExpList = constExpList;
        this.eqToken = eqToken;
        this.constInitVal = constInitVal;
        this.semicnToken = semicnToken;
    }

    public ConstDef(int lineNo) {
        super(lineNo);
        lBrackList = new LinkedList<>();
        constExpList = new LinkedList<>();
        rBrackList = new LinkedList<>();
    }

    public void setConstInitVal(ConstInitVal constInitVal) {
        this.constInitVal = constInitVal;
    }

    public void setSemicnToken(Token semicnToken) {
        this.semicnToken = semicnToken;
    }

    public void setIdent(Ident ident) {
        this.ident = ident;
    }

    public void setEqToken(Token eqToken) {
        this.eqToken = eqToken;
    }

    public void addLeftBrack(Token lBrackToken) {
        lBrackList.addLast(lBrackToken);
    }

    public void addRightBrack(Token rBrackToken) {
        rBrackList.addLast(rBrackToken);
    }

    public void addLeftBrack(ConstExp constExp) {
        constExpList.addLast(constExp);
    }

    public String constExpListToString() {
        StringBuilder outStr = new StringBuilder();
        for (int i = 0; i < constExpList.size(); ++i) {
            outStr.append(lBrackList.get(i).toString());
            outStr.append("\n");
            outStr.append(constExpList.get(i).toString());
            outStr.append("\n");
            outStr.append(rBrackList.get(i).toString());
            outStr.append("\n");
        }
        return outStr.toString();
    }

    @Override
    public String toString() {
        return ident.toString() + "\n" +
                constExpListToString() +
                eqToken.toString() + "\n" +
                constInitVal + "\n" +
                semicnToken + "\n" + "<ConstDef>";
    }
}
