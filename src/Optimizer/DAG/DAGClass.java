package Optimizer.DAG;

import IR.IRElem;
import IR.IRFuncSymbol;
import IR.IRSymbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

public class DAGClass {
    public static final int DAGLEAF = 100;
    private HashMap<IRSymbol, DAGNode> symbolMap;
    private ArrayList<DAGNode> allNodeList;
    private int nextId;

    private DAGNode setRetNode;
    private DAGNode blockEndNode;
    private LinkedList<DAGNode> ioList; // （可能的）IO操作列表，不可打乱
    private ArrayList<DAGNode> memList; // （可能的）内存操作列表，不可打乱
    private HashSet<IRElem> labelSet;

    public DAGClass() {
        this.symbolMap = new HashMap<>();
        this.allNodeList = new ArrayList<>();
        this.nextId = 0;

        this.setRetNode = null;
        this.blockEndNode = null;
        ioList = new LinkedList<>();
        memList = new ArrayList<>();
        labelSet = new HashSet<>();
    }

    public void setSymbolMapRelation(IRSymbol symbol, DAGNode node) {
        DAGNode formerNode = symbolMap.getOrDefault(symbol, null);
        if (formerNode == node) {
            return;
        }
        if (formerNode != null && formerNode.getType() != DAGClass.DAGLEAF) {
            // LEAF节点就不改了，保证找的时候能找到就行
            // TODO: 或者还是改一下？申请一个新符号来保存原始值
            formerNode.removeSymbol(symbol);
        }
        symbolMap.put(symbol, node);
    }

    public DAGNode getBlockEndNode() {
        return blockEndNode;
    }

    public DAGNode getSetRetNode() {
        return setRetNode;
    }

    public ArrayList<DAGNode> getMemList() {
        return memList;
    }

    public LinkedList<DAGNode> getIoList() {
        return ioList;
    }

    public void addLabelInst(IRElem labelInst) {
        labelSet.add(labelInst);
    }

    public void addReadMemNode(IRSymbol symbol, int type, DAGNode base, DAGNode offset) {
        DAGNode dependency = null;
        for (int i = memList.size(); i >= 0; --i) {
            if (memList.get(i).getType() != IRElem.LOAD) {
                //readMemNode.setThirdChild(memList.get(i));
                dependency = memList.get(i); // 设置上一次修改内存为其dependency
                break;
            }
        } // 先确定建立节点所需信息
        boolean found = false;
        for (DAGNode node : allNodeList) {
            if (node.getType() == type) {
                if (node.getLChild() == base && node.getRChild() == offset &&
                        node.getDependency1() == dependency) { // 需要所有依赖均相同！！
                    setSymbolMapRelation(symbol, node); // 找到匹配的公共表达式
                    found = true;
                    break;
                }
            }
        } // 然后检查是否可以匹配之前已经存在的节点
        if (!found) { // 若不能匹配，再新建节点
            DAGNode readMemNode = new DAGNode(nextId++, type);
            readMemNode.setLChild(base);
            readMemNode.setRChild(offset);
            readMemNode.setDependency1(dependency);
            allNodeList.add(readMemNode);
            setSymbolMapRelation(symbol, readMemNode);
            memList.add(readMemNode); // 内存访问列表中加入本次访问
        }
    }

    public void addWriteMemNode(int type, DAGNode value, DAGNode base, DAGNode offset) {
        DAGNode dependency = null;
        if (memList.size() > 0) {
            dependency = memList.get(memList.size() - 1); // 设置上一次访问内存为其dependency
        }
        // 先确定建立节点所需信息
        DAGNode writeMemNode = new DAGNode(nextId++, type);
        writeMemNode.setLChild(value);
        writeMemNode.setRChild(base);
        writeMemNode.setDependency1(offset);
        allNodeList.add(writeMemNode);
        if (dependency != null) {
            // 连续对同一位置写入，中间没有可能的读取，则可以去掉上次写入
            if (dependency.getType() == IRElem.STORE) {
                if (dependency.getRChild() == base &&
                        dependency.getDependency1() == offset) { // 删去应被覆盖的写入
                    memList.remove(memList.size() - 1); // 在内存访问表中将冗余写入扔掉
                    allNodeList.remove(dependency); // 丢弃冗余写入
                    dependency = dependency.getDependency2(); // 当前写入的依赖更新
                }
            }
        }
        writeMemNode.setDependency2(dependency); // 设置当前写入依赖
        memList.add(writeMemNode);
    }

