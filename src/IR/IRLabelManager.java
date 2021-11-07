package IR;

import java.util.HashMap;

public class IRLabelManager {
    private static IRLabelManager instance = null;
    private HashMap<Integer, IRLabelSymbol> labelMap;
    private int cnt;

    private IRLabelManager() {
        labelMap = new HashMap<>();
        cnt = 0;
    }

    public static IRLabelManager getIRLabelManager() {
        if (instance == null) {
            instance = new IRLabelManager();
        }
        return instance;
    }

    public IRLabelSymbol allocSymbol() {
        IRLabelSymbol symbol = new IRLabelSymbol(cnt);
        labelMap.put(cnt, symbol);
        ++cnt;
        return symbol;
    }

    public IRLabelSymbol getSymbolById(int id) {
        return labelMap.get(id);
    }
}
