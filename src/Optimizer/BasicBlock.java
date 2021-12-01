package Optimizer;

import IR.IRElem;
import IR.IRFuncSymbol;
import IR.IRSymbol;
import Optimizer.DAG.DAGClass;
import Optimizer.DAG.DAGNode;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;

public class BasicBlock {
    private ArrayList<BasicBlock> successors; // 后继
    private ArrayList<BasicBlock> predecessors; // 前驱
    private HashSet<IRSymbol> useSet; // 活跃变量分析的use集合
    private HashSet<IRSymbol> defSet; // 活跃变量分析的def集合
    private LinkedList<IRElem> iRList; // 基本块指令序列
    private HashSet<IRSymbol> inSetLVA;
    private HashSet<IRSymbol> outSetLVA;
    private int blockID;
    private DAGClass dag;

    private IRElem setRetInst; // 一个块内只有最后一条有效
    private HashSet<IRElem> labelSet;
    private IRElem funcInst; // 一个块内最多一个Func
    private LinkedList<DAGNode> calculateList;

    public BasicBlock(int id) {
        this.blockID = id;
        this.successors = new ArrayList<>();
        this.predecessors = new ArrayList<>();
        this.iRList = new LinkedList<>();
        this.inSetLVA = new HashSet<>();
        this.outSetLVA = new HashSet<>();

        this.setRetInst = null;
        this.funcInst = null;
        this.labelSet = new HashSet<>();
        this.calculateList = new LinkedList<>();
    }

