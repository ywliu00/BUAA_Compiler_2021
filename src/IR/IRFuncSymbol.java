package IR;

import java.util.ArrayList;

public class IRFuncSymbol implements IRSymbol{
    private String func;
    private IRSymbol entry;
    private ArrayList<IRSymbol> fParamList;

    public IRFuncSymbol(String funcName) {
        this.func = funcName;
    }

    public void setfParamList(ArrayList<IRSymbol> fParamList) {
        this.fParamList = fParamList;
    }

    public ArrayList<IRSymbol> getfParamList() {
        return fParamList;
    }

    public void setEntry(IRSymbol entry) {
        this.entry = entry;
    }

    public IRSymbol getEntry() {
        return entry;
    }

    public String getFunc() {
        return func;
    }

    public int getId() {
        return -1;
    }

    @Override
    public String toString() {
        return func + "()";
    }
}
