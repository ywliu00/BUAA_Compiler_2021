package Optimizer.DAG;

import IR.IRFuncSymbol;

public class DAGCallNode extends DAGNode{
    private IRFuncSymbol funcSymbol;

    public DAGCallNode(int id, int type, IRFuncSymbol funcSymbol) {
        super(id, type);
        this.funcSymbol = funcSymbol;
    }
}