    public void buildDAG() {
        DAGClass graph = new DAGClass();
        for (IRElem inst : iRList) {
            if (inst.getType() == IRElem.FUNC) {
                funcInst = inst;
            } else if (inst.getType() == IRElem.ADD || inst.getType() == IRElem.MULT
                    || inst.getType() == IRElem.EQL || inst.getType() == IRElem.NEQ) { // 可交换
                IRSymbol op1 = inst.getOp1();
                IRSymbol op2 = inst.getOp2(); // 操作数
                IRSymbol op3 = inst.getOp3(); // 运算结果
                DAGNode node1 = graph.getNodeOrCreateLeafNode(op1);
                DAGNode node2 = graph.getNodeOrCreateLeafNode(op2);
                graph.addMidNode(op3, inst.getType(), node1, node2, true);
            } else if (inst.getType() == IRElem.MINU || inst.getType() == IRElem.DIV ||
                    inst.getType() == IRElem.MOD || inst.getType() == IRElem.LSHIFT ||
                    inst.getType() == IRElem.RSHIFT || inst.getType() == IRElem.GRE ||
                    inst.getType() == IRElem.GEQ || inst.getType() == IRElem.LSS ||
                    inst.getType() == IRElem.LEQ) { // 不可交换
                IRSymbol op1 = inst.getOp1();
                IRSymbol op2 = inst.getOp2(); // 操作数
                IRSymbol op3 = inst.getOp3(); // 运算结果
                DAGNode node1 = graph.getNodeOrCreateLeafNode(op1);
                DAGNode node2 = graph.getNodeOrCreateLeafNode(op2);
                graph.addMidNode(op3, inst.getType(), node1, node2, false);
            } else if (inst.getType() == IRElem.ASSIGN) {
                IRSymbol op1 = inst.getOp1();
                IRSymbol op3 = inst.getOp3();
                DAGNode node1 = graph.getNodeOrCreateLeafNode(op1);
                if (node1.getType() == DAGClass.DAGLEAF) {
                    graph.addMidNode(op3, inst.getType(), node1, null, true);
                } else {
                    graph.addNodeAttr(op3, node1);
                }
            } else if (inst.getType() == IRElem.SETRET) {
                this.setRetInst = inst;
                IRSymbol opNum = inst.getOp3();
                DAGNode retNode = graph.getNodeOrCreateLeafNode(opNum);
                graph.addSetRetNode(retNode);
            } else if (inst.getType() == IRElem.BR || inst.getType() == IRElem.BZ ||
                    inst.getType() == IRElem.BNZ) {
                IRSymbol op3 = inst.getOp3();
                DAGNode retLabel = graph.getNodeOrCreateLeafNode(op3);
                DAGNode judgeValue = null;
                if (inst.getType() != IRElem.BR) {
                    IRSymbol op1 = inst.getOp1();
                    judgeValue = graph.getNodeOrCreateLeafNode(op1);
                }
                graph.addBlockEndNode(inst.getType(), retLabel, judgeValue);
            } else if (inst.getType() == IRElem.RET || inst.getType() == IRElem.EXIT) {
                graph.addBlockEndNode(inst.getType(), null, null);
            } else if (inst.getType() == IRElem.LOAD) {
                IRSymbol op1 = inst.getOp1();
                IRSymbol op2 = inst.getOp2();
                IRSymbol op3 = inst.getOp3();
                DAGNode base = graph.getNodeOrCreateLeafNode(op1);
                DAGNode offset = graph.getNodeOrCreateLeafNode(op2);
                graph.addReadMemNode(op3, inst.getType(), base, offset);
            } else if (inst.getType() == IRElem.STORE) {
                IRSymbol op1 = inst.getOp1();
                IRSymbol op2 = inst.getOp2();
                IRSymbol op3 = inst.getOp3();
                DAGNode value = graph.getNodeOrCreateLeafNode(op3);
                DAGNode base = graph.getNodeOrCreateLeafNode(op1);
                DAGNode offset = graph.getNodeOrCreateLeafNode(op2);
                graph.addWriteMemNode(inst.getType(), value, base, offset);
            } else if (inst.getType() == IRElem.ALLOCA) {
                IRSymbol op1 = inst.getOp1();
                IRSymbol op3 = inst.getOp3();
                DAGNode size = graph.getNodeOrCreateLeafNode(op1);
                graph.forcedAddMidNode(op3, inst.getType(), size, null); // 申请空间不可以合并
            } else if (inst.getType() == IRElem.PRINTI ||
                    inst.getType() == IRElem.PRINTS) {
                IRSymbol op3 = inst.getOp3();
                DAGNode outValue = graph.getNodeOrCreateLeafNode(op3);
                graph.addOutputNode(inst.getType(), outValue);
            } else if (inst.getType() == IRElem.GETINT) {
                IRSymbol op3 = inst.getOp3();
                graph.addInputNode(op3, inst.getType());
            } else if (inst.getType() == IRElem.CALL) {
                IRSymbol op3 = inst.getOp3();
                IRSymbol op1 = inst.getOp1();
                ArrayList<IRSymbol> paramList = inst.getSymbolList();
                ArrayList<DAGNode> paramNodeList = new ArrayList<>();
                for (IRSymbol symbol : paramList) {
                    DAGNode symbolNode = graph.getNodeOrCreateLeafNode(symbol);
                    paramNodeList.add(symbolNode);
                }
                graph.addCallNode(inst.getType(), op3, (IRFuncSymbol) op1, paramNodeList);
            } else if (inst.getType() == IRElem.LABEL) {
                graph.addLabelInst(inst);
                labelSet.add(inst);
            }
        }
        this.dag = graph;
    }

