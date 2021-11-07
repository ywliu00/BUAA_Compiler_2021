package IR;

public class IRLabelSymbol implements IRSymbol{
    private int id;

    public IRLabelSymbol(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "#" + id;
    }
}
