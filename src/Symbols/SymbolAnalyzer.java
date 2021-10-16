package Symbols;

import SyntaxClasses.SyntaxClass;
import SyntaxClasses.Token;

import java.util.ArrayList;
import java.util.LinkedList;

public class SymbolAnalyzer {
//    public static ArrayList<Symbol> getSymbolsFromDecl(SyntaxClass decl) {
//        ArrayList<Symbol> symbolList;
//        SyntaxClass subDecl = decl.getSonNodeList().get(0);
//        if (subDecl.getSyntaxType() == SyntaxClass.CONSTDECL) {
//            symbolList = getSymbolsFromConstDecl(subDecl);
//        } else {
//
//        }
//    }
//
//    public static ArrayList<Symbol> getSymbolsFromConstDecl(SyntaxClass constDecl) {
//
//    }

    public static FuncSymbol getFuncSymbol(SyntaxClass funcDef) {
        LinkedList<SyntaxClass> funcDefSonNodes = funcDef.getSonNodeList();
        SyntaxClass funcType = funcDefSonNodes.get(0);
        boolean hasReturn;
        hasReturn = ((Token) funcType.getSonNodeList().get(0)).getTokenType() == Token.INTTK;
        Token funcIdent = (Token) funcDefSonNodes.get(1);
        FuncSymbol funcSymbol = new FuncSymbol(funcIdent, hasReturn); // 函数符号建立

        SyntaxClass funcParams = funcDefSonNodes.get(3);
        if (funcParams.getSyntaxType() == SyntaxClass.FUNCFPARAMS) { // 若有形参
            LinkedList<SyntaxClass> funcFParamList = funcParams.getSonNodeList();
            for (int i = 0; i < funcFParamList.size(); i += 2) {
                SyntaxClass funcfParam = funcFParamList.get(i);
                LinkedList<SyntaxClass> fParamSonList = funcfParam.getSonNodeList();
                Token fParamToken = (Token) fParamSonList.get(1);
                int brackNum = 0, j = 2;
                for (; j < fParamSonList.size(); ++j) {
                    if (fParamSonList.get(j).getSyntaxType() == SyntaxClass.TOKEN) {
                        if (((Token) fParamSonList.get(j)).getTokenType() == Token.LBRACK) {
                            ++brackNum;
                        }
                    }
                }
                funcSymbol.addFormalParamType(brackNum); // 添加形参类型
            }
        }

        return funcSymbol;
    }
}