    public LinkedList<IRElem> reArrangeInstFromDAG() {
        LinkedList<IRElem> instList;

        HashSet<DAGNode> visited = new HashSet<>();
        for (IRSymbol liveSymbol : outSetLVA) {
            DAGNode node = dag.getNode(liveSymbol);
            if (node != null && node.getType() != DAGClass.DAGLEAF) { // 能找到，被修改过
                depthFirstSearch(node, visited);
            }
        }
        LinkedList<DAGNode> ioList = dag.getIoList();
        DAGNode ioLastNode;
        if (!ioList.isEmpty()) { // 连通，找最后一个就行
            ioLastNode = ioList.getLast();
            if (!visited.contains(ioLastNode)) {
                depthFirstSearch(ioLastNode, visited);
            }
        }
        ArrayList<DAGNode> memList = dag.getMemList();
        DAGNode memLastModifyNode = null;
        for (int i = memList.size() - 1; i >= 0; i--) { // 连通，找最后一个就行
            memLastModifyNode = memList.get(i);
            if (memLastModifyNode.getType() != IRElem.LOAD) {
                if (!visited.contains(memLastModifyNode)) {
                    depthFirstSearch(memLastModifyNode, visited);
                }
                break;
            }
        }
        if (dag.getSetRetNode() != null) { // setret语句
            depthFirstSearch(dag.getSetRetNode(), visited);
        }
        if (dag.getBlockEndNode() != null) { // 块结束语句
            depthFirstSearch(dag.getBlockEndNode(), visited);
        }
        // TODO:按顺序计算
        for (DAGNode curNode : calculateList) {
            if (curNode.getType() == IRElem.ADD || curNode.getType() == IRElem.MULT
                    || curNode.getType() == IRElem.EQL || curNode.getType() == IRElem.NEQ) { // 可交换
                IRSymbol op1 = curNode.getOp1();
                IRSymbol op2 = curNode.getOp2(); // 操作数
                IRSymbol op3 = curNode.getOp3(); // 运算结果
                DAGNode node1 = graph.getNodeOrCreateLeafNode(op1);
                DAGNode node2 = graph.getNodeOrCreateLeafNode(op2);
                graph.addMidNode(op3, curNode.getType(), node1, node2, true);
            } else if (curNode.getType() == IRElem.MINU || curNode.getType() == IRElem.DIV ||
                    curNode.getType() == IRElem.MOD || curNode.getType() == IRElem.LSHIFT ||
                    curNode.getType() == IRElem.RSHIFT || curNode.getType() == IRElem.GRE ||
                    curNode.getType() == IRElem.GEQ || curNode.getType() == IRElem.LSS ||
                    curNode.getType() == IRElem.LEQ) { // 不可交换
                IRSymbol op1 = curNode.getOp1();
                IRSymbol op2 = curNode.getOp2(); // 操作数
                IRSymbol op3 = curNode.getOp3(); // 运算结果
                DAGNode node1 = graph.getNodeOrCreateLeafNode(op1);
                DAGNode node2 = graph.getNodeOrCreateLeafNode(op2);
                graph.addMidNode(op3, curNode.getType(), node1, node2, false);
            } else if (curNode.getType() == IRElem.ASSIGN) {
                IRSymbol op1 = curNode.getOp1();
                IRSymbol op3 = curNode.getOp3();
                DAGNode node1 = graph.getNodeOrCreateLeafNode(op1);
                if (node1.getType() == DAGClass.DAGLEAF) {
                    graph.addMidNode(op3, curNode.getType(), node1, null, true);
                } else {
                    graph.addNodeAttr(op3, node1);
                }
            } else if (curNode.getType() == IRElem.SETRET) {
                this.setRetInst = curNode;
                IRSymbol opNum = curNode.getOp3();
                DAGNode retNode = graph.getNodeOrCreateLeafNode(opNum);
                graph.addSetRetNode(retNode);
            } else if (curNode.getType() == IRElem.BR || curNode.getType() == IRElem.BZ ||
                    curNode.getType() == IRElem.BNZ) {
                IRSymbol op3 = curNode.getOp3();
                DAGNode retLabel = graph.getNodeOrCreateLeafNode(op3);
                DAGNode judgeValue = null;
                if (curNode.getType() != IRElem.BR) {
                    IRSymbol op1 = curNode.getOp1();
                    judgeValue = graph.getNodeOrCreateLeafNode(op1);
                }
                graph.addBlockEndNode(curNode.getType(), retLabel, judgeValue);
            } else if (curNode.getType() == IRElem.RET || curNode.getType() == IRElem.EXIT) {
                graph.addBlockEndNode(curNode.getType(), null, null);
            } else if (curNode.getType() == IRElem.LOAD) {
                IRSymbol op1 = curNode.getOp1();
                IRSymbol op2 = curNode.getOp2();
                IRSymbol op3 = curNode.getOp3();
                DAGNode base = graph.getNodeOrCreateLeafNode(op1);
                DAGNode offset = graph.getNodeOrCreateLeafNode(op2);
                graph.addReadMemNode(op3, curNode.getType(), base, offset);
            } else if (curNode.getType() == IRElem.STORE) {
                IRSymbol op1 = curNode.getOp1();
                IRSymbol op2 = curNode.getOp2();
                IRSymbol op3 = curNode.getOp3();
                DAGNode value = graph.getNodeOrCreateLeafNode(op3);
                DAGNode base = graph.getNodeOrCreateLeafNode(op1);
                DAGNode offset = graph.getNodeOrCreateLeafNode(op2);
                graph.addWriteMemNode(curNode.getType(), value, base, offset);
            } else if (curNode.getType() == IRElem.ALLOCA) {
                IRSymbol op1 = curNode.getOp1();
                IRSymbol op3 = curNode.getOp3();
                DAGNode size = graph.getNodeOrCreateLeafNode(op1);
                graph.forcedAddMidNode(op3, curNode.getType(), size, null); // 申请空间不可以合并
            } else if (curNode.getType() == IRElem.PRINTI ||
                    curNode.getType() == IRElem.PRINTS) {
                IRSymbol op3 = curNode.getOp3();
                DAGNode outValue = graph.getNodeOrCreateLeafNode(op3);
                graph.addOutputNode(curNode.getType(), outValue);
            } else if (curNode.getType() == IRElem.GETINT) {
                IRSymbol op3 = curNode.getOp3();
                graph.addInputNode(op3, curNode.getType());
            } else if (curNode.getType() == IRElem.CALL) {
                IRSymbol op3 = curNode.getOp3();
                IRSymbol op1 = curNode.getOp1();
                ArrayList<IRSymbol> paramList = curNode.getSymbolList();
                ArrayList<DAGNode> paramNodeList = new ArrayList<>();
                for (IRSymbol symbol : paramList) {
                    DAGNode symbolNode = graph.getNodeOrCreateLeafNode(symbol);
                    paramNodeList.add(symbolNode);
                }
                graph.addCallNode(curNode.getType(), op3, (IRFuncSymbol) op1, paramNodeList);
            } else if (curNode.getType() == IRElem.LABEL) {
                graph.addLabelInst(curNode);
                labelSet.add(curNode);
            }
        }
    }

