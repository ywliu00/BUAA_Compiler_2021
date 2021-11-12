import IR.IRFuncSymbol;
import IR.IRSymbol;

import java.util.ArrayList;
import java.util.HashMap;

public class FunctionTemplate {
    public static final FunctionTemplate GLOBAL = null;
    public static final int RET_AREA_NUM = 1, REG_SAVE_AREA_NUM = 1; // 暂时只存$sp
    private String name;
    private HashMap<IRSymbol, Integer> symbolOffsetTable;
    private ArrayList<IRSymbol> fParamList; // 按顺序排列的参数列表
    private int curPos;

    public FunctionTemplate(String name) {
        this.name = name;
        symbolOffsetTable = new HashMap<>();
        fParamList = new ArrayList<>();
        curPos = 4 * (RET_AREA_NUM + REG_SAVE_AREA_NUM);
    }

    public FunctionTemplate(IRFuncSymbol funcSymbol) {
        this.name = funcSymbol.getFunc();
        symbolOffsetTable = new HashMap<>();
        fParamList = funcSymbol.getfParamList();
        curPos = 4 * (RET_AREA_NUM + REG_SAVE_AREA_NUM);
        for (IRSymbol symbol : fParamList) {
            curPos += 4;
            symbolOffsetTable.put(symbol, curPos);
        }
    }

    public int getRetOffset() { // 返回值
        return 0;
    }

    public int getSPOffset() {
        return 4;
    }

    public int getLocalVarOffset() { // 还在顶部，其实还要再挪4Byte
        return 4 * (RET_AREA_NUM + REG_SAVE_AREA_NUM);
    }

    public void alloca(int bytes) {
        curPos += bytes;
    }

    public int getTemplateSize() {
        return 4 * (RET_AREA_NUM + REG_SAVE_AREA_NUM + symbolOffsetTable.size());
    }

    public void addLocalSymbol(IRSymbol symbol) {
        curPos += 4;
        symbolOffsetTable.put(symbol, curPos);
    }

    public void setfParamList(ArrayList<IRSymbol> fParamList) {
        this.fParamList = fParamList;
        for (IRSymbol symbol : fParamList) {
            curPos += 4;
            symbolOffsetTable.put(symbol, curPos);
        }
    }

    public String getFuncName() {
        return name;
    }

    public int getSymbolOffset(IRSymbol symbol) {
        return symbolOffsetTable.get(symbol);
    }

    public boolean isLocalSymbol(IRSymbol symbol) {
        return symbolOffsetTable.containsKey(symbol);
    }
}
