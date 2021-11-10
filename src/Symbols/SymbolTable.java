package Symbols;

import Exceptions.DuplicatedDefineIdentException;
import IR.IRLabelSymbol;
import IR.IRSymbol;

import java.util.ArrayList;
import java.util.HashMap;

public class SymbolTable {
    private SymbolTable parent;
    private HashMap<String, VarSymbol> varSymbolMap;
    private HashMap<String, FuncSymbol> funcSymbolMap;
    private boolean cycleBlock;
    private FuncSymbol curFunc;
    private HashMap<VarSymbol, IRLabelSymbol> varRefMap;
    private HashMap<FuncSymbol, IRLabelSymbol> funcRefMap;
    private ArrayList<VarSymbol> varSymbolList;
    private IRSymbol cycleStart;
    private IRSymbol cycleEnd;

    public SymbolTable() {
        parent = null;
        varSymbolMap = new HashMap<>();
        funcSymbolMap = new HashMap<>();
        cycleBlock = false;
        curFunc = null;
        varRefMap = new HashMap<>();
        funcRefMap = new HashMap<>();
        varSymbolList = new ArrayList<>();
    }

    public SymbolTable(SymbolTable parent) {
        this.parent = parent;
        varSymbolMap = new HashMap<>();
        funcSymbolMap = new HashMap<>();
        cycleBlock = false;
        curFunc = null;
        varRefMap = new HashMap<>();
        funcRefMap = new HashMap<>();
        varSymbolList = new ArrayList<>();
    }

    public void setCycleStartEnd(IRSymbol cycleStart, IRSymbol cycleEnd) {
        this.cycleStart = cycleStart;
        this.cycleEnd = cycleEnd;
    }

    public IRSymbol getCurCycleStart() {
        return cycleStart;
    }

    public IRSymbol getCurCycleEnd() {
        return cycleEnd;
    }

    public IRSymbol findCycleEnd() {
        SymbolTable curTable = this;
        while (curTable != null) {
            if (curTable.getCurCycleEnd() != null) {
                return curTable.getCurCycleEnd();
            }
            curTable = curTable.getParent();
        }
        return null;
    }

    public IRSymbol findCycleStart() {
        SymbolTable curTable = this;
        while (curTable != null) {
            if (curTable.getCurCycleStart() != null) {
                return curTable.getCurCycleStart();
            }
            curTable = curTable.getParent();
        }
        return null;
    }

    public void setVarRef(VarSymbol varSymbol, IRLabelSymbol refSymbol) {
        varRefMap.put(varSymbol, refSymbol);
    }

    public void setFuncRef(FuncSymbol funcSymbol, IRLabelSymbol refSymbol) {
        funcRefMap.put(funcSymbol, refSymbol);
    }

    public IRLabelSymbol getFuncRef(FuncSymbol funcSymbol) {
        return funcRefMap.get(funcSymbol);
    }

    public IRLabelSymbol getCurLastRef(VarSymbol varSymbol) {
        return varRefMap.getOrDefault(varSymbol, null);
    }

    public IRLabelSymbol getLastVarRef(VarSymbol varSymbol) {
        SymbolTable curTable = this;
        while (curTable != null) {
            IRLabelSymbol refSymbol = curTable.getCurLastRef(varSymbol);
            if (refSymbol != null) {
                return refSymbol;
            }
            curTable = curTable.getParent();
        }
        return null;
    }

    public VarSymbol varLocalLookup(String name, int curListPos) {
        for (int i = curListPos - 1; i >= 0; --i) {
            if (varSymbolList.get(i).getName().equals(name)) {
                return varSymbolList.get(i);
            }
        }
        return null;
    }

    public VarSymbol varGlobalLookup(String name, int curListPos) {
        VarSymbol res = varLocalLookup(name, curListPos);
        if (res != null) {
            return res;
        }
        SymbolTable curTable = this.getParent();
        while (curTable != null) {
            res = curTable.varLocalLookup(name);
            if (res != null) {
                return res;
            }
            curTable = curTable.getParent();
        }
        return null;
    }

    public int getCurListPos() {
        return varSymbolList.size();
    }

    public void setCurFunc(FuncSymbol curFunc) {
        this.curFunc = curFunc;
    }

    public FuncSymbol getCurBlockFunc() {
        return curFunc;
    }

    public FuncSymbol checkCurFunc() {
        SymbolTable curEnv = this;
        while (curEnv != null && curEnv.getCurBlockFunc() == null) {
            curEnv = curEnv.getParent();
        }
        if (curEnv != null) {
            return curEnv.getCurBlockFunc();
        } else {
            return null;
        }
    }

    public void setCycleBlock(boolean cycleBlock) {
        this.cycleBlock = cycleBlock;
    }

    public boolean isInCycleBlock() {
        SymbolTable curEnv = this;
        while (curEnv != null && !curEnv.cycleBlock && curEnv.getCurBlockFunc() == null) {
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

    public Symbol globalLookup(String name, int type) { // 0:Var, 1:Func
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

    public Symbol upLevelLookup(String name, int type) {
        SymbolTable curTable = this.getParent();
        return (curTable == null) ? null : curTable.globalLookup(name, type);
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
                varSymbolList.add((VarSymbol) symbol);
            } else {
                funcSymbolMap.put(symbol.getName(), (FuncSymbol) symbol);
            }
        }
    }
}
