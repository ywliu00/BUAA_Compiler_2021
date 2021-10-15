package Symbols;

import java.util.ArrayList;

public class FuncSymbol extends Symbol{
    private boolean hasReturn; // 是否有返回值
    private ArrayList<SymbolType> fParams; // 参数类型列表

    public FuncSymbol(boolean hasReturn) {
        this.hasReturn = hasReturn;
        this.fParams = new ArrayList<>();
    }
}
