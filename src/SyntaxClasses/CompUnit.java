package SyntaxClasses;

import java.util.LinkedList;

public class CompUnit extends SyntaxClass {
    private LinkedList<Decl> declList;
    private LinkedList<FuncDef> funcDefList;
    private MainFuncDef mainFuncDef;

    public CompUnit(int lineNum, LinkedList<Decl> declList,
                    LinkedList<FuncDef> funcDefList, MainFuncDef mainFuncDef) {
        super(lineNum);
        this.declList = declList;
        this.funcDefList = funcDefList;
        this.mainFuncDef = mainFuncDef;
    }

    public String declListToString() {
        String outStr = "";
        for (Decl decl : declList) {
            outStr += decl.toString() + "\n";
        }
        return outStr;
    }

    public String funcDefListToString() {
        String outStr = "";
        for (Funcdef funcdef : funcDefList) {
            outStr += funcdef.toString() + "\n";
        }
        return outStr;
    }

    @Override
    public String toString() {
        return declListToString() + funcDefListToString() +
                mainFuncDef.toString() + "\n" + "<CompUnit>";
    }
}
