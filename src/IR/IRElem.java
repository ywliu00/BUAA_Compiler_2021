package IR;

import java.util.ArrayList;

public class IRElem {
    public static final int ADD = 0, MINU = 1, MULT = 2, DIV = 3, ASSIGN = 4,
            LSHIFT = 5, RSHIFT = 6, GRE = 7, GEQ = 8, LSS = 9, LEQ = 10,
            EQL = 11, NEQ = 12, BR = 13, BZ = 14, CALL = 15, RET = 16,
            EXIT = 17, LOAD = 18, STORE = 19, ALLOCA = 20, GETINT = 21,
            PRINTS = 22, FUNC = 23, LABEL = 24, AND = 25, OR = 26, SETRET = 27,
            PRINTI = 28;
    private int type;
    private IRSymbol op1;
    private IRSymbol op2;
    private IRSymbol op3;
    private ArrayList<IRSymbol> symbolList;

    public IRElem(int type) {
        this.type = type;
        this.op1 = null;
        this.op2 = null;
        this.op3 = null;
        this.symbolList = null;
    }

    public IRElem(int type, IRSymbol op3) {
        this.type = type;
        this.op1 = null;
        this.op2 = null;
        this.op3 = op3;
        this.symbolList = null;
    }

    public IRElem(int type, IRSymbol op3, IRSymbol op1) {
        this.type = type;
        this.op1 = op1;
        this.op2 = null;
        this.op3 = op3;
        this.symbolList = null;
    }

    public IRElem(int type, IRSymbol op3, IRSymbol op1, IRSymbol op2) {
        this.type = type;
        this.op1 = op1;
        this.op2 = op2;
        this.op3 = op3;
        this.symbolList = null;
    }

    public IRElem(int type, IRSymbol func, ArrayList<IRSymbol> symbolList) {
        this.type = type;
        this.op1 = null;
        this.op2 = null;
        this.op3 = func;
        this.symbolList = symbolList;
    }

    public IRElem(int type, IRSymbol ret, IRSymbol func, ArrayList<IRSymbol> symbolList) {
        this.type = type;
        this.op1 = func;
        this.op2 = null;
        this.op3 = ret;
        this.symbolList = symbolList;
    }

    public int getType() {
        return type;
    }

    public IRSymbol getOp1() {
        return op1;
    }

    public IRSymbol getOp2() {
        return op2;
    }

    public IRSymbol getOp3() {
        return op3;
    }

    public ArrayList<IRSymbol> getSymbolList() {
        return symbolList;
    }
}
