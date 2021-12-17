package Optimizer;

import IR.IRElem;
import IR.IRFuncSymbol;
import IR.IRImmSymbol;
import IR.IRLabelManager;
import IR.IRSymbol;
import MIPSTranslatePackage.DefUseNetElem;
import Optimizer.DAG.DAGCallNode;
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
    private HashSet<IRSymbol> inSetLVA; // 活跃变量分析的in集合
    private HashSet<IRSymbol> outSetLVA; // 活跃变量分析的out集合
    private HashSet<DefUseNetElem> genSet;
    private HashSet<DefUseNetElem> killSet;
    private HashSet<DefUseNetElem> inSetDG;
    private HashSet<DefUseNetElem> outSetDG;
    private int blockID;
    private DAGClass dag;

    private IRElem setRetInst; // 一个块内只有最后一条有效
    private HashSet<IRElem> labelSet;
    private IRElem funcInst; // 一个块内最多一个Func
    private LinkedList<DAGNode> calculateList;
    private LinkedList<IRElem> blockOptInstList;

    public BasicBlock(int id) {
        this.blockID = id;
        this.successors = new ArrayList<>();
        this.predecessors = new ArrayList<>();
        this.iRList = new LinkedList<>();
        this.inSetLVA = new HashSet<>();
        this.outSetLVA = new HashSet<>();
        this.defSet = new HashSet<>();
        this.useSet = new HashSet<>();
        this.genSet = new HashSet<>();
        this.killSet = new HashSet<>();
        this.inSetDG = new HashSet<>();
        this.outSetDG = new HashSet<>();

        this.setRetInst = null;
        this.funcInst = null;
        this.labelSet = new HashSet<>();
        this.calculateList = new LinkedList<>();
        blockOptInstList = null;
    }

    public void buildDAG() {
        DAGClass graph = new DAGClass();
        for (IRElem inst : iRList) {
            if (inst.getType() == IRElem.FUNC) {
                funcInst = inst;
            } else if (inst.getType() == IRElem.ADD || inst.getType() == IRElem.MULT
                    || inst.getType() == IRElem.EQL || inst.getType() == IRElem.NEQ ||
                    inst.getType() == IRElem.AND) { // 可交换
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
                    inst.getType() == IRElem.LEQ || inst.getType() == IRElem.RASHIFT) { // 不可交换
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
                /*if (node1.getType() == DAGClass.DAGLEAF) {
                    graph.addMidNode(op3, inst.getType(), node1, null, true);
                } else {*/
                graph.addNodeAttr(op3, node1);
                //}
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

    public HashSet<IRSymbol> inSetCal(HashSet<IRSymbol> outSet) {
        LinkedList<DAGNode> depList = new LinkedList<>();
        HashSet<DAGNode> visited = new HashSet<>();
        for (IRSymbol liveSymbol : outSet) {
            DAGNode node = dag.getNode(liveSymbol);
            if (node != null) { // 能找到，被修改过
                depthFirstSearch(node, visited, depList);
            }
        }
        LinkedList<DAGNode> ioList = dag.getIoList();
        DAGNode ioLastNode;
        if (!ioList.isEmpty()) { // 连通，找最后一个就行
            ioLastNode = ioList.getLast();
            if (!visited.contains(ioLastNode)) {
                depthFirstSearch(ioLastNode, visited, depList);
            }
        }
        ArrayList<DAGNode> memList = dag.getMemList();
        DAGNode memLastModifyNode = null;
        for (int i = memList.size() - 1; i >= 0; i--) { // 连通，找最后一个就行
            memLastModifyNode = memList.get(i);
            if (memLastModifyNode.getType() != IRElem.LOAD) {
                if (!visited.contains(memLastModifyNode)) {
                    depthFirstSearch(memLastModifyNode, visited, depList);
                }
                break;
            }
        }
        if (dag.getSetRetNode() != null) { // setret语句
            depthFirstSearch(dag.getSetRetNode(), visited, depList);
        }
        if (dag.getBlockEndNode() != null) { // 块结束语句
            depthFirstSearch(dag.getBlockEndNode(), visited, depList);
        }
        HashSet<IRSymbol> inSet = new HashSet<>();
        for (DAGNode node : depList) {
            if (node.getType()==DAGClass.DAGLEAF) {
                IRSymbol oldSymbol = node.getLeafOldSymbol();
                if (!(oldSymbol instanceof IRImmSymbol)){
                    inSet.add(oldSymbol);
                }
            }
        }
        return inSet;
    }

    public LinkedList<IRElem> reArrangeInstFromDAG() {
        HashSet<DAGNode> visited = new HashSet<>();
        for (IRSymbol liveSymbol : outSetLVA) {
            DAGNode node = dag.getNode(liveSymbol);
            if (node != null) { // 能找到，被修改过
                depthFirstSearch(node, visited, calculateList);
            }
        }
        LinkedList<DAGNode> ioList = dag.getIoList();
        DAGNode ioLastNode;
        if (!ioList.isEmpty()) { // 连通，找最后一个就行
            ioLastNode = ioList.getLast();
            if (!visited.contains(ioLastNode)) {
                depthFirstSearch(ioLastNode, visited, calculateList);
            }
        }
        ArrayList<DAGNode> memList = dag.getMemList();
        DAGNode memLastModifyNode = null;
        for (int i = memList.size() - 1; i >= 0; i--) { // 连通，找最后一个就行
            memLastModifyNode = memList.get(i);
            if (memLastModifyNode.getType() != IRElem.LOAD) {
                if (!visited.contains(memLastModifyNode)) {
                    depthFirstSearch(memLastModifyNode, visited, calculateList);
                }
                break;
            }
        }
        if (dag.getSetRetNode() != null) { // setret语句
            depthFirstSearch(dag.getSetRetNode(), visited, calculateList);
        }
        if (dag.getBlockEndNode() != null) { // 块结束语句
            depthFirstSearch(dag.getBlockEndNode(), visited, calculateList);
        }

        LinkedList<IRElem> instList = new LinkedList<>();
        IRLabelManager labelManager = IRLabelManager.getIRLabelManager();

        for (DAGNode curNode : calculateList) { // 最开始就确认所有叶节点变更名称情况
            if (curNode.getType() == DAGClass.DAGLEAF) { // 叶节点，检查是否有重命名
                //IRSymbol leafNewSymbol = curNode.getLeafRenameSymbol();
                HashSet<IRSymbol> symbols = curNode.getCorrespondingSymbols();
                if (!symbols.contains(curNode.getLeafOldSymbol())) { // 最开始的symbol被移除，有重命名，则加入一条赋值
                    IRSymbol leafOldSymbol = curNode.getLeafOldSymbol();
                    IRSymbol leafNewSymbol = labelManager.allocSymbol();
                    IRElem leafRenameAssign = new IRElem(IRElem.ASSIGN, leafNewSymbol, leafOldSymbol);
                    instList.add(leafRenameAssign);
                    curNode.setChosenSymbol(leafNewSymbol);
                } else {
                    for (IRSymbol symbol : curNode.getCorrespondingSymbols()) {
                        curNode.setChosenSymbol(curNode.getLeafOldSymbol()); // 最原本的那个
                    }
                }
            }
        }
        for (DAGNode curNode : calculateList) {
            if (curNode.getType() == DAGClass.DAGLEAF) {
                //HashSet<IRSymbol> liveSymbols = new HashSet<>();
                for (IRSymbol symbol : curNode.getCorrespondingSymbols()) {
                    if (outSetLVA.contains(symbol) && symbol != curNode.getChosenSymbol()) {
                        IRElem nodeInst = new IRElem(IRElem.ASSIGN, symbol, curNode.getChosenSymbol());
                        instList.add(nodeInst);
                        //liveSymbols.add(symbol);
                    }
                }
                /*for (IRSymbol symbol : liveSymbols) { // 其它活跃变量赋值
                    if (symbol != curNode.getChosenSymbol()) {
                        IRElem nodeInst = new IRElem(IRElem.ASSIGN, symbol, curNode.getChosenSymbol());
                        instList.add(nodeInst);
                    }
                }*/
            } else if (curNode.getType() == IRElem.ADD || curNode.getType() == IRElem.MULT ||
                    curNode.getType() == IRElem.EQL || curNode.getType() == IRElem.NEQ ||
                    curNode.getType() == IRElem.MINU || curNode.getType() == IRElem.DIV ||
                    curNode.getType() == IRElem.MOD || curNode.getType() == IRElem.LSHIFT ||
                    curNode.getType() == IRElem.RSHIFT || curNode.getType() == IRElem.GRE ||
                    curNode.getType() == IRElem.GEQ || curNode.getType() == IRElem.LSS ||
                    curNode.getType() == IRElem.LEQ || curNode.getType() == IRElem.RASHIFT ||
                    curNode.getType() == IRElem.AND) { // 三地址计算
                IRSymbol leftSymbol = curNode.getLChild().getChosenSymbol();
                IRSymbol rightSymbol = curNode.getRChild().getChosenSymbol();
                IRSymbol liveSymbol = null, deadSymbol = null;
                HashSet<IRSymbol> liveSymbols = new HashSet<>();
                for (IRSymbol symbol : curNode.getCorrespondingSymbols()) {
                    if (outSetLVA.contains(symbol)) {
                        liveSymbol = symbol;
                        liveSymbols.add(symbol);
                    } else {
                        deadSymbol = symbol;
                    }
                }
                IRSymbol resSymbol;
                if (liveSymbol != null) {
                    resSymbol = liveSymbol;
                } else if (deadSymbol != null) {
                    resSymbol = deadSymbol;
                } else {
                    resSymbol = labelManager.allocSymbol();
                }
                curNode.setChosenSymbol(resSymbol);
                IRElem nodeInst = new IRElem(curNode.getType(), resSymbol, leftSymbol, rightSymbol);
                instList.add(nodeInst); // 计算指令
                for (IRSymbol symbol : liveSymbols) { // 其它活跃变量赋值
                    if (symbol != resSymbol) {
                        nodeInst = new IRElem(IRElem.ASSIGN, symbol, resSymbol);
                        instList.add(nodeInst);
                    }
                }
            } else if (curNode.getType() == IRElem.ASSIGN) {
                IRSymbol leftSymbol = curNode.getLChild().getChosenSymbol();
                IRSymbol liveSymbol = null, deadSymbol = null;
                HashSet<IRSymbol> liveSymbols = new HashSet<>();
                for (IRSymbol symbol : curNode.getCorrespondingSymbols()) {
                    if (outSetLVA.contains(symbol)) {
                        liveSymbol = symbol;
                        liveSymbols.add(symbol);
                    } else {
                        deadSymbol = symbol;
                    }
                }
                IRSymbol resSymbol;
                if (liveSymbol != null) {
                    resSymbol = liveSymbol;
                } else if (deadSymbol != null) {
                    resSymbol = deadSymbol;
                } else {
                    resSymbol = labelManager.allocSymbol();
                }
                curNode.setChosenSymbol(resSymbol);
                IRElem nodeInst = new IRElem(curNode.getType(), resSymbol, leftSymbol);
                instList.add(nodeInst); // 计算指令
                for (IRSymbol symbol : liveSymbols) { // 其它活跃变量赋值
                    if (symbol != resSymbol) {
                        nodeInst = new IRElem(IRElem.ASSIGN, symbol, resSymbol);
                        instList.add(nodeInst);
                    }
                }
            } else if (curNode.getType() == IRElem.SETRET) {
                IRSymbol valueSymbol = curNode.getLChild().getChosenSymbol();
                IRElem nodeInst = new IRElem(curNode.getType(), valueSymbol);
                instList.add(nodeInst);
            } else if (curNode.getType() == IRElem.BR || curNode.getType() == IRElem.BZ ||
                    curNode.getType() == IRElem.BNZ) {
                continue;
            } else if (curNode.getType() == IRElem.RET || curNode.getType() == IRElem.EXIT) {
                continue;
            } else if (curNode.getType() == IRElem.LOAD) {
                IRSymbol baseSymbol = curNode.getLChild().getChosenSymbol();
                IRSymbol offsetSymbol = curNode.getRChild().getChosenSymbol();
                IRSymbol liveSymbol = null, deadSymbol = null;
                HashSet<IRSymbol> liveSymbols = new HashSet<>();
                for (IRSymbol symbol : curNode.getCorrespondingSymbols()) {
                    if (outSetLVA.contains(symbol)) {
                        liveSymbol = symbol;
                        liveSymbols.add(symbol);
                    } else {
                        deadSymbol = symbol;
                    }
                }
                IRSymbol resSymbol;
                if (liveSymbol != null) {
                    resSymbol = liveSymbol;
                } else if (deadSymbol != null) {
                    resSymbol = deadSymbol;
                } else {
                    resSymbol = labelManager.allocSymbol();
                }
                curNode.setChosenSymbol(resSymbol);
                IRElem nodeInst = new IRElem(curNode.getType(), resSymbol, baseSymbol, offsetSymbol);
                instList.add(nodeInst); // 计算指令
                for (IRSymbol symbol : liveSymbols) { // 其它活跃变量赋值
                    if (symbol != resSymbol) {
                        nodeInst = new IRElem(IRElem.ASSIGN, symbol, resSymbol);
                        instList.add(nodeInst);
                    }
                }
            } else if (curNode.getType() == IRElem.STORE) {
                IRSymbol valueSymbol = curNode.getLChild().getChosenSymbol();
                IRSymbol baseSymbol = curNode.getRChild().getChosenSymbol();
                IRSymbol offsetSymbol = curNode.getDependency1().getChosenSymbol();
                IRElem nodeInst = new IRElem(curNode.getType(), valueSymbol, baseSymbol, offsetSymbol);
                instList.add(nodeInst);
            } else if (curNode.getType() == IRElem.ALLOCA) {
                IRSymbol sizeSymbol = curNode.getLChild().getChosenSymbol();
                IRSymbol liveSymbol = null, deadSymbol = null;
                HashSet<IRSymbol> liveSymbols = new HashSet<>();
                for (IRSymbol symbol : curNode.getCorrespondingSymbols()) {
                    if (outSetLVA.contains(symbol)) {
                        liveSymbol = symbol;
                        liveSymbols.add(symbol);
                    } else {
                        deadSymbol = symbol;
                    }
                }
                IRSymbol resSymbol;
                if (liveSymbol != null) {
                    resSymbol = liveSymbol;
                } else if (deadSymbol != null) {
                    resSymbol = deadSymbol;
                } else {
                    resSymbol = labelManager.allocSymbol();
                }
                curNode.setChosenSymbol(resSymbol);
                IRElem nodeInst = new IRElem(curNode.getType(), resSymbol, sizeSymbol);
                instList.add(nodeInst); // 计算指令
                for (IRSymbol symbol : liveSymbols) { // 其它活跃变量赋值
                    if (symbol != resSymbol) {
                        nodeInst = new IRElem(IRElem.ASSIGN, symbol, resSymbol);
                        instList.add(nodeInst);
                    }
                } // 申请空间不可以合并
            } else if (curNode.getType() == IRElem.PRINTI ||
                    curNode.getType() == IRElem.PRINTS) {
                IRSymbol printContextSymbol = curNode.getLChild().getChosenSymbol();
                IRElem nodeInst = new IRElem(curNode.getType(), printContextSymbol);
                instList.add(nodeInst); // 指令
            } else if (curNode.getType() == IRElem.GETINT) {
                IRSymbol liveSymbol = null, deadSymbol = null;
                HashSet<IRSymbol> liveSymbols = new HashSet<>();
                for (IRSymbol symbol : curNode.getCorrespondingSymbols()) {
                    if (outSetLVA.contains(symbol)) {
                        liveSymbol = symbol;
                        liveSymbols.add(symbol);
                    } else {
                        deadSymbol = symbol;
                    }
                }
                IRSymbol resSymbol;
                if (liveSymbol != null) {
                    resSymbol = liveSymbol;
                } else if (deadSymbol != null) {
                    resSymbol = deadSymbol;
                } else {
                    resSymbol = labelManager.allocSymbol();
                }
                curNode.setChosenSymbol(resSymbol);
                IRElem nodeInst = new IRElem(curNode.getType(), resSymbol);
                instList.add(nodeInst); // 计算
                for (IRSymbol symbol : liveSymbols) { // 其它活跃变量赋值
                    if (symbol != resSymbol) {
                        nodeInst = new IRElem(IRElem.ASSIGN, symbol, resSymbol);
                        instList.add(nodeInst);
                    }
                }
            } else if (curNode.getType() == IRElem.CALL) {
                IRFuncSymbol funcSymbol = ((DAGCallNode) curNode).getFuncSymbol();
                IRSymbol liveSymbol = null, deadSymbol = null;
                HashSet<IRSymbol> liveSymbols = new HashSet<>();
                for (IRSymbol symbol : curNode.getCorrespondingSymbols()) {
                    if (outSetLVA.contains(symbol)) {
                        liveSymbol = symbol;
                        liveSymbols.add(symbol);
                    } else {
                        deadSymbol = symbol;
                    }
                }
                IRSymbol resSymbol;
                if (liveSymbol != null) {
                    resSymbol = liveSymbol;
                } else if (deadSymbol != null) {
                    resSymbol = deadSymbol;
                } else {
                    resSymbol = labelManager.allocSymbol();
                }
                curNode.setChosenSymbol(resSymbol);
                ArrayList<DAGNode> dependencyArr = curNode.getDependencyArr();
                ArrayList<IRSymbol> paramSymbol = new ArrayList<>();
                for (DAGNode node : dependencyArr) {
                    paramSymbol.add(node.getChosenSymbol());
                }
                IRElem callInst = new IRElem(IRElem.CALL, resSymbol, funcSymbol, paramSymbol);
                instList.add(callInst);
                IRElem nodeInst;
                for (IRSymbol symbol : liveSymbols) { // 其它活跃变量赋值
                    if (symbol != resSymbol) {
                        nodeInst = new IRElem(IRElem.ASSIGN, symbol, resSymbol);
                        instList.add(nodeInst);
                    }
                }
            }
        }
        DAGNode blockEndNode = dag.getBlockEndNode();
        if (blockEndNode != null) {
            IRSymbol addrSymbol = null;
            DAGNode lChild = blockEndNode.getLChild();
            if (lChild != null) {
                addrSymbol = lChild.getChosenSymbol();
            }
            IRSymbol judgeSymbol = null;
            DAGNode rChild = blockEndNode.getRChild();
            if (rChild != null) {
                judgeSymbol = rChild.getChosenSymbol();
            }
            IRElem nodeInst = new IRElem(blockEndNode.getType(), addrSymbol, judgeSymbol);
            instList.add(nodeInst); // 指令
        }
        for (IRElem label : labelSet) {
            instList.addFirst(label);
        }
        if (funcInst != null) {
            instList.addFirst(funcInst);
        }
        blockOptInstList = instList;
        return instList;
    }

    public LinkedList<IRElem> getBlockOptInstList() {
        return blockOptInstList;
    }

    public void depthFirstSearch(DAGNode node, HashSet<DAGNode> visited, LinkedList<DAGNode> calculateList) {
        if (node == null || visited.contains(node)) {
            return;
        }
        visited.add(node);
        if (node.getLChild() != null) {
            depthFirstSearch(node.getLChild(), visited, calculateList);
        }
        if (node.getRChild() != null) {
            depthFirstSearch(node.getRChild(), visited, calculateList);
        }
        ArrayList<DAGNode> dependency = node.getDependencyArr();
        for (DAGNode depNode : dependency) {
            if (depNode != null && !visited.contains(depNode)) {
                depthFirstSearch(depNode, visited, calculateList);
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

    public void setInSetDG(HashSet<DefUseNetElem> inSetDG) {
        this.inSetDG = inSetDG;
    }

    public void setOutSetDG(HashSet<DefUseNetElem> outSetDG) {
        this.outSetDG = outSetDG;
    }

    public HashSet<DefUseNetElem> getInSetDG() {
        return inSetDG;
    }

    public HashSet<DefUseNetElem> getOutSetDG() {
        return outSetDG;
    }

    public HashSet<DefUseNetElem> getGenSet() {
        return genSet;
    }

    public HashSet<DefUseNetElem> getKillSet() {
        return killSet;
    }

    public void setGenKillSet(HashSet<DefUseNetElem> genSet, HashSet<DefUseNetElem> killSet) {
        this.genSet = genSet;
        this.killSet = killSet;
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
