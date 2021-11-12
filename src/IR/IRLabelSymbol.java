package IR;

public class IRLabelSymbol implements IRSymbol{
    private int id;
    private boolean isGlobal;

    public IRLabelSymbol(int id) {
        this.id = id;
        this.isGlobal = false;
    }

    public void setGlobal(boolean global) {
        isGlobal = global;
    }

    public boolean isGlobal() {
        return isGlobal;
    }

    public int getId() {
        return id;
    }

    @Override
    public String toString() {
        return "#" + id;
    }
}
