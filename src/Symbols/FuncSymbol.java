package Symbols;

import SyntaxClasses.Token;

import java.util.ArrayList;

public class FuncSymbol extends Symbol{
    private boolean hasReturn; // 是否有返回值
    private ArrayList<SymbolType> fParams; // 参数类型列表

    public FuncSymbol(Token token, boolean hasReturn) {
        super(token, 1);
        this.hasReturn = hasReturn;
        this.fParams = new ArrayList<>();
    }

    public void addFormalParamType(int typeNo) {
        SymbolType type = new SymbolType(typeNo, 1);
        fParams.add(type);
    }

    public ArrayList<SymbolType> getfParams() {
        return fParams;
    }

    public boolean checkParamsLength(FuncSymbol func) {
        ArrayList<SymbolType> funcParams = func.getfParams();
        return funcParams.size() == fParams.size();
    }

    public boolean checkConsistent(FuncSymbol func) {
        ArrayList<SymbolType> funcParams = func.getfParams();
        for (int i = 0; i < fParams.size(); ++i) {
            SymbolType myType = fParams.get(i);
            SymbolType funcType = funcParams.get(i);
            if(myType.getDimType() != funcType.getDimType()) {
                return false; // 类型不匹配
            }
            if (myType.getDimType() > 0 && !funcType.isVar()) {
                return false; // 常量数组被传入
            }
        }
        return true;
    }
}
