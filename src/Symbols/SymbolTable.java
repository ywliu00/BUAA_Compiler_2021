package Symbols;

import Exceptions.DuplicatedDefineIdentException;

import java.util.HashMap;

public class SymbolTable {
    private SymbolTable parent;
    private HashMap<String, Symbol> symbolMap;

    public SymbolTable() {
        parent = null;
        symbolMap = new HashMap<>();
    }

    public SymbolTable(SymbolTable parent) {
        this.parent = parent;
        symbolMap = new HashMap<>();
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
