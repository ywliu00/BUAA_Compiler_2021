package IR;

public class IRFuncSymbol implements IRSymbol{
    String func;

    public IRFuncSymbol(String funcName) {
        this.func = funcName;
    }

    public String getFunc() {
        return func;
    }

    @Override
    public String toString() {
        return func + "()";
    }
}
