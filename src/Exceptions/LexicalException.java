package Exceptions;

public class LexicalException extends Exception {
    private int lineNum;

    public LexicalException(int lineNum) {
        super();
        this.lineNum = lineNum;
    }

    public int getLineNum() {
        return lineNum;
    }
}
