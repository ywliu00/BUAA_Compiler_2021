package Optimizer;

import IR.IRElem;
import IR.IRSymbol;
import IR.IRTranslater;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

public class PrintOpt {

    public static void emptyStrOpt(IRTranslater iRPackage) {
        HashMap<IRSymbol, String> formatStrMap = iRPackage.getFormatStrMap();
        HashSet<IRSymbol> emptyStrSet = new HashSet<>();
        for (IRSymbol symbol : formatStrMap.keySet()) {
            if (formatStrMap.get(symbol).isEmpty()) {
                emptyStrSet.add(symbol);
            }
        } // 统计空串

        LinkedList<IRElem> iRList = iRPackage.getIRList();
        ArrayList<Integer> delList = new ArrayList<>();
        for (int i = 0; i < iRList.size(); i++) {
            IRElem inst = iRList.get(i);
            if (inst.getType() == IRElem.PRINTS) {
                if (emptyStrSet.contains(inst.getOp3())) {
                    delList.add(i);
                }
            }
        }
        for (int i = delList.size() - 1; i >= 0; i--) {
            iRList.remove((int) delList.get(i));
        }
    }
}
