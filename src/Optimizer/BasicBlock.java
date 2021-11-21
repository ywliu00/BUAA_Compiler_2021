package Optimizer;

import IR.IRElem;

import java.util.ArrayList;
import java.util.LinkedList;

public class BasicBlock {
    private ArrayList<BasicBlock> successors; // 后继
    private ArrayList<BasicBlock> predecessors; // 前驱
    private LinkedList<IRElem> iRList; // 基本块指令序列
    private int blockID;

    public BasicBlock(int id) {
        this.blockID = id;
        this.successors = new ArrayList<>();
        this.predecessors = new ArrayList<>();
        this.iRList = new LinkedList<>();
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