    public void depthFirstSearch(DAGNode node, HashSet<DAGNode> visited) {
        if (node == null || node.getType() == DAGClass.DAGLEAF || visited.contains(node)) {
            return;
        }
        visited.add(node);
        if (node.getLChild() != null) {
            depthFirstSearch(node.getLChild(), visited);
        }
        if (node.getRChild() != null) {
            depthFirstSearch(node.getRChild(), visited);
        }
        ArrayList<DAGNode> dependency = node.getDependencyArr();
        for (DAGNode depNode : dependency) {
            if (depNode != null && !visited.contains(depNode)) {
                depthFirstSearch(depNode, visited);
            }
        }
        calculateList.add(node);
    }

    public void setInSetLVA(HashSet<IRSymbol> inSetLVA) {
        this.inSetLVA = inSetLVA;
    }

    public void setOutSetLVA(HashSet<IRSymbol> outSetLVA) {
        this.outSetLVA = outSetLVA;
    }

    public HashSet<IRSymbol> getInSetLVA() {
        return inSetLVA;
    }

    public HashSet<IRSymbol> getOutSetLVA() {
        return outSetLVA;
    }

    public HashSet<IRSymbol> getDefSet() {
        return defSet;
    }

    public HashSet<IRSymbol> getUseSet() {
        return useSet;
    }

    public void setUseAndDefSet(HashSet<IRSymbol> useSet, HashSet<IRSymbol> defSet) {
        this.useSet = useSet;
        this.defSet = defSet;
    }

    public int getBlockID() {
        return blockID;
    }

    public LinkedList<IRElem> getBlockIRList() {
        return iRList;
    }

    public void addIR(ArrayList<IRElem> globalIRList, int start, int end) { // 包含start，不含end
        int i = start;
        for (; i < end; ++i) {
            this.iRList.add(globalIRList.get(i));
        }
    }

    public ArrayList<BasicBlock> getPredecessors() {
        return predecessors;
    }

    public ArrayList<BasicBlock> getSuccessors() {
        return successors;
    }

    public void addSuccessor(BasicBlock successorBlock) {
        successors.add(successorBlock);
    }

    public void addPredecessor(BasicBlock forwardBlock) {
        predecessors.add(forwardBlock);
    }
}
