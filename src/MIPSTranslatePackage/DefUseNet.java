package MIPSTranslatePackage;

import java.util.HashSet;

public class DefUseNet {
    private HashSet<DefUseNetElem> netBody;
    private HashSet<DefUseNetElem> defPart;

    public DefUseNet() {
        netBody = new HashSet<>();
        defPart = new HashSet<>();
    }

    public void addDef(DefUseNetElem elem) {
        netBody.add(elem);
        defPart.add(elem);
    }

    public void add(DefUseNetElem elem) {
        netBody.add(elem);
    }

    public HashSet<DefUseNetElem> getDefPart() {
        return defPart;
    }

    public HashSet<DefUseNetElem> getNetBody() {
        return netBody;
    }

    public boolean contains(DefUseNetElem elem) {
        return netBody.contains(elem);
    }

    public void merge(DefUseNet newNet) {
        netBody.addAll(newNet.getNetBody());
        defPart.addAll(newNet.getDefPart());
    }
}
