package Symbols;

import Exceptions.DuplicatedDefineIdentException;

import java.util.HashMap;

public class SymbolTable {
    private SymbolTable parent;
    private HashMap<String, VarSymbol> varSymbolMap;
    private HashMap<String, FuncSymbol> funcSymbolMap;
    private boolean cycleBlock;
    private FuncSymbol curFunc;

    public SymbolTable() {
        parent = null;
        varSymbolMap = new HashMap<>();
        funcSymbolMap = new HashMap<>();
        cycleBlock = false;
        curFunc = null;
    }

    public SymbolTable(SymbolTable parent) {
        this.parent = parent;
        varSymbolMap = new HashMap<>();
        funcSymbolMap = new HashMap<>();
        cycleBlock = false;
        curFunc = null;
    }

    public void setCurFunc(FuncSymbol curFunc) {
        this.curFunc = curFunc;
    }

    public FuncSymbol getCurFunc() {
        return curFunc;
    }

    public FuncSymbol checkCurFunc() {
        SymbolTable curEnv = this;
        while (curEnv != null && curEnv.getCurFunc() == null) {
            curEnv = curEnv.getParent();
        }
        if (curEnv != null) {
            return curEnv.getCurFunc();
        } else {
            return null;
        }
    }

    public void setCycleBlock(boolean cycleBlock) {
        this.cycleBlock = cycleBlock;
    }

    public boolean isInCycleBlock() {
        SymbolTable curEnv = this;
        while (curEnv != null && !curEnv.cycleBlock && curEnv.getCurFunc() == null) {
            curEnv = curEnv.getParent();
        }
        if (curEnv != null) {
            return curEnv.cycleBlock;
        } else {
            return false;
        }
    }

    public SymbolTable getParent() {
        return parent;
    }

    public VarSymbol varLocalLookup(String name) {
        return varSymbolMap.getOrDefault(name, null);
    }

    public VarSymbol varGlobalLookup(String name) {
        SymbolTable curTable = this;
        while (curTable != null) {
            VarSymbol res = curTable.varLocalLookup(name);
            if (res != null) {
                return res;
            }
            curTable = curTable.getParent();
        }
        return null;
    }

    public Symbol globalLookup(String name, int type) {
        Symbol symbol = globalLookup(name);
        if (symbol == null) {
            return null;
        }
        if (type == 0) {
            return (symbol instanceof VarSymbol) ? symbol : null;
        } else {
            return (symbol instanceof FuncSymbol) ? symbol : null;
        }
    }

    public FuncSymbol funcLocalLookup(String name) {
        return funcSymbolMap.getOrDefault(name, null);
    }

    public FuncSymbol funcGlobalLookup(String name) {
        SymbolTable curTable = this;
        while (curTable != null) {
            FuncSymbol res = curTable.funcLocalLookup(name);
            if (res != null) {
                return res;
            }
            curTable = curTable.getParent();
        }
        return null;
    }

    public Symbol localLookup(String name) {
        Symbol res = varSymbolMap.getOrDefault(name, null);
        if (res != null) {
            return res;
        } else {
            return funcSymbolMap.getOrDefault(name, null);
        }
    }

    public Symbol globalLookup(String name) {
        SymbolTable curTable = this;
        while (curTable != null) {
            Symbol res = curTable.localLookup(name);
            if (res != null) {
                return res;
            }
            curTable = curTable.getParent();
        }
        return null;
    }

    public void addSymbol(Symbol symbol) throws DuplicatedDefineIdentException {
        if (localLookup(symbol.getName()) != null) {
            throw new DuplicatedDefineIdentException();
        } else {
            if (symbol instanceof VarSymbol) {
                varSymbolMap.put(symbol.getName(), (VarSymbol) symbol);
            } else {
                funcSymbolMap.put(symbol.getName(), (FuncSymbol) symbol);
            }
        }
    }
}
