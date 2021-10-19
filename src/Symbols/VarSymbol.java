package Symbols;

import SyntaxClasses.Token;

public class VarSymbol extends Symbol {
    private SymbolType varType;
    private int[] dimLength;

    public VarSymbol(Token token, int isVar, int dimType) {
        super(token, 0);
        varType = new SymbolType(dimType, isVar);
        dimLength = new int[]{0, 0, 0};
    }

    public void setDimLengthByDim(int dim, int length) {
        this.dimLength[dim] = length;
    }

    public boolean isVar() {
        return varType.isVar();
    }

    public int getDimType() {
        return varType.getDimType();
    }

    public int getDimLength(int dim) {
        return dimLength[dim];
    }
}
