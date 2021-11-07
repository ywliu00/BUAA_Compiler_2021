package Symbols;

import Exceptions.SyntaxException;
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

    public static FuncSymbol getCallFuncSymbol(SyntaxClass unaryExp) throws SyntaxException {
        SymbolTable curEnv = unaryExp.getCurEnv();
        ArrayList<SyntaxClass> funcDefSonNodes = unaryExp.getSonNodeList();
        Token funcIdent = (Token) funcDefSonNodes.get(0);
        FuncSymbol funcSymbol = new FuncSymbol(funcIdent, true);

        SyntaxClass funcParams = funcDefSonNodes.get(2);
        if (funcParams.getSyntaxType() == SyntaxClass.FUNCRPARAMS) { // 若有实参
            ArrayList<SyntaxClass> funcRParamList = funcParams.getSonNodeList();
            for (int i = 0; i < funcRParamList.size(); i += 2) {
                SyntaxClass exp = funcRParamList.get(i);
//                System.out.println(exp);
//                System.out.println(exp.getLineNo());
                SyntaxClass addExp = exp.getSonNodeList().get(0);
                if (addExp.getSonNodeList().size() > 1) {
                    funcSymbol.addFormalParamType(0);
                    continue;
                }
                SyntaxClass mulExp = addExp.getSonNodeList().get(0);
                if (mulExp.getSonNodeList().size() > 1) {
                    funcSymbol.addFormalParamType(0);
                    continue;
                }
                SyntaxClass subUnaryExp = mulExp.getSonNodeList().get(0);
                SyntaxClass primaryExp = subUnaryExp.getSonNodeList().get(0);
                if (primaryExp.getSyntaxType() == SyntaxClass.UNARYOP) { // 式子
                    funcSymbol.addFormalParamType(0);
                    continue;
                } else if (primaryExp.getSyntaxType() == SyntaxClass.TOKEN) { // 函数
                    // 函数调用有效性应该已经在前面检查过了，这里只检查类型
                    //FuncSymbol tokenSymbol = curEnv.funcGlobalLookup(((Token) primaryExp).getTokenContext());
                    FuncSymbol tokenSymbol = (FuncSymbol) curEnv.globalLookup(
                            ((Token) primaryExp).getTokenContext(), 1);
                    if (!tokenSymbol.funcHasReturn()) {
                        funcSymbol.addFormalParamType(-1);
                    } else {
                        funcSymbol.addFormalParamType(0);
                    }
                    continue;
                }
                SyntaxClass lVal = primaryExp.getSonNodeList().get(0);
                if (lVal.getSyntaxType() != SyntaxClass.LVAL) { // (Exp)或数字
                    funcSymbol.addFormalParamType(0);
                    continue;
                }
                Token ident = (Token) lVal.getSonNodeList().get(0);
                //VarSymbol tokenSymbol = curEnv.varGlobalLookup(ident.getTokenContext());
                VarSymbol tokenSymbol = (VarSymbol) curEnv.globalLookup(ident.getTokenContext(), 0);
                if (tokenSymbol == null) {
                    throw new SyntaxException(ident.getLineNo());
                }
                int dimType = tokenSymbol.getDimType();
                int isVariable = tokenSymbol.isVar() ? 1 : 0;
                for (int j = 1; j < lVal.getSonNodeList().size(); ++j) {
                    if (lVal.getSonNodeList().get(j).getSyntaxType() == SyntaxClass.TOKEN) {
                        if (((Token) lVal.getSonNodeList().get(j)).getTokenType() == Token.LBRACK) { // 有一个方括号就减1
                            --dimType;
                        }
                    }
                }
                funcSymbol.addFormalParamType(dimType, isVariable);
            }
        }
        return funcSymbol;
    }

    public static FuncSymbol getFuncSymbol(SyntaxClass funcDef) {
        ArrayList<SyntaxClass> funcDefSonNodes = funcDef.getSonNodeList();
        SyntaxClass funcType = funcDefSonNodes.get(0);
        boolean hasReturn;
        hasReturn = ((Token) funcType.getSonNodeList().get(0)).getTokenType() == Token.INTTK;
        Token funcIdent = (Token) funcDefSonNodes.get(1);
        FuncSymbol funcSymbol = new FuncSymbol(funcIdent, hasReturn); // 函数符号建立

        SyntaxClass funcParams = funcDefSonNodes.get(3);
        if (funcParams.getSyntaxType() == SyntaxClass.FUNCFPARAMS) { // 若有形参
            ArrayList<SyntaxClass> funcFParamList = funcParams.getSonNodeList();
            for (int i = 0; i < funcFParamList.size(); i += 2) {
                SyntaxClass funcfParam = funcFParamList.get(i);
                ArrayList<SyntaxClass> fParamSonList = funcfParam.getSonNodeList();
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
