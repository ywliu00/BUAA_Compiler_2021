package Symbols;

import SyntaxClasses.SyntaxClass;

import java.util.ArrayList;

public class SymbolAnalyzer {
    public static ArrayList<Symbol> getSymbolsFromDecl(SyntaxClass decl) {
        ArrayList<Symbol> symbolList;
        SyntaxClass subDecl = decl.getSonNodeList().get(0);
        if (subDecl.getSyntaxType() == SyntaxClass.CONSTDECL) {
            symbolList = getSymbolsFromConstDecl(subDecl);
        } else {

        }
    }

    public static ArrayList<Symbol> getSymbolsFromConstDecl(SyntaxClass constDecl) {

    }

    public static ArrayList<Symbol>
}
