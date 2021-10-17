package Symbols;

public class SymbolType {
    public static final int INT = 0, ARR_1_DIM = 1, ARR_2_DIM = 2, ARR_3_DIM = 3, VOID = -1;
    public static final int CONST = 0, VARIABLE = 1;

    private int dimType;
    private int isVariable;

    public SymbolType(int dimType, int isVariable) {
        this.dimType = dimType;
        this.isVariable = isVariable;
    }

    public boolean isVar() {
        return this.isVariable == VARIABLE;
    }

    public int getDimType() {
        return dimType;
    }
}
