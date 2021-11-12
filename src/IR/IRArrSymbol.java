package IR;

public class IRArrSymbol implements IRSymbol{
    private IRSymbol baseAddr;
    private IRSymbol offset;

    public IRArrSymbol(IRSymbol base, IRSymbol offset) {
        this.baseAddr = base;
        this.offset = offset;
    }

    public IRSymbol getBaseAddr() {
        return baseAddr;
    }

    public IRSymbol getOffset() {
        return offset;
    }

    @Override
    public int getId() {
        return -1;
    }
}
