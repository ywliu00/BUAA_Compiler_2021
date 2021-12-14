package MIPSTranslatePackage;

import IR.IRSymbol;

public class DefUseNetElem {
    IRSymbol symbol;
    int blockID;
    int lineNo;

    public DefUseNetElem(IRSymbol symbol, int blockID, int lineNo) {
        this.symbol = symbol;
        this.blockID = blockID;
        this.lineNo = lineNo;
    }

    public IRSymbol getSymbol() {
        return symbol;
    }

    public int getBlockID() {
        return blockID;
    }

    public int getLineNo() {
        return lineNo;
    }

    @Override
    public String toString() {
        return "<#" + symbol.getId() +
                ", B" + blockID +
                ", " + lineNo +
                '>';
    }
}
