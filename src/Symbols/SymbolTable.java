package Symbols;

import Exceptions.DuplicatedDefineIdentException;

import java.util.HashMap;

public class SymbolTable {
    private SymbolTable parent;
    private HashMap<String, Symbol> symbolMap;
    private boolean cycleBlock;

    public SymbolTable() {
        parent = null;
        symbolMap = new HashMap<>();
        cycleBlock = false;
    }

    public SymbolTable(SymbolTable parent) {
        this.parent = parent;
        symbolMap = new HashMap<>();
        cycleBlock = false;
    }

    public void setCycleBlock(boolean cycleBlock) {
        this.cycleBlock = cycleBlock;
    }

    public boolean isCycleBlock() {
        return this.cycleBlock;
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
