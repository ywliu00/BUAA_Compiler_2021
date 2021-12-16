package MIPSTranslatePackage;

import IR.IRSymbol;

import java.util.Objects;

public class DefUseNetElem {
    private IRSymbol symbol;
    private int blockID;
    private int lineNo;

    public DefUseNetElem(IRSymbol symbol, int blockID, int lineNo) {
        this.symbol = symbol;
        this.blockID = blockID;
        this.lineNo = lineNo;
    }

    public IRSymbol getSymbol() {
        return symbol;
    }

    public void setSymbol(IRSymbol symbol) {
        this.symbol = symbol;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DefUseNetElem elem = (DefUseNetElem) o;
        return blockID == elem.blockID && lineNo == elem.lineNo && Objects.equals(symbol, elem.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbol, blockID, lineNo);
    }
}
