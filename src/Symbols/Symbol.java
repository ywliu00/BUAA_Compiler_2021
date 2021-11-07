package Symbols;

import SyntaxClasses.Token;

public class Symbol {
    private Token token;
    private String name;
    private int type; // 0:常/变量Token，1:函数Token
    private int curRefID; // IR中的引用ID，-1为尚未引用

    public Symbol(Token token, int type) {
        this.token = token;
        this.name = token.getTokenContext();
        this.type = type;
        curRefID = -1;
    }

    public void setCurRefID(int refID) {
        curRefID = refID;
    }

    public int getCurRefID() {
        return curRefID;
    }

    public int getlineNo() {
        return token.getLineNo();
    }

    public String getName() {
        return name;
    }
}
