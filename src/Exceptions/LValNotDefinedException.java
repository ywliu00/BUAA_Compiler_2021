package Exceptions;

import SyntaxClasses.SyntaxClass;

public class LValNotDefinedException extends Exception{
    private int lineNum;
    private SyntaxClass lVal;

    public LValNotDefinedException(int lineNum) {
        super();
        this.lineNum = lineNum;
    }

    public SyntaxClass getlVal() {
        return lVal;
    }

    public void setlVal(SyntaxClass lVal) {
        this.lVal = lVal;
    }

    public int getLineNum() {
        return lineNum;
    }
}
