package Exceptions;

public class SyntaxException extends Exception{
    private int lineNum;
    public SyntaxException() {
    }
    public SyntaxException(int lineNo) {
        lineNum = lineNo;
    }

    public int getLineNum() {
        return lineNum;
    }
}
