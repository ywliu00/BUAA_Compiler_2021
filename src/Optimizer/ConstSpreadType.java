package Optimizer;

public class ConstSpreadType {
    public static final int CONST = 0, NAC = 1, UNDEF = 2;
    private int type;
    private int value;
    private static ConstSpreadType nacObj;
    private static ConstSpreadType undefObj;

    private ConstSpreadType(int type) {
        this.type = type;
    }

    public static ConstSpreadType getTypeObj(int type) {
        if (type == NAC) {
            if (nacObj == null) {
                nacObj = new ConstSpreadType(NAC);
            }
            return nacObj;
        } else if (type == UNDEF) {
            if (undefObj == null) {
                undefObj = new ConstSpreadType(UNDEF);
            }
            return undefObj;
        } else {
            return null;
        }
    }

    public static ConstSpreadType getConstTypeObj(int value) {
        ConstSpreadType obj = new ConstSpreadType(CONST);
        obj.setValue(value);
        return obj;
    }

    public void setValue(int value) {
        this.value = value;
    }

    public int getType() {
        return type;
    }

    public int getValue() {
        return value;
    }
}
