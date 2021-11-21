package Optimizer;

import IR.IRElem;
import IR.IRLabelSymbol;

import java.util.LinkedList;

public class JumpOpt {
    private LinkedList<IRElem> iRList;

    public JumpOpt(LinkedList<IRElem> irList) {
        iRList = irList;
    }

    public LinkedList<IRElem> doJumpOpt() {
        IRElem first = null;
        IRElem next = null;
        int firstPos = 0, nextPos = 1;
        while (firstPos < iRList.size() - 1) {
            nextPos = firstPos + 1;
            first = iRList.get(firstPos);
            if (first.getType() == IRElem.BR || first.getType() == IRElem.BZ || first.getType() == IRElem.BNZ) {
                boolean removed = false;
                IRLabelSymbol jumpLabel = (IRLabelSymbol) first.getOp3();
                next = iRList.get(nextPos);
                while (next.getType() == IRElem.LABEL) {
                    IRLabelSymbol nextLabel = (IRLabelSymbol) next.getOp3();
                    if (jumpLabel.getId() == nextLabel.getId()) { // 发现跳转到下一条的情况
                        iRList.remove(firstPos); // 删除该条跳转语句
                        removed = true;
                        break;
                    } else {
                        ++nextPos;
                        if (nextPos >= iRList.size()) {
                            break;
                        }
                        next = iRList.get(nextPos);
                    }
                }
                if (removed) {
                    firstPos -=1; // 若删除过，则前移一位
                } else {
                    firstPos=nextPos; // 否则，nextPos或者指向下一个非Label语句，或者已经越界
                }
            } else {
                firstPos+=1;
            }
        }
        return iRList;
    }
}
