package IR;

public class IRImmSymbol implements IRSymbol{
    private int value;
    public static IRImmSymbol ZERO = new IRImmSymbol(0);

    public IRImmSymbol(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    @Override
    public int getId() {
        return value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
