package Symbols;

import SyntaxClasses.Token;

public class Symbol {
    private Token token;
    private String name;
    private int type; // 0:常/变量Token，1:函数Token

    public Symbol(Token token, int type) {
        this.token = token;
        this.name = token.getTokenContext();
        this.type = type;
    }

    public int getlineNo() {
        return token.getLineNo();
    }

    public String getName() {
        return name;
    }
}