    public void addCallNode(int type, IRSymbol symbol, IRFuncSymbol funcSymbol,
                            ArrayList<DAGNode> paramList) {
        DAGNode callNode = new DAGCallNode(nextId++, type, funcSymbol);
        allNodeList.add(callNode);
        setSymbolMapRelation(symbol, callNode);  // 返回值映射
        callNode.setDependencyArr(paramList);
        if (ioList.size() > 0) {
            callNode.setLChild(ioList.getLast());
        }
        if (memList.size() > 0) {
            callNode.setRChild(memList.get(memList.size() - 1));
        }
        ioList.add(callNode);
        memList.add(callNode); // 保证IO和内存操作顺序
    }

    public void addOutputNode(int type, DAGNode lChild) {
        DAGNode outNode = new DAGNode(nextId++, type);
        allNodeList.add(outNode);
        outNode.setLChild(lChild);
        if (!ioList.isEmpty()) {
            outNode.setRChild(ioList.getLast()); // 设置前一个IO语句为其依赖
        }
        ioList.add(outNode);
    }

    public void addInputNode(IRSymbol symbol, int type) {
        DAGNode inputNode = new DAGNode(nextId++, type);
        allNodeList.add(inputNode);
        setSymbolMapRelation(symbol, inputNode);
        if (!ioList.isEmpty()) {
            inputNode.setRChild(ioList.getLast()); // 设置前一个IO语句为其依赖
        }
        ioList.add(inputNode);
    }

    public DAGNode getNodeOrCreateLeafNode(IRSymbol symbol) {
        DAGNode node = symbolMap.getOrDefault(symbol, null);
        if (node == null) {
            node = new DAGNode(nextId++, DAGClass.DAGLEAF);
            setSymbolMapRelation(symbol, node);
            allNodeList.add(node);
        }
        return node;
    }

    public DAGNode getNode(IRSymbol symbol) {
        return symbolMap.getOrDefault(symbol, null);
    }

    public void addBlockEndNode(int type, DAGNode lChild, DAGNode rChild) {
        this.blockEndNode = new DAGNode(nextId++, type);
        allNodeList.add(blockEndNode); // 不往symbolMap里加了，但allNodeList还是算进去
        this.blockEndNode.setLChild(lChild);
        this.blockEndNode.setRChild(rChild);
    }

    public void addSetRetNode(DAGNode retValue) { // 将某节点设置为setret语句所用值
        if (this.setRetNode == null) {
            this.setRetNode = new DAGNode(nextId++, IRElem.SETRET);
            // 不往symbolMap里加了，但allNodeList还是算进去
            this.allNodeList.add(setRetNode);
        }
        this.setRetNode.setLChild(retValue); // 一个基本块只有最后一句setret有效，因此可以任意覆盖
    }

    public void addNodeAttr(IRSymbol symbol, DAGNode node) {
        setSymbolMapRelation(symbol, node);
    }

    public DAGNode forcedAddMidNode(IRSymbol symbol, int type, DAGNode lChild,
                                    DAGNode rChild) { // 强制加入节点，不检测公共表达式
        DAGNode resNode = new DAGNode(nextId++, type);
        resNode.setLChild(lChild);
        resNode.setRChild(rChild);
        setSymbolMapRelation(symbol, resNode); // 新建公共表达式
        allNodeList.add(resNode);
        return resNode;
    }

    public DAGNode addMidNode(IRSymbol symbol, int type, DAGNode lChild,
                              DAGNode rChild, boolean swap) {
        boolean found = false;
        DAGNode resNode = null;
        for (DAGNode node : allNodeList) {
            if (node.getType() == type) {
                if ((node.getLChild() == lChild && node.getRChild() == rChild) ||
                        (swap && node.getLChild() == rChild && node.getRChild() == lChild)) {
                    setSymbolMapRelation(symbol, node); // 找到匹配的公共表达式
                    resNode = node;
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            resNode = new DAGNode(nextId++, type);
            resNode.setLChild(lChild);
            resNode.setRChild(rChild);
            setSymbolMapRelation(symbol, resNode); // 新建公共表达式
            allNodeList.add(resNode);
        }
        return resNode;
    }

    public DAGNode addMidNode(IRSymbol symbol, int type, DAGNode lChild,
                              DAGNode rChild, DAGNode tChild) {
        boolean found = false;
        DAGNode resNode = null;
        for (DAGNode node : allNodeList) {
            if (node.getType() == type) {
                if (node.getLChild() == lChild && node.getRChild() == rChild
                        && node.getDependency1() == tChild) {
                    setSymbolMapRelation(symbol, node); // 找到匹配的公共表达式
                    resNode = node;
                    found = true;
                    break;
                }
            }
        }
        if (!found) {
            resNode = new DAGNode(nextId++, type);
            resNode.setLChild(lChild);
            resNode.setRChild(rChild);
            resNode.setDependency1(tChild);
            setSymbolMapRelation(symbol, resNode); // 新建公共表达式
            allNodeList.add(resNode);
        }
        return resNode;
    }
}
