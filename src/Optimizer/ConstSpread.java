package Optimizer;

import IR.IRElem;
import IR.IRImmSymbol;
import IR.IRSymbol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

public class ConstSpread {
    private ArrayList<BasicBlock> blockList;
    private HashMap<BasicBlock, HashMap<IRSymbol, ConstSpreadType>> blockInMap;
    private HashMap<BasicBlock, HashMap<IRSymbol, ConstSpreadType>> blockOutMap;

    public ConstSpread(ArrayList<BasicBlock> blockList) {
        this.blockList = blockList;
        blockInMap = new HashMap<>();
        blockOutMap = new HashMap<>();
    }

    public void doConstSpread() {
        boolean changed = true;
        int cnt = 0;
        while (changed) {
            //System.out.println("你在演我" + Integer.toString(cnt++));
            changed = false;
            for (BasicBlock block : blockList) {
                /*if (cnt == 39 && block.getBlockID() == 13) {
                    System.out.println("GET1");
                }*/
                HashMap<IRSymbol, ConstSpreadType> inMap = new HashMap();
                for (BasicBlock predBlock : block.getPredecessors()) {
                    HashMap<IRSymbol, ConstSpreadType> outMap = blockOutMap.get(predBlock);
                    if (outMap == null) {
                        continue;
                    }
                    for (IRSymbol symbol : outMap.keySet()) {
                        ConstSpreadType currentCST = inMap.getOrDefault(symbol, null);
                        if (currentCST == null || currentCST.getType() == ConstSpreadType.UNDEF) {
                            inMap.put(symbol, outMap.get(symbol));
                        } else if (currentCST.getType() == ConstSpreadType.NAC ||
                                outMap.get(symbol).getType() == ConstSpreadType.NAC) {
                            inMap.put(symbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else { // currentCST.getType() == CONST && out != NAC
                            if (outMap.get(symbol).getType() == ConstSpreadType.CONST &&
                                    (outMap.get(symbol).getValue() != currentCST.getValue())) {
                                inMap.put(symbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                                // 两个常数值不同
                            } else {
                                inMap.put(symbol, currentCST);
                            }
                        }
                    }
                }
                blockInMap.put(block, inMap);
                boolean blockRes = blockSpreadCal(block);
                changed = changed || blockRes;
                //if (blockRes) System.out.println(block.getBlockID());
            }
        }
        for (BasicBlock block : blockList) {
            renewBlockIR(block);
        }
    }

    public void renewBlockIR(BasicBlock block) {
        LinkedList<IRElem> blockInstList = block.getBlockIRList();
        HashMap<IRSymbol, ConstSpreadType> blockOut = blockOutMap.get(block);
        for (IRElem inst : blockInstList) {
            if (inst.getType() == IRElem.ADD || inst.getType() == IRElem.MINU ||
                    inst.getType() == IRElem.MULT || inst.getType() == IRElem.DIV ||
                    inst.getType() == IRElem.MOD || inst.getType() == IRElem.LSHIFT ||
                    inst.getType() == IRElem.RSHIFT || inst.getType() == IRElem.GRE ||
                    inst.getType() == IRElem.GEQ || inst.getType() == IRElem.LSS ||
                    inst.getType() == IRElem.LEQ || inst.getType() == IRElem.EQL ||
                    inst.getType() == IRElem.NEQ) {
                IRSymbol op2 = inst.getOp2();
                IRSymbol op1 = inst.getOp1();
                ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                if (op1Type != null && op1Type.getType() == ConstSpreadType.CONST) {
                    inst.setOp1(new IRImmSymbol(op1Type.getValue()));
                }
                if (op2Type != null && op2Type.getType() == ConstSpreadType.CONST) {
                    inst.setOp2(new IRImmSymbol(op2Type.getValue()));
                }
            } else if (inst.getType() == IRElem.BZ || inst.getType() == IRElem.BNZ ||
                    inst.getType() == IRElem.ASSIGN) {
                IRSymbol op1 = inst.getOp1();
                ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                if (op1Type != null && op1Type.getType() == ConstSpreadType.CONST) {
                    inst.setOp1(new IRImmSymbol(op1Type.getValue()));
                }
            } else if (inst.getType() == IRElem.PRINTI || inst.getType() == IRElem.SETRET) {
                IRSymbol op3 = inst.getOp3();
                ConstSpreadType op3Type = blockOut.getOrDefault(op3, null);
                if (op3Type != null && op3Type.getType() == ConstSpreadType.CONST) {
                    inst.setOp3(new IRImmSymbol(op3Type.getValue()));
                }
            } else if (inst.getType() == IRElem.LOAD) {
                IRSymbol op2 = inst.getOp2();
                ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                if (op2Type != null && op2Type.getType() == ConstSpreadType.CONST) {
                    inst.setOp2(new IRImmSymbol(op2Type.getValue()));
                }
            } else if (inst.getType() == IRElem.STORE) {
                IRSymbol op3 = inst.getOp3();
                ConstSpreadType op3Type = blockOut.getOrDefault(op3, null);
                if (op3Type != null && op3Type.getType() == ConstSpreadType.CONST) {
                    inst.setOp3(new IRImmSymbol(op3Type.getValue()));
                }
                IRSymbol op2 = inst.getOp2();
                ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                if (op2Type != null && op2Type.getType() == ConstSpreadType.CONST) {
                    inst.setOp2(new IRImmSymbol(op2Type.getValue()));
                }
            } else if (inst.getType() == IRElem.CALL) {
                ArrayList<IRSymbol> rParams = inst.getSymbolList();
                for (int i = 0; i < rParams.size(); ++i) {
                    IRSymbol symbol = rParams.get(i);
                    ConstSpreadType symbolType = blockOut.getOrDefault(symbol, null);
                    if (symbolType != null && symbolType.getType() == ConstSpreadType.CONST) {
                        rParams.set(i, new IRImmSymbol(symbolType.getValue()));
                    }
                }
            }
        }
    }

    public boolean blockSpreadCal(BasicBlock block) {
        LinkedList<IRElem> blockInstList = block.getBlockIRList();
        HashMap<IRSymbol, ConstSpreadType> blockOut = new HashMap<>();
        HashMap<IRSymbol, ConstSpreadType> blockIn = blockInMap.get(block);
        HashMap<IRSymbol, ConstSpreadType> blockOutLast = blockOutMap.get(block);
        boolean changed = false;
        if (blockOutLast == null) {
            blockOutLast = new HashMap<>();
        }
        HashSet<IRSymbol> keySet = new HashSet<>(blockIn.keySet());
        keySet.addAll(blockOutLast.keySet());
        for (IRSymbol symbol : keySet) {
            ConstSpreadType oldType = blockOutLast.getOrDefault(symbol, null);
            ConstSpreadType newType = blockIn.get(symbol);
            if (oldType == null) {
                changed = true;
                blockOut.put(symbol, newType);
            } else if (newType == null) {
                blockOut.put(symbol, oldType);
            } else if (oldType.getType() != newType.getType()) {
                if (oldType.getType() == ConstSpreadType.NAC) {
                    blockOut.put(symbol, oldType);
                } else if (newType.getType() == ConstSpreadType.NAC) {
                    changed = true;
                    blockOut.put(symbol, newType);
                } else if (oldType.getType() == ConstSpreadType.UNDEF) {
                    changed = true; // 新的一定是CONST
                    blockOut.put(symbol, newType);
                } else if (oldType.getType() == ConstSpreadType.CONST) {
                    blockOut.put(symbol, oldType);
                }
            } else { // Type一致
                if (oldType.getType() == ConstSpreadType.CONST) {
                    if (oldType.getValue() != newType.getValue()) {
                        changed = true;
                        blockOut.put(symbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                    } else {
                        blockOut.put(symbol, oldType);
                    }
                } else {
                    blockOut.put(symbol, oldType);
                }
            }
        }
        for (IRElem inst : blockInstList) {
            if (inst.getType() == IRElem.ADD || inst.getType() == IRElem.MINU ||
                    inst.getType() == IRElem.MULT || inst.getType() == IRElem.DIV ||
                    inst.getType() == IRElem.MOD || inst.getType() == IRElem.LSHIFT ||
                    inst.getType() == IRElem.RSHIFT || inst.getType() == IRElem.GRE ||
                    inst.getType() == IRElem.GEQ || inst.getType() == IRElem.LSS ||
                    inst.getType() == IRElem.LEQ || inst.getType() == IRElem.EQL ||
                    inst.getType() == IRElem.NEQ || inst.getType() == IRElem.ASSIGN ||
                    inst.getType() == IRElem.GETINT) {
                IRSymbol destSymbol = inst.getOp3();
                ConstSpreadType destType = blockOut.getOrDefault(destSymbol, null);
                if (destType == null) {
                    changed = true;
                    blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                    destType = blockOut.get(destSymbol);
                }
                if (destType.getType() == ConstSpreadType.NAC) {
                    continue;
                } else if (destType.getType() == ConstSpreadType.CONST) {
                    int value = destType.getValue();
                    IRSymbol op1, op2;
                    if (inst.getType() == IRElem.ADD) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type == null || op2Type == null) {
                            if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                            if (value != op1Type.getValue() + op2Type.getValue()) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else { // 有undef
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        }
                    } else if (inst.getType() == IRElem.MINU) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type == null || op2Type == null) {
                            if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                            if (value != op1Type.getValue() - op2Type.getValue()) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else { // 有undef
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        }
                    } else if (inst.getType() == IRElem.MULT) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type == null || op2Type == null) {
                            if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                            if (value != op1Type.getValue() * op2Type.getValue()) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else { // 有undef
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        }
                    } else if (inst.getType() == IRElem.DIV) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type == null || op2Type == null) {
                            if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                            if (value != op1Type.getValue() / op2Type.getValue()) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else { // 有undef
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        }
                    } else if (inst.getType() == IRElem.MOD) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type == null || op2Type == null) {
                            if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                            if (value != op1Type.getValue() % op2Type.getValue()) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else { // 有undef
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        }
                    } else if (inst.getType() == IRElem.LSHIFT) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type == null || op2Type == null) {
                            if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                            if (value != op1Type.getValue() << op2Type.getValue()) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else { // 有undef
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        }
                    } else if (inst.getType() == IRElem.RSHIFT) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type == null || op2Type == null) {
                            if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                            if (value != op1Type.getValue() >> op2Type.getValue()) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else { // 有undef
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        }
                    } else if (inst.getType() == IRElem.GRE) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type == null || op2Type == null) {
                            if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                            int expected;
                            if (op1Type.getValue() > op2Type.getValue()) {
                                expected = 1;
                            } else {
                                expected = 0;
                            }
                            if (value != expected) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else { // 有undef
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        }
                    } else if (inst.getType() == IRElem.GEQ) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type == null || op2Type == null) {
                            if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                            int expected;
                            if (op1Type.getValue() >= op2Type.getValue()) {
                                expected = 1;
                            } else {
                                expected = 0;
                            }
                            if (value != expected) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else { // 有undef
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        }
                    } else if (inst.getType() == IRElem.LSS) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type == null || op2Type == null) {
                            if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                            int expected;
                            if (op1Type.getValue() < op2Type.getValue()) {
                                expected = 1;
                            } else {
                                expected = 0;
                            }
                            if (value != expected) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else { // 有undef
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        }
                    } else if (inst.getType() == IRElem.LEQ) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type == null || op2Type == null) {
                            if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                            int expected;
                            if (op1Type.getValue() <= op2Type.getValue()) {
                                expected = 1;
                            } else {
                                expected = 0;
                            }
                            if (value != expected) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else { // 有undef
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        }
                    } else if (inst.getType() == IRElem.EQL) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type == null || op2Type == null) {
                            if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                            int expected;
                            if (op1Type.getValue() == op2Type.getValue()) {
                                expected = 1;
                            } else {
                                expected = 0;
                            }
                            if (value != expected) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else { // 有undef
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        }
                    } else if (inst.getType() == IRElem.NEQ) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type == null || op2Type == null) {
                            if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            } else {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                            int expected;
                            if (op1Type.getValue() != op2Type.getValue()) {
                                expected = 1;
                            } else {
                                expected = 0;
                            }
                            if (value != expected) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else { // 有undef
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        }
                    } else if (inst.getType() == IRElem.ASSIGN) {
                        op1 = inst.getOp1();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        if (op1Type == null) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        } else if (op1Type.getType() == ConstSpreadType.CONST) {
                            if (value != op1Type.getValue()) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op1Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else { // 有undef
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        }
                    } else if (inst.getType() == IRElem.GETINT) {
                        changed = true;
                        blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                    }
                } else if (destType.getType() == ConstSpreadType.UNDEF) {
                    IRSymbol op1, op2;
                    if (inst.getType() == IRElem.ADD) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type != null && op2Type != null) {
                            if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                                changed = true;
                                destType = ConstSpreadType.getConstTypeObj(op1Type.getValue() + op2Type.getValue());
                                blockOut.put(destSymbol, destType);
                            } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        }
                    } else if (inst.getType() == IRElem.MINU) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type != null && op2Type != null) {
                            if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                                changed = true;
                                destType = ConstSpreadType.getConstTypeObj(op1Type.getValue() - op2Type.getValue());
                                blockOut.put(destSymbol, destType);
                            } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        }
                    } else if (inst.getType() == IRElem.MULT) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type != null && op2Type != null) {
                            if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                                changed = true;
                                destType = ConstSpreadType.getConstTypeObj(op1Type.getValue() * op2Type.getValue());
                                blockOut.put(destSymbol, destType);
                            } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        }
                    } else if (inst.getType() == IRElem.DIV) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type != null && op2Type != null) {
                            if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                                changed = true;
                                destType = ConstSpreadType.getConstTypeObj(op1Type.getValue() / op2Type.getValue());
                                blockOut.put(destSymbol, destType);
                            } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        }
                    } else if (inst.getType() == IRElem.MOD) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type != null && op2Type != null) {
                            if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                                changed = true;
                                destType = ConstSpreadType.getConstTypeObj(op1Type.getValue() % op2Type.getValue());
                                blockOut.put(destSymbol, destType);
                            } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        }
                    } else if (inst.getType() == IRElem.LSHIFT) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type != null && op2Type != null) {
                            if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                                changed = true;
                                destType = ConstSpreadType.getConstTypeObj(op1Type.getValue() << op2Type.getValue());
                                blockOut.put(destSymbol, destType);
                            } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        }
                    } else if (inst.getType() == IRElem.RSHIFT) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type != null && op2Type != null) {
                            if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                                changed = true;
                                destType = ConstSpreadType.getConstTypeObj(op1Type.getValue() >> op2Type.getValue());
                                blockOut.put(destSymbol, destType);
                            } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        }
                    } else if (inst.getType() == IRElem.GRE) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type != null && op2Type != null) {
                            if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                                int expected;
                                if (op1Type.getValue() > op2Type.getValue()) {
                                    expected = 1;
                                } else {
                                    expected = 0;
                                }
                                changed = true;
                                destType = ConstSpreadType.getConstTypeObj(expected);
                                blockOut.put(destSymbol, destType);
                            } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        }
                    } else if (inst.getType() == IRElem.GEQ) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type != null && op2Type != null) {
                            if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                                int expected;
                                if (op1Type.getValue() >= op2Type.getValue()) {
                                    expected = 1;
                                } else {
                                    expected = 0;
                                }
                                changed = true;
                                destType = ConstSpreadType.getConstTypeObj(expected);
                                blockOut.put(destSymbol, destType);
                            } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        }
                    } else if (inst.getType() == IRElem.LSS) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type != null && op2Type != null) {
                            if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                                int expected;
                                if (op1Type.getValue() < op2Type.getValue()) {
                                    expected = 1;
                                } else {
                                    expected = 0;
                                }
                                changed = true;
                                destType = ConstSpreadType.getConstTypeObj(expected);
                                blockOut.put(destSymbol, destType);
                            } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        }
                    } else if (inst.getType() == IRElem.LEQ) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type != null && op2Type != null) {
                            if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                                int expected;
                                if (op1Type.getValue() <= op2Type.getValue()) {
                                    expected = 1;
                                } else {
                                    expected = 0;
                                }
                                changed = true;
                                destType = ConstSpreadType.getConstTypeObj(expected);
                                blockOut.put(destSymbol, destType);
                            } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        }
                    } else if (inst.getType() == IRElem.EQL) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type != null && op2Type != null) {
                            if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                                int expected;
                                if (op1Type.getValue() == op2Type.getValue()) {
                                    expected = 1;
                                } else {
                                    expected = 0;
                                }
                                changed = true;
                                destType = ConstSpreadType.getConstTypeObj(expected);
                                blockOut.put(destSymbol, destType);
                            } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        }
                    } else if (inst.getType() == IRElem.NEQ) {
                        op1 = inst.getOp1();
                        op2 = inst.getOp2();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        ConstSpreadType op2Type = blockOut.getOrDefault(op2, null);
                        if (op2 instanceof IRImmSymbol) {
                            op2Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op2).getValue());
                        }
                        if (op1Type != null && op2Type != null) {
                            if (op1Type.getType() == ConstSpreadType.CONST && op2Type.getType() == ConstSpreadType.CONST) {
                                int expected;
                                if (op1Type.getValue() != op2Type.getValue()) {
                                    expected = 1;
                                } else {
                                    expected = 0;
                                }
                                changed = true;
                                destType = ConstSpreadType.getConstTypeObj(expected);
                                blockOut.put(destSymbol, destType);
                            } else if (op1Type.getType() == ConstSpreadType.NAC || op2Type.getType() == ConstSpreadType.NAC) {
                                changed = true;
                                blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                            }
                        } else if (op2Type != null && op2Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        } else if (op1Type != null && op1Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        }
                    } else if (inst.getType() == IRElem.ASSIGN) {
                        op1 = inst.getOp1();
                        ConstSpreadType op1Type = blockOut.getOrDefault(op1, null);
                        if (op1 instanceof IRImmSymbol) {
                            op1Type = ConstSpreadType.getConstTypeObj(((IRImmSymbol) op1).getValue());
                        }
                        if (op1Type == null) {
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.UNDEF));
                        } else if (op1Type.getType() == ConstSpreadType.CONST) {
                            changed = true;
                            destType = ConstSpreadType.getConstTypeObj(op1Type.getValue());
                            blockOut.put(destSymbol, destType);
                        } else if (op1Type.getType() == ConstSpreadType.NAC) {
                            changed = true;
                            blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                        }
                    } else if (inst.getType() == IRElem.GETINT) {
                        changed = true;
                        blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                    }
                }
            } else if (inst.getType() == IRElem.LOAD || inst.getType() == IRElem.CALL) {
                IRSymbol destSymbol = inst.getOp3();
                ConstSpreadType destType = blockOut.getOrDefault(destSymbol, null);
                if (destType == null) {
                    changed = true;
                    blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                } else if (destType.getType() != ConstSpreadType.NAC) {
                    changed = true;
                    blockOut.put(destSymbol, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                }
            } else if (inst.getType() == IRElem.FUNC) {
                ArrayList<IRSymbol> fParams = inst.getSymbolList();
                for (IRSymbol fParam : fParams) {
                    ConstSpreadType fParamType = blockOut.getOrDefault(fParam, null);
                    if (fParamType == null) {
                        changed = true;
                        blockOut.put(fParam, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                    } else if (fParamType.getType() != ConstSpreadType.NAC) {
                        changed = true;
                        blockOut.put(fParam, ConstSpreadType.getTypeObj(ConstSpreadType.NAC));
                    }
                }
            }
        }
        blockOutMap.put(block, blockOut);
        return changed;
    }
}
