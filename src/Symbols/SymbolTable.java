package Symbols;

import Exceptions.DuplicatedDefineIdentException;

import java.util.HashMap;

public class SymbolTable {
    private SymbolTable parent;
    private HashMap<String, Symbol> symbolMap;
    private boolean cycleBlock;
    private FuncSymbol curFunc;

    public SymbolTable() {
        parent = null;
        symbolMap = new HashMap<>();
        cycleBlock = false;
        curFunc = null;
    }

    public SymbolTable(SymbolTable parent) {
        this.parent = parent;
        symbolMap = new HashMap<>();
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

    public Symbol localLookup(String name) {
        return symbolMap.getOrDefault(name, null);
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
            // TODO: 当前作用域重定义错误抛出
            throw new DuplicatedDefineIdentException();
        } else {
            symbolMap.put(symbol.getName(), symbol);
        }
    }
}
