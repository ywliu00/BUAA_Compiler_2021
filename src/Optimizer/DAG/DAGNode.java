package Optimizer.DAG;

import IR.IRImmSymbol;
import IR.IRSymbol;

import java.util.ArrayList;
import java.util.HashSet;

public class DAGNode {
    private int id;
    private DAGNode lChild;
    private DAGNode rChild;
    private ArrayList<DAGNode> dependency;
    private int type;
    private HashSet<DAGNode> inNodes;
    private HashSet<IRSymbol> correspondingSymbols;
    private IRSymbol leafOldSymbol;
    //private IRSymbol leafRenameSymbol;
    private IRSymbol chosenSymbol; // 导出代码时被选中的Symbol
    private int immValue;
    private boolean hasImmValue;

    public DAGNode(int id, int type) {
        this.type = type;
        this.id = id;
        this.lChild = null;
        this.rChild = null;
        this.dependency = new ArrayList<>();
        this.correspondingSymbols = new HashSet<>();
        this.leafOldSymbol = null;
        //this.leafRenameSymbol = null;
        this.chosenSymbol = null;
        this.hasImmValue = false;
    }

    public boolean nodeHasImmValue() {
        return hasImmValue;
    }

    public int getImmValue() {
        return immValue;
    }

    public void setChosenSymbol(IRSymbol chosenSymbol) {
        this.chosenSymbol = chosenSymbol;
    }

    public IRSymbol getChosenSymbol() {
        return chosenSymbol;
    }

    /*public void renameLeafSymbol(IRSymbol leafRenameSymbol) {
        this.leafRenameSymbol = leafRenameSymbol;
    }*/

    public void setLeafOldSymbol(IRSymbol leafOldSymbol) {
        this.leafOldSymbol = leafOldSymbol;
    }

    /*public IRSymbol getLeafRenameSymbol() {
        return leafRenameSymbol;
    }*/

    public IRSymbol getLeafOldSymbol() {
        return leafOldSymbol;
    }

    public void addSymbol(IRSymbol symbol) {
        correspondingSymbols.add(symbol);
        if (symbol instanceof IRImmSymbol) {
            hasImmValue = true;
            immValue = ((IRImmSymbol) symbol).getValue();
        }
    }

    public void removeSymbol(IRSymbol symbol) {
        correspondingSymbols.remove(symbol);
    }

    public HashSet<IRSymbol> getCorrespondingSymbols() {
        return correspondingSymbols;
    }

    public void setLChild(DAGNode lChild) {
        this.lChild = lChild;
    }

    public void setRChild(DAGNode rChild) {
        this.rChild = rChild;
    }

    public void setDependency1(DAGNode thirdChild) {
        if (dependency.size() > 0) {
            dependency.set(0, thirdChild);
        } else {
            dependency.add(thirdChild);
        }
    }

    public void setDependency2(DAGNode dependencyNode) {
        if (dependency.size() > 1) {
            dependency.set(1, dependencyNode);
        } else {
            dependency.add(dependencyNode);
        }
    }

    public int getType() {
        return type;
    }

    public int getId() {
        return id;
    }

    public DAGNode getLChild() {
        return lChild;
    }

    public DAGNode getRChild() {
        return rChild;
    }

    public DAGNode getDependency1() {
        if (dependency.size() > 0) {
            return dependency.get(0);
        } else {
            return null;
        }
    }

    public DAGNode getDependency2() {
        if (dependency.size() > 1) {
            return dependency.get(1);
        } else {
            return null;
        }
    }

    public ArrayList<DAGNode> getDependencyArr() {
        return dependency;
    }

    public void setDependencyArr(ArrayList<DAGNode> dependencyArr) {
        this.dependency = dependencyArr;
    }
}
