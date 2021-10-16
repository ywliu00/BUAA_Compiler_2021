import Exceptions.DuplicatedDefineIdentException;
import Exceptions.SemicnMissingException;
import Exceptions.SyntaxException;
import Symbols.FuncSymbol;
import Symbols.Symbol;
import Symbols.SymbolAnalyzer;
import Symbols.SymbolTable;
import Symbols.VarSymbol;
import SyntaxClasses.SyntaxClass;
import SyntaxClasses.Token;

import java.util.ArrayList;
import java.util.LinkedList;

public class SyntaxAnalyzer {
    private ArrayList<Token> tokenList;
    private SyntaxClass globalCompUnit;
    private int pos;
    private ArrayList<Error> errorList;

    public SyntaxAnalyzer() {
        this.globalCompUnit = null;
        this.pos = 0;
        errorList = new ArrayList<>();
    }

    public void setTokenList(LinkedList<Token> tokenList) {
        this.tokenList = new ArrayList<>();
        this.tokenList.addAll(tokenList);
    }

    public void setErrorList(ArrayList<Error> errorList) {
        this.errorList = errorList;
    }

    public int getPos() {
        return pos;
    }

    public SyntaxClass getGlobalCompUnit() {
        return globalCompUnit;
    }

    public void syntaxAnalyze() throws SyntaxException {
        globalCompUnit = readCompUnit();
    }

    public SyntaxClass readCompUnit() throws SyntaxException {
        SyntaxClass compUnit = new SyntaxClass(SyntaxClass.COMPUNIT);
        SymbolTable globalSymbolTable = new SymbolTable();
        compUnit.setCurEnv(globalSymbolTable); // 创建并设置全局符号表
        if (pos >= tokenList.size()) {
            return null;
        }
        // 检查是否有Decl成分，如果没有，弹出错误，就break
        SyntaxClass decl = null;
        int startPos = pos;
        while (true) {
            try {
                startPos = pos;
                decl = readDecl(globalSymbolTable);
            } catch (SyntaxException e) {
                pos = startPos;
                break;
            }
            if (decl == null) break;
            compUnit.appendSonNode(decl);
        }
        // 检查是否有FuncDef成分，如果没有，弹出错误，就break
        SyntaxClass funcDef = null;
        while (true) {
            try {
                startPos = pos;
                funcDef = readFuncDef(globalSymbolTable);
            } catch (SyntaxException e) {
                pos = startPos;
                break;
            }
            if (funcDef == null) break;
            compUnit.appendSonNode(funcDef);
        }
        // 分析MainFuncDef成分
        SyntaxClass mainFuncDef;
        startPos = pos;
        mainFuncDef = readMainFuncDef(globalSymbolTable);
        if (mainFuncDef == null) {
            pos = startPos;
            throw new SyntaxException();
        }
        compUnit.appendSonNode(mainFuncDef);
        compUnit.setFirstAsLineNo(); // 设置非终结符行号
        return compUnit;
    }

    public SyntaxClass readDecl(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        int startPos;
        SyntaxClass decl = new SyntaxClass(SyntaxClass.DECL);
        decl.setCurEnv(curEnv);
        // 开头是const，说明是ConstDecl
        if (tokenList.get(pos).getTokenType() == Token.CONSTTK) {
            SyntaxClass constDecl;
            startPos = pos;
            constDecl = readConstDecl(curEnv);
            if (constDecl == null) {
                pos = startPos;
                throw new SyntaxException();
            } else {
                decl.appendSonNode(constDecl);
            }
        } else { // 否则是VarDecl
            SyntaxClass varDecl;
            startPos = pos;
            varDecl = readVarDecl(curEnv);
            if (varDecl == null) {
                pos = startPos;
                throw new SyntaxException();
            } else {
                decl.appendSonNode(varDecl);
            }
        }
        decl.setFirstAsLineNo();
        return decl;
    }

    public SyntaxClass readConstDecl(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        int startPos;
        // 检查是否以const开头
        if (tokenList.get(pos).getTokenType() != Token.CONSTTK) {
            return null;
        }
        SyntaxClass constDecl = new SyntaxClass(SyntaxClass.CONSTDECL),
                bType, constDef;
        constDecl.setCurEnv(curEnv);
        Token constToken = tokenList.get(this.pos++);
        constDecl.appendSonNode(constToken);
        // 检查BType
        startPos = pos;
        bType = readBType();
        if (bType == null) {
            pos = startPos;
            throw new SyntaxException();
        } else {
            constDecl.appendSonNode(bType);
        }
        // 检查必需的constDef
        startPos = pos;
        constDef = readConstDef(curEnv);
        if (constDef == null) {
            pos = startPos;
            throw new SyntaxException();
        } else {
            constDecl.appendSonNode(constDef);
        }

        // 如果有逗号就继续
        while (tokenList.get(pos).getTokenType() == Token.COMMA) {
            Token comma = tokenList.get(pos++);
            startPos = pos;
            constDef = readConstDef(curEnv);
            if (constDef == null) {
                pos = startPos;
                throw new SyntaxException();
            } else {
                constDecl.appendSonNode(comma);
                constDecl.appendSonNode(constDef);
            }
        }

        // 检测分号
        if (tokenList.get(pos).getTokenType() != Token.SEMICN) {
            //throw new SyntaxException();
            Error semicnMissingError = new Error(8, tokenList.get(pos - 1).getLineNo());
            errorList.add(semicnMissingError);
        } else {
            Token semicn = tokenList.get(pos++);
            constDecl.appendSonNode(semicn);
        }
        constDecl.setFirstAsLineNo();
        return constDecl;
    }

    public SyntaxClass readBType() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass bType = new SyntaxClass(SyntaxClass.BTYPE);
        // 检查是不是int
        if (tokenList.get(pos).getTokenType() != Token.INTTK) {
            throw new SyntaxException();
        } else {
            bType.appendSonNode(tokenList.get(pos++));
        }
        return bType;
    }

    public SyntaxClass readConstDef(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass constDef = new SyntaxClass(SyntaxClass.CONSTDEF);
        constDef.setCurEnv(curEnv);
        Token ident = tokenList.get(pos);
        int startPos;
        // 检查是否是标识符
        if (ident.getTokenType() != Token.IDENFR) {
            throw new SyntaxException();
        } else {
            ++pos;
            constDef.appendSonNode(ident);
        }
        // 检查是否有左中括号
        Token token;
        int brackNum = 0;
        while (tokenList.get(pos).getTokenType() == Token.LBRACK) {
            // 若有，检查ConstExp和右中括号
            ++brackNum;
            token = tokenList.get(pos++);
            SyntaxClass constExp;
            startPos = pos;
            constExp = readConstExp(curEnv);
            Token rbrack = tokenList.get(pos);
            // TODO: Missing ']'
            if (rbrack.getTokenType() != Token.RBRACK) {
                Error rBrackMissingError = new Error(10, tokenList.get(pos - 1).getLineNo());
                errorList.add(rBrackMissingError);
                // throw new SyntaxException();
            } else {
                ++pos;
                constDef.appendSonNode(token);
                constDef.appendSonNode(constExp);
                constDef.appendSonNode(rbrack);
            }
        }
        // 检查是否是 =
        if (tokenList.get(pos).getTokenType() != Token.ASSIGN) {
            throw new SyntaxException();
        } else {
            token = tokenList.get(pos++);
            constDef.appendSonNode(token);
            SyntaxClass constInitVal;
            // 检查constInitVal
            constInitVal = readConstInitVal(curEnv);
            if (constInitVal == null) {
                throw new SyntaxException();
            } else {
                constDef.appendSonNode(constInitVal);
            }
        }
        // Add current constdef to symbol table
        VarSymbol curSymbol = new VarSymbol(ident, 0, brackNum);
        // TODO: Duplicated Symbol Exception
        constDef.setFirstAsLineNo();
        try {
            curEnv.addSymbol(curSymbol);
        } catch (DuplicatedDefineIdentException e) {
            Error duplicatedDefinedError = new Error(1, ident.getLineNo());
            errorList.add(duplicatedDefinedError);
        }
        return constDef;
    }

    public SyntaxClass readConstInitVal(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass constInitVal = new SyntaxClass(SyntaxClass.CONSTINITVAL);
        constInitVal.setCurEnv(curEnv);
        // 检查是否是左花括号
        if (tokenList.get(pos).getTokenType() == Token.LBRACE) {
            Token token = tokenList.get(pos++);
            constInitVal.appendSonNode(token);
            // 先看看是不是右花括号，如果不是，说明中间有内容
            // 这样的话可以避免回溯
            if (tokenList.get(pos).getTokenType() != Token.RBRACE) {
                // 检查是否是ConstInitVal
                SyntaxClass subConstInitVal;
                subConstInitVal = readConstInitVal(curEnv);
                if (subConstInitVal == null) {
                    throw new SyntaxException();
                } else {
                    constInitVal.appendSonNode(subConstInitVal);
                }
                // 检查有无逗号
                while (tokenList.get(pos).getTokenType() == Token.COMMA) {
                    Token comma = tokenList.get(pos++);
                    constInitVal.appendSonNode(comma);
                    // 有逗号，后面需要再接ConstInitVal
                    subConstInitVal = readConstInitVal(curEnv);
                    if (subConstInitVal == null) {
                        throw new SyntaxException();
                    } else {
                        constInitVal.appendSonNode(subConstInitVal);
                    }
                }
            }
            // 检查右花括号
            if (tokenList.get(pos).getTokenType() != Token.RBRACE) {
                throw new SyntaxException();
            } else {
                token = tokenList.get(pos++);
                constInitVal.appendSonNode(token);
            }
        } else { // 不是左花括号，要匹配一个ConstExp
            SyntaxClass constExp;
            constExp = readConstExp(curEnv);
            if (constExp == null) {
                throw new SyntaxException();
            } else {
                constInitVal.appendSonNode(constExp);
            }
        }
        constInitVal.setFirstAsLineNo();
        return constInitVal;
    }

    public SyntaxClass readVarDecl(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass varDecl = new SyntaxClass(SyntaxClass.VARDECL);
        varDecl.setCurEnv(curEnv);
        SyntaxClass bType, varDef;
        // 检查BType
        bType = readBType();
        if (bType == null) {
            throw new SyntaxException();
        } else {
            varDecl.appendSonNode(bType);
        }
        // 若下一个ident后面是括号，则说明是FuncDef
        if (tokenList.get(pos + 1).getTokenType() == Token.LPARENT) {
            throw new SyntaxException();
        }
        // 检查VarDef
        varDef = readVarDef(curEnv);
        if (varDef == null) {
            throw new SyntaxException();
        } else {
            varDecl.appendSonNode(varDef);
        }
        Token token;
        // 如果有逗号
        while (tokenList.get(pos).getTokenType() == Token.COMMA) {
            token = tokenList.get(pos++);
            varDecl.appendSonNode(token);
            // 逗号后面需要是VarDef
            varDef = readVarDef(curEnv);
            if (varDef == null) {
                throw new SyntaxException();
            } else {
                varDecl.appendSonNode(varDef);
            }
        }
        // 需要一个分号
        if (tokenList.get(pos).getTokenType() == Token.SEMICN) {
            token = tokenList.get(pos++);
            varDecl.appendSonNode(token);
        } else if (tokenList.get(pos).getTokenType() == Token.LPARENT) {
            throw new SyntaxException();
        } else {
            Error semicnMissingError = new Error(8, tokenList.get(pos - 1).getLineNo());
            errorList.add(semicnMissingError);
            //throw new SemicnMissingException(tokenList.get(pos - 1).getLineNo());
        }
        varDecl.setFirstAsLineNo();
        return varDecl;
    }

    public SyntaxClass readVarDef(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass varDef = new SyntaxClass(SyntaxClass.VARDEF);
        varDef.setCurEnv(curEnv);
        Token ident = tokenList.get(pos);
        // 检查Token是否是标识符
        if (ident.getTokenType() != Token.IDENFR) {
            throw new SyntaxException();
        } else {
            ++pos;
            varDef.appendSonNode(ident);
        }
        // 检查是否有左中括号
        int brackNum = 0;
        Token token = tokenList.get(pos);
        while (token.getTokenType() == Token.LBRACK) {
            ++brackNum;
            // 若有，检查ConstExp和右中括号
            ++pos;
            SyntaxClass constExp;
            constExp = readConstExp(curEnv);
            Token rbrack = tokenList.get(pos);
            if (rbrack.getTokenType() != Token.RBRACK) {
                Error rBrackMissingError = new Error(10, tokenList.get(pos - 1).getLineNo());
                errorList.add(rBrackMissingError);
                // throw new SyntaxException();
            } else {
                ++pos;
                varDef.appendSonNode(token);
                varDef.appendSonNode(constExp);
                varDef.appendSonNode(rbrack);
            }
            token = tokenList.get(pos);
        }
        // 如果是 =
        if (token.getTokenType() == Token.ASSIGN) {
            ++pos;
            varDef.appendSonNode(token);
            SyntaxClass initVal;
            // 检查InitVal
            initVal = readInitVal(curEnv);
            if (initVal == null) {
                throw new SyntaxException();
            } else {
                varDef.appendSonNode(initVal);
            }
        }
        // Add current constdef to symbol table
        VarSymbol curSymbol = new VarSymbol(ident, 1, brackNum);
        varDef.setFirstAsLineNo();
        // TODO: Duplicated Symbol Exception
        try {
            curEnv.addSymbol(curSymbol);
        } catch (DuplicatedDefineIdentException e) {
            Error duplicatedDefinedError = new Error(1, ident.getLineNo());
            errorList.add(duplicatedDefinedError);
        }
        return varDef;
    }

    public SyntaxClass readInitVal(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass initVal = new SyntaxClass(SyntaxClass.INITVAL);
        initVal.setCurEnv(curEnv);
        Token token;
        // 检查是否是左花括号
        if (tokenList.get(pos).getTokenType() == Token.LBRACE) {
            token = tokenList.get(pos++);
            initVal.appendSonNode(token);
            // 如果不是右花括号，说明中间有东西，避免回溯
            if (tokenList.get(pos).getTokenType() != Token.RBRACE) {
                // 检查必须存在的InitVal
                SyntaxClass subInitVal;
                subInitVal = readInitVal(curEnv);
                if (subInitVal == null) {
                    throw new SyntaxException();
                } else {
                    initVal.appendSonNode(subInitVal);
                }
                // 有逗号，可以继续读
                while (tokenList.get(pos).getTokenType() == Token.COMMA) {
                    token = tokenList.get(pos++);
                    initVal.appendSonNode(token);
                    // 需要一个InitVal
                    subInitVal = readInitVal(curEnv);
                    if (subInitVal == null) {
                        throw new SyntaxException();
                    } else {
                        initVal.appendSonNode(subInitVal);
                    }
                }
            }
            // 检查右花括号
            if (tokenList.get(pos).getTokenType() == Token.RBRACE) {
                token = tokenList.get(pos++);
                initVal.appendSonNode(token);
            } else {
                throw new SyntaxException();
            }
        } else {
            // 单Exp情况
            SyntaxClass exp;
            exp = readExp(curEnv);
            if (exp == null) {
                throw new SyntaxException();
            } else {
                initVal.appendSonNode(exp);
            }
        }
        initVal.setFirstAsLineNo();
        return initVal;
    }

    public SyntaxClass readFuncDef(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SymbolTable funcBlockEnv = new SymbolTable(curEnv);
        SyntaxClass funcDef = new SyntaxClass(SyntaxClass.FUNCDEF);
        funcDef.setCurEnv(curEnv);
        SyntaxClass funcType, block;
        // 检查FuncType
        funcType = readFuncType();
        if (funcType == null) {
            throw new SyntaxException();
        } else {
            funcDef.appendSonNode(funcType);
        }
        // 检查Ident
        if (tokenList.get(pos).getTokenType() != Token.IDENFR) {
            throw new SyntaxException();
        } else {
            Token ident;
            ident = tokenList.get(pos++);
            funcDef.appendSonNode(ident);
        }
        // 检查左括号
        if (tokenList.get(pos).getTokenType() != Token.LPARENT) {
            throw new SyntaxException();
        } else {
            Token ident;
            ident = tokenList.get(pos++);
            funcDef.appendSonNode(ident);
        }
        // 若没有直接遇见右括号，说明中间有东西
        if (tokenList.get(pos).getTokenType() != Token.RPARENT) {
            SyntaxClass funcFParams;
            funcFParams = readFuncFParams(funcBlockEnv);
            if (funcFParams == null) {
                throw new SyntaxException();
            } else {
                funcDef.appendSonNode(funcFParams);
            }
        }
        // 检查右括号
        if (tokenList.get(pos).getTokenType() != Token.RPARENT) {
            Error rParentMissingError = new Error(9, tokenList.get(pos - 1).getLineNo());
            errorList.add(rParentMissingError);
            //throw new SyntaxException();
        } else {
            Token ident;
            ident = tokenList.get(pos++);
            funcDef.appendSonNode(ident);
        }
        // 需要在Block前把该函数加入env以应对递归情形
        FuncSymbol funcSymbol = SymbolAnalyzer.getFuncSymbol(funcDef);
        funcBlockEnv.setCurFunc(funcSymbol); // 设置函数块函数
        try {
            curEnv.addSymbol(funcSymbol);
        } catch (DuplicatedDefineIdentException e) {
            Error duplicatedDefinedError = new Error(1, funcDef.getLineNo());
            errorList.add(duplicatedDefinedError);
        }
        // 检查Block
        block = readBlock(funcBlockEnv);
        if (block == null) {
            throw new SyntaxException();
        } else {
            funcDef.appendSonNode(block);
        }
        funcDef.setFirstAsLineNo();
        return funcDef;
    }

    public SyntaxClass readMainFuncDef(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SymbolTable mainFuncBlockEnv = new SymbolTable(curEnv);
        SyntaxClass mainFuncDef = new SyntaxClass(SyntaxClass.MAINFUNCDEF);
        mainFuncDef.setCurEnv(curEnv);
        SyntaxClass block;
        Token mainToken;
        // 检查int
        if (tokenList.get(pos).getTokenType() != Token.INTTK) {
            throw new SyntaxException();
        } else {
            Token ident;
            ident = tokenList.get(pos++);
            mainFuncDef.appendSonNode(ident);
        }
        // 检查main
        if (tokenList.get(pos).getTokenType() != Token.MAINTK) {
            throw new SyntaxException();
        } else {
            mainToken = tokenList.get(pos++);
            mainFuncDef.appendSonNode(mainToken);
        }
        // 检查左括号
        if (tokenList.get(pos).getTokenType() != Token.LPARENT) {
            throw new SyntaxException();
        } else {
            Token ident;
            ident = tokenList.get(pos++);
            mainFuncDef.appendSonNode(ident);
        }
        // 检查右括号
        if (tokenList.get(pos).getTokenType() != Token.RPARENT) {
            Error rParentMissingError = new Error(9, tokenList.get(pos - 1).getLineNo());
            errorList.add(rParentMissingError);
            // throw new SyntaxException();
        } else {
            Token ident;
            ident = tokenList.get(pos++);
            mainFuncDef.appendSonNode(ident);
        }
        // 先加入env
        FuncSymbol mainFuncSymbol = new FuncSymbol(mainToken, true);
        mainFuncBlockEnv.setCurFunc(mainFuncSymbol); // 设置当前函数
        // TODO: Main func duplicated
        try {
            curEnv.addSymbol(mainFuncSymbol);
        } catch (DuplicatedDefineIdentException e) {
            Error duplicatedDefinedError = new Error(1, mainFuncDef.getLineNo());
            errorList.add(duplicatedDefinedError);
        }
        // 检查Block
        block = readBlock(mainFuncBlockEnv);
        if (block == null) {
            throw new SyntaxException();
        } else {
            mainFuncDef.appendSonNode(block);
        }
        mainFuncDef.setFirstAsLineNo();

        return mainFuncDef;
    }

    public SyntaxClass readFuncType() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass funcType = new SyntaxClass(SyntaxClass.FUNCTYPE);
        // 检查void
        if (tokenList.get(pos).getTokenType() == Token.VOIDTK ||
                tokenList.get(pos).getTokenType() == Token.INTTK) {
            Token ident;
            ident = tokenList.get(pos++);
            funcType.appendSonNode(ident);
        } else {
            throw new SyntaxException();
        }
        funcType.setFirstAsLineNo();
        return funcType;
    }

    public SyntaxClass readFuncFParams(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass funcFParams = new SyntaxClass(SyntaxClass.FUNCFPARAMS);
        funcFParams.setCurEnv(curEnv);
        SyntaxClass param;
        // 检查必须有的FuncFParam
        param = readFuncFParam(curEnv);
        if (param == null) {
            throw new SyntaxException();
        } else {
            funcFParams.appendSonNode(param);
        }
        // 如果有逗号，说明后面还有
        while (tokenList.get(pos).getTokenType() == Token.COMMA) {
            Token token = tokenList.get(pos++);
            funcFParams.appendSonNode(token);
            param = readFuncFParam(curEnv);
            if (param == null) {
                throw new SyntaxException();
            } else {
                funcFParams.appendSonNode(param);
            }
        }
        funcFParams.setFirstAsLineNo();
        return funcFParams;
    }

    public SyntaxClass readFuncFParam(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass funcFParam = new SyntaxClass(SyntaxClass.FUNCFPARAM);
        funcFParam.setCurEnv(curEnv);
        Token fParamToken;
        // 检查BType
        SyntaxClass bType;
        bType = readBType();
        if (bType == null) {
            throw new SyntaxException();
        } else {
            funcFParam.appendSonNode(bType);
        }
        // 检查ident
        if (tokenList.get(pos).getTokenType() == Token.IDENFR) {
            fParamToken = tokenList.get(pos++);
            funcFParam.appendSonNode(fParamToken);
        } else {
            throw new SyntaxException();
        }
        int brackNum = 0;
        // 如果有左中括号
        if (tokenList.get(pos).getTokenType() == Token.LBRACK) {
            ++brackNum;
            Token ident = tokenList.get(pos++);
            funcFParam.appendSonNode(ident);
            // 需要跟一个右中括号
            if (tokenList.get(pos).getTokenType() == Token.RBRACK) {
                ident = tokenList.get(pos++);
                funcFParam.appendSonNode(ident);
            } else {
                Error rBrackMissingError = new Error(10, tokenList.get(pos - 1).getLineNo());
                errorList.add(rBrackMissingError);
                // throw new SyntaxException();
            }
            // 如果还有左中括号
            while (tokenList.get(pos).getTokenType() == Token.LBRACK) {
                ++brackNum;
                ident = tokenList.get(pos++);
                funcFParam.appendSonNode(ident);
                // 检查一个ConstExp
                SyntaxClass constExp;
                constExp = readConstExp(curEnv);
                if (constExp == null) {
                    throw new SyntaxException();
                }
                funcFParam.appendSonNode(constExp);
                // 检查右中括号
                if (tokenList.get(pos).getTokenType() == Token.RBRACK) {
                    ident = tokenList.get(pos++);
                    funcFParam.appendSonNode(ident);
                } else {
                    Error rBrackMissingError = new Error(10, tokenList.get(pos - 1).getLineNo());
                    errorList.add(rBrackMissingError);
                    // throw new SyntaxException();
                }
            }
        }
        funcFParam.setFirstAsLineNo();
        VarSymbol paramSymbol = new VarSymbol(fParamToken, 1, brackNum); // 作为函数块内变量
        // TODO:重定义错误（应该不会发生，除非int a, int a这种情况......）
        try {
            curEnv.addSymbol(paramSymbol);
        } catch (DuplicatedDefineIdentException e) {
            Error duplicatedDefinedError = new Error(1, fParamToken.getLineNo());
            errorList.add(duplicatedDefinedError);
        }
        // TODO: 二维数组的第一维长度
        return funcFParam;
    }

    public SyntaxClass readBlock(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass block = new SyntaxClass(SyntaxClass.BLOCK);
        block.setCurEnv(curEnv);
        // 检查左花括号
        int startPos = pos;
        Token brace;
        if (tokenList.get(pos).getTokenType() == Token.LBRACE) {
            brace = tokenList.get(pos++);
            block.appendSonNode(brace);
            SyntaxClass blockItem;
            // 没见到右花括号就继续
            while (tokenList.get(pos).getTokenType() != Token.RBRACE) {
                if (pos >= tokenList.size()) {
                    throw new SyntaxException();
                }
                // 可能存在的BlockItem，若发生错误，则说明没有，break
                // 准备回溯（疑似无必要）
                startPos = pos;
                try {
                    blockItem = readBlockItem(curEnv);
                } catch (SyntaxException e) {
                    pos = startPos;
                    break;
                }
                if (blockItem == null) {
                    throw new SyntaxException();
                } else {
                    block.appendSonNode(blockItem);
                }
            }
            // 右花括号
            if (tokenList.get(pos).getTokenType() == Token.RBRACE) {
                brace = tokenList.get(pos++);

                // 检查有返回值的函数的return情况
                boolean retErr = true;
                if (curEnv.getCurFunc() == null || !curEnv.getCurFunc().funcHasReturn()) {
                    retErr = false;
                }
                blockItem = block.getSonNodeList().getLast();
                if (blockItem.getSyntaxType() == SyntaxClass.BLOCKITEM) {
                    SyntaxClass stmt = blockItem.getSonNodeList().getLast();
                    SyntaxClass returnToken = stmt.getSonNodeList().getFirst();
                    if (returnToken.getSyntaxType() == SyntaxClass.TOKEN) {
                        if (((Token) returnToken).getTokenType() == Token.RETURNTK) {
                            retErr = false;
                        }
                    }
                }
                if (retErr) {
                    Error missingReturnError = new Error(6, brace.getLineNo());
                    errorList.add(missingReturnError);
                }

                block.appendSonNode(brace);
            } else {
                throw new SyntaxException();
            }
        } else {
            throw new SyntaxException();
        }
        block.setFirstAsLineNo();
        return block;
    }

    public SyntaxClass readBlockItem(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass blockItem = new SyntaxClass(SyntaxClass.BLOCKITEM);
        blockItem.setCurEnv(curEnv);
        SyntaxClass syntaxClass;

        int startPos = pos, nextTokenType = tokenList.get(pos).getTokenType();
        if (nextTokenType == Token.CONSTTK || nextTokenType == Token.INTTK) {
            // 尝试解析Decl
            syntaxClass = readDecl(curEnv);
            if (syntaxClass == null) {
                throw new SyntaxException();
            }
            blockItem.appendSonNode(syntaxClass);
        } else {
            // 尝试解析Stmt
            // 先回溯
            // pos = startPos;
            syntaxClass = readStmt(curEnv);
            if (syntaxClass != null) {
                blockItem.appendSonNode(syntaxClass);
            } else {
                throw new SyntaxException();
            }
        }
        blockItem.setFirstAsLineNo();
        return blockItem;
    }

    public SyntaxClass readStmt(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass stmt = new SyntaxClass(SyntaxClass.STMT);
        stmt.setCurEnv(curEnv);
        int nextTokenType = tokenList.get(pos).getTokenType();
        // If
        if (nextTokenType == Token.IFTK) {
            Token token = tokenList.get(pos++);
            stmt.appendSonNode(token);
            // 左括号
            if (tokenList.get(pos).getTokenType() == Token.LPARENT) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                throw new SyntaxException();
            }
            // Cond
            SyntaxClass cond = readCond(curEnv);
            if (cond == null) {
                throw new SyntaxException();
            } else {
                stmt.appendSonNode(cond);
            }
            // 右括号
            if (tokenList.get(pos).getTokenType() == Token.RPARENT) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                Error rParentMissingError = new Error(9, tokenList.get(pos - 1).getLineNo());
                errorList.add(rParentMissingError);
                // throw new SyntaxException();
            }
            // Stmt
            // 新作用域
            SymbolTable ifStmtEnv = new SymbolTable(curEnv);
            SyntaxClass subStmt = readStmt(ifStmtEnv);
            if (subStmt == null) {
                throw new SyntaxException();
            } else {
                stmt.appendSonNode(subStmt);
            }
            // 如果有else
            if (tokenList.get(pos).getTokenType() == Token.ELSETK) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
                // Stmt
                // 新作用域
                SymbolTable elseStmtEnv = new SymbolTable(curEnv);
                subStmt = readStmt(elseStmtEnv);
                if (subStmt == null) {
                    throw new SyntaxException();
                } else {
                    stmt.appendSonNode(subStmt);
                }
            }
        } else if (nextTokenType == Token.WHILETK) {
            // While
            Token token = tokenList.get(pos++);
            stmt.appendSonNode(token);
            // 左括号
            if (tokenList.get(pos).getTokenType() == Token.LPARENT) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                throw new SyntaxException();
            }
            // Cond
            SyntaxClass cond = readCond(curEnv);
            if (cond == null) {
                throw new SyntaxException();
            } else {
                stmt.appendSonNode(cond);
            }
            // 右括号
            if (tokenList.get(pos).getTokenType() == Token.RPARENT) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                Error rParentMissingError = new Error(9, tokenList.get(pos - 1).getLineNo());
                errorList.add(rParentMissingError);
                // throw new SyntaxException();
            }
            // Stmt
            // While的新作用域
            SymbolTable whileStmtEnv = new SymbolTable(curEnv);
            whileStmtEnv.setCycleBlock(true); // 标记为循环块的作用域
            SyntaxClass subStmt = readStmt(whileStmtEnv);
            if (subStmt == null) {
                throw new SyntaxException();
            } else {
                stmt.appendSonNode(subStmt);
            }
        } else if (nextTokenType == Token.BREAKTK) {
            // Break
            Token token, breakToken = tokenList.get(pos++);
            stmt.appendSonNode(breakToken);
            // 分号
            if (tokenList.get(pos).getTokenType() == Token.SEMICN) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                Error semicnMissingError = new Error(8, tokenList.get(pos - 1).getLineNo());
                errorList.add(semicnMissingError);
                //throw new SyntaxException();
            }
            if (!curEnv.isInCycleBlock()) {
                Error cycleError = new Error(12, breakToken.getLineNo());
                errorList.add(cycleError);
            }
        } else if (nextTokenType == Token.CONTINUETK) {
            // Continue
            Token token, continueToken = tokenList.get(pos++);
            stmt.appendSonNode(continueToken);
            // 分号
            if (tokenList.get(pos).getTokenType() == Token.SEMICN) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                Error semicnMissingError = new Error(8, tokenList.get(pos - 1).getLineNo());
                errorList.add(semicnMissingError);
                //throw new SyntaxException();
            }
            if (!curEnv.isInCycleBlock()) {
                Error cycleError = new Error(12, continueToken.getLineNo());
                errorList.add(cycleError);
            }
        } else if (nextTokenType == Token.RETURNTK) {
            // Return
            Token returnToken = tokenList.get(pos++), token;
            stmt.appendSonNode(returnToken);
            // 分号
            if (tokenList.get(pos).getTokenType() == Token.SEMICN) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                // 没有分号，说明有返回值
                SyntaxClass exp;
                exp = readExp(curEnv);
                if (exp == null) {
                    throw new SyntaxException();
                } else {
                    stmt.appendSonNode(exp);
                }
                // 分号
                if (tokenList.get(pos).getTokenType() == Token.SEMICN) {
                    token = tokenList.get(pos++);
                    stmt.appendSonNode(token);
                } else {
                    Error semicnMissingError = new Error(8, tokenList.get(pos - 1).getLineNo());
                    errorList.add(semicnMissingError);
                    //throw new SyntaxException();
                }
                // 检查返回值是否匹配函数类型
                FuncSymbol curFuncSymbol = curEnv.checkCurFunc();
                if (!curFuncSymbol.funcHasReturn()) {
                    // void型却有返回值
                    Error semicnMissingError = new Error(6, returnToken.getLineNo());
                    errorList.add(semicnMissingError);
                }
            }
        } else if (nextTokenType == Token.PRINTFTK) {
            // Printf
            Token token, printfToken = tokenList.get(pos++);
            stmt.appendSonNode(printfToken);
            // 左括号
            if (tokenList.get(pos).getTokenType() == Token.LPARENT) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                throw new SyntaxException();
            }
            // FormatString
            int formatCharNum = 0;
            if (tokenList.get(pos).getTokenType() == Token.STRCON) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
                formatCharNum = token.getFormatCharNum();
            } else {
                throw new SyntaxException();
            }
            // 如果有逗号
            int expNum = 0; // 后接表达式数量
            while (tokenList.get(pos).getTokenType() == Token.COMMA) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
                // 需要有Exp
                SyntaxClass exp = readExp(curEnv);
                if (exp == null) {
                    throw new SyntaxException();
                } else {
                    stmt.appendSonNode(exp);
                }
                ++expNum;
            }
            // 右括号
            if (tokenList.get(pos).getTokenType() == Token.RPARENT) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                Error rParentMissingError = new Error(9, tokenList.get(pos - 1).getLineNo());
                errorList.add(rParentMissingError);
                // throw new SyntaxException();
            }
            // 分号
            if (tokenList.get(pos).getTokenType() == Token.SEMICN) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                Error semicnMissingError = new Error(8, tokenList.get(pos - 1).getLineNo());
                errorList.add(semicnMissingError);
                // throw new SyntaxException();
            }
            //检查格式字符与后接表达式数目是否相等
            if (formatCharNum != expNum) {
                Error formatNumNotMatchError = new Error(11, printfToken.getLineNo());
                errorList.add(formatNumNotMatchError);
            }
        } else if (nextTokenType == Token.LBRACE) {
            // 左花括号，说明是一个Block
            // 新作用域
            SymbolTable blockEnv = new SymbolTable(curEnv);
            SyntaxClass block = readBlock(blockEnv);
            if (block == null) {
                throw new SyntaxException();
            } else {
                stmt.appendSonNode(block);
            }
        } else {
            /* LVal=Exp,[Exp],LVal=getint()三种情况，存在回溯可能*/
            int startPos = pos;
            // [Exp];中单走一个分号的情况（空语句）
            Token semicn;
            if (nextTokenType == Token.SEMICN) {
                semicn = tokenList.get(pos++);
                stmt.appendSonNode(semicn);
            } else { // 非空语句
                // 先试试LVal
                SyntaxClass lVal = null;
                boolean isLVal = true;
                try {
                    lVal = readLVal(curEnv);
                } catch (SyntaxException e) {
                    isLVal = false;
                }
                if (lVal == null) {
                    isLVal = false;
                }
                if (isLVal) {
                    // 检查是否跟着等号
                    if (tokenList.get(pos).getTokenType() == Token.ASSIGN) {
                        // 给LVal赋值，需要检查LVal是否是常量
                        Token lValToken = (Token) lVal.getSonNodeList().get(0);
                        VarSymbol tokenSymbol = (VarSymbol) curEnv.globalLookup(lValToken.getTokenContext());
                        if (!tokenSymbol.isVar()) {
                            // 确实是常量，寄了
                            Error constantAssignmentError = new Error(7, lValToken.getLineNo());
                            errorList.add(constantAssignmentError);
                        }

                        Token token = tokenList.get(pos++);
                        // 确实是等号，则LVal可以确认加入
                        stmt.appendSonNode(lVal);
                        stmt.appendSonNode(token);
                        // 检查是否是getint
                        if (tokenList.get(pos).getTokenType() == Token.GETINTTK) {
                            token = tokenList.get(pos++);
                            // 是getint
                            stmt.appendSonNode(token);
                            // 左括号
                            if (tokenList.get(pos).getTokenType() == Token.LPARENT) {
                                token = tokenList.get(pos++);
                                stmt.appendSonNode(token);
                            } else {
                                throw new SyntaxException(); // getint 左括号缺失
                            }
                            // 右括号
                            if (tokenList.get(pos).getTokenType() == Token.RPARENT) {
                                token = tokenList.get(pos++);
                                stmt.appendSonNode(token);
                            } else {
                                Error rParentMissingError = new Error(9, tokenList.get(pos - 1).getLineNo());
                                errorList.add(rParentMissingError); // getint 右括号缺失
                                // throw new SyntaxException();
                            }
                        } else {
                            // 不是LVal = getint，则应该是LVal = Exp
                            SyntaxClass exp;
                            exp = readExp(curEnv);
                            if (exp == null) {
                                throw new SyntaxException();
                            }
                            stmt.appendSonNode(exp);
                        }
                    } else {
                        // LVal后面没跟等号，说明不可以解析为LVal，应该是Exp的一部分
                        isLVal = false;
                    }
                }
                if (!isLVal) {
                    // 经过前面所有判断，认为不是单独的LVal，只能是非空的Exp，需要回溯
                    pos = startPos;
                    SyntaxClass exp = readExp(curEnv);
                    if (exp == null) {
                        throw new SyntaxException();
                    }
                    stmt.appendSonNode(exp);
                }
                // 分号
                if (tokenList.get(pos).getTokenType() == Token.SEMICN) {
                    semicn = tokenList.get(pos++);
                    stmt.appendSonNode(semicn);
                } else {
                    Error semicnMissingError = new Error(8, tokenList.get(pos - 1).getLineNo());
                    errorList.add(semicnMissingError);
                    //throw new SyntaxException();
                }
            }
        }
        stmt.setFirstAsLineNo();
        return stmt;
    }

    public SyntaxClass readExp(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass exp = new SyntaxClass(SyntaxClass.EXP), addExp;
        exp.setCurEnv(curEnv);
        // 检查AddExp
        addExp = readAddExp(curEnv);
        if (addExp == null) {
            throw new SyntaxException();
        }
        exp.appendSonNode(addExp);
        return exp;
    }

    public SyntaxClass readCond(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass cond = new SyntaxClass(SyntaxClass.COND), lOrExp;
        cond.setCurEnv(curEnv);
        // 检查LOrExp
        lOrExp = readLOrExp(curEnv);
        if (lOrExp == null) {
            throw new SyntaxException();
        }
        cond.appendSonNode(lOrExp);
        return cond;
    }

    public SyntaxClass readLVal(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass lVal = new SyntaxClass(SyntaxClass.LVAL);
        lVal.setCurEnv(curEnv);
        // 检查Ident
        Token ident;
        if (tokenList.get(pos).getTokenType() == Token.IDENFR) {
            ident = tokenList.get(pos++);
            lVal.appendSonNode(ident);
            // 错误检查：查符号表是否存在该标识符
            Symbol identSymbol = curEnv.globalLookup(ident.getTokenContext());
            if (identSymbol == null) {
                // 未定义符号
                Error undefinedSymbolError = new Error(2, ident.getLineNo());
                errorList.add(undefinedSymbolError);
            }
            // 检查可能有的左中括号
            int brackNum = 0;
            while (tokenList.get(pos).getTokenType() == Token.LBRACK) {
                ++brackNum;
                Token brack = tokenList.get(pos++);
                lVal.appendSonNode(brack);
                SyntaxClass exp;
                // Exp
                exp = readExp(curEnv);
                if (exp == null) {
                    throw new SyntaxException();
                }
                lVal.appendSonNode(exp);
                // 右中括号
                if (tokenList.get(pos).getTokenType() == Token.RBRACK) {
                    brack = tokenList.get(pos++);
                    lVal.appendSonNode(brack);
                } else {
                    Error rBrackMissingError = new Error(10, tokenList.get(pos - 1).getLineNo());
                    errorList.add(rBrackMissingError);
                    // throw new SyntaxException();
                }
            }
        } else {
            throw new SyntaxException();
        }
        // TODO: 检查左值有效性
        lVal.setFirstAsLineNo();
        return lVal;
    }

    public SyntaxClass readPrimaryExp(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass primaryExp = new SyntaxClass(SyntaxClass.PRIMARYEXP);
        primaryExp.setCurEnv(curEnv);
        // (Exp)
        if (tokenList.get(pos).getTokenType() == Token.LPARENT) {
            // (
            Token parent = tokenList.get(pos++);
            primaryExp.appendSonNode(parent);
            // Exp
            SyntaxClass exp;
            exp = readExp(curEnv);
            if (exp == null) {
                throw new SyntaxException();
            }
            primaryExp.appendSonNode(exp);
            // )
            if (tokenList.get(pos).getTokenType() == Token.RPARENT) {
                parent = tokenList.get(pos++);
                primaryExp.appendSonNode(parent);
            } else {
                Error rParentMissingError = new Error(9, tokenList.get(pos - 1).getLineNo());
                errorList.add(rParentMissingError);
                // throw new SyntaxException();
            }
        } else if (tokenList.get(pos).getTokenType() == Token.INTCON) {
            // Number
            SyntaxClass number;
            number = readNumber();
            if (number == null) {
                throw new SyntaxException();
            }
            primaryExp.appendSonNode(number);
        } else {
            // LVal
            SyntaxClass lVal;
            lVal = readLVal(curEnv);
            if (lVal == null) {
                throw new SyntaxException();
            }
            primaryExp.appendSonNode(lVal);
        }
        primaryExp.setFirstAsLineNo();
        return primaryExp;
    }

    public SyntaxClass readNumber() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass number = new SyntaxClass(SyntaxClass.NUMBER);
        if (tokenList.get(pos).getTokenType() == Token.INTCON) {
            // IntConst
            Token parent = tokenList.get(pos++);
            number.appendSonNode(parent);
        } else {
            throw new SyntaxException();
        }
        number.setFirstAsLineNo();
        return number;
    }

    public SyntaxClass readUnaryExp(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass unaryExp = new SyntaxClass(SyntaxClass.UNARYEXP);
        unaryExp.setCurEnv(curEnv);
        int nextTokenType = tokenList.get(pos).getTokenType();
        // Ident
        if (nextTokenType == Token.IDENFR && tokenList.get(pos + 1).getTokenType() == Token.LPARENT) {
            Token ident = tokenList.get(pos++);
            unaryExp.appendSonNode(ident);
            // 左括号
            if (tokenList.get(pos).getTokenType() == Token.LPARENT) {
                Token lParent = tokenList.get(pos++);
                unaryExp.appendSonNode(lParent);
            } else {
                throw new SyntaxException();
            }
            // 可能的参数
            SyntaxClass funcRParams;
            int startPos = pos;
            try {
                funcRParams = readFuncRParams(curEnv); // 在当前作用域找
            } catch (SyntaxException e) {
                // 出错说明没有能够读取到参数，那就是没有
                funcRParams = null;
                pos = startPos;
            }
            if (funcRParams != null) {
                unaryExp.appendSonNode(funcRParams);
            }
            // 右括号
            if (tokenList.get(pos).getTokenType() == Token.RPARENT) {
                Token rParent = tokenList.get(pos++);
                unaryExp.appendSonNode(rParent);
            } else {
                Error rParentMissingError = new Error(9, tokenList.get(pos - 1).getLineNo());
                errorList.add(rParentMissingError);
                // throw new SyntaxException();
            }
            // 先检查符号存在与否
            FuncSymbol identSymbol = (FuncSymbol) curEnv.globalLookup(ident.getTokenContext());
            if (identSymbol == null) {
                Error undefinedSymbolError = new Error(2, ident.getLineNo());
                errorList.add(undefinedSymbolError);
            } else {
                FuncSymbol curFuncSymbol = SymbolAnalyzer.getCallFuncSymbol(unaryExp);
                if (!identSymbol.checkParamsLength(curFuncSymbol)) {
                    // 检查参数个数
                    Error undefinedSymbolError = new Error(3, ident.getLineNo());
                    errorList.add(undefinedSymbolError);
                } else if (!identSymbol.checkConsistent(curFuncSymbol)) {
                    // 检查参数类型
                    Error undefinedSymbolError = new Error(4, ident.getLineNo());
                    errorList.add(undefinedSymbolError);
                }
            }
        } else if (nextTokenType == Token.PLUS ||
                nextTokenType == Token.MINU ||
                nextTokenType == Token.NOT) {
            // UnaryOp UnaryExp情况
            // 先检查一元运算符
            SyntaxClass unaryOp;
            unaryOp = readUnaryOp();
            if (unaryOp == null) {
                throw new SyntaxException();
            }
            unaryExp.appendSonNode(unaryOp);
            // 检查一元表达式
            SyntaxClass subUnaryExp;
            subUnaryExp = readUnaryExp(curEnv);
            if (subUnaryExp == null) {
                throw new SyntaxException();
            }
            unaryExp.appendSonNode(subUnaryExp);
        } else {
            // PrimaryExp
            SyntaxClass primaryExp;
            primaryExp = readPrimaryExp(curEnv);
            if (primaryExp == null) {
                throw new SyntaxException();
            }
            unaryExp.appendSonNode(primaryExp);
        }
        unaryExp.setFirstAsLineNo();
        return unaryExp;
    }

    public SyntaxClass readUnaryOp() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass unaryOp = new SyntaxClass(SyntaxClass.UNARYOP);
        // +,-,!
        int tokenType = tokenList.get(pos).getTokenType();
        if (tokenType == Token.PLUS || tokenType == Token.MINU || tokenType == Token.NOT) {
            Token unaryOpToken = tokenList.get(pos++);
            unaryOp.appendSonNode(unaryOpToken);
        } else {
            throw new SyntaxException();
        }
        unaryOp.setFirstAsLineNo();
        return unaryOp;
    }

    public SyntaxClass readFuncRParams(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass funcRParams = new SyntaxClass(SyntaxClass.FUNCRPARAMS);
        funcRParams.setCurEnv(curEnv);
        // 必须要有的Exp
        SyntaxClass exp;
        exp = readExp(curEnv);
        if (exp == null) {
            throw new SyntaxException();
        }
        funcRParams.appendSonNode(exp);
        // 如果有逗号就一直读
        while (tokenList.get(pos).getTokenType() == Token.COMMA) {
            Token comma = tokenList.get(pos++);
            funcRParams.appendSonNode(comma);
            exp = readExp(curEnv);
            if (exp == null) {
                throw new SyntaxException();
            }
            funcRParams.appendSonNode(exp);
        }
        funcRParams.setFirstAsLineNo();
        return funcRParams;
    }

    public SyntaxClass readMulExp(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass mulExp = new SyntaxClass(SyntaxClass.MULEXP);
        mulExp.setCurEnv(curEnv);
        SyntaxClass unaryExp;
        // 修改文法，消除左递归
        // 需要一个UnaryExp
        unaryExp = readUnaryExp(curEnv);
        if (unaryExp == null) {
            throw new SyntaxException();
        }
        mulExp.appendSonNode(unaryExp);
        int nextTokenType = tokenList.get(pos).getTokenType();
        // *, /, %
        while (nextTokenType == Token.MULT || nextTokenType == Token.DIV || nextTokenType == Token.MOD) {
            // 先封装自己
            mulExp.setFirstAsLineNo();
            SyntaxClass tmpMulExp = new SyntaxClass(SyntaxClass.MULEXP);
            tmpMulExp.appendSonNode(mulExp);
            mulExp = tmpMulExp;
            Token token = tokenList.get(pos++);
            mulExp.appendSonNode(token);
            // UnaryExp
            unaryExp = readUnaryExp(curEnv);
            if (unaryExp == null) {
                throw new SyntaxException();
            }
            mulExp.appendSonNode(unaryExp);
            nextTokenType = tokenList.get(pos).getTokenType();
        }
        mulExp.setFirstAsLineNo();
        return mulExp;
    }

    public SyntaxClass readAddExp(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass addExp = new SyntaxClass(SyntaxClass.ADDEXP);
        addExp.setCurEnv(curEnv);
        SyntaxClass mulExp;
        // 修改文法，消除左递归
        // 需要一个MulExp
        mulExp = readMulExp(curEnv);
        if (mulExp == null) {
            throw new SyntaxException();
        }
        addExp.appendSonNode(mulExp);
        int nextTokenType = tokenList.get(pos).getTokenType();
        // +,-
        while (nextTokenType == Token.PLUS || nextTokenType == Token.MINU) {
            // 先封装自己
            addExp.setFirstAsLineNo();
            SyntaxClass tmpAddExp = new SyntaxClass(SyntaxClass.ADDEXP);
            tmpAddExp.appendSonNode(addExp);
            addExp = tmpAddExp;
            Token token = tokenList.get(pos++);
            addExp.appendSonNode(token);
            // MulExp
            mulExp = readMulExp(curEnv);
            if (mulExp == null) {
                throw new SyntaxException();
            }
            addExp.appendSonNode(mulExp);
            nextTokenType = tokenList.get(pos).getTokenType();
        }
        addExp.setFirstAsLineNo();
        return addExp;
    }

    public SyntaxClass readRelExp(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass relExp = new SyntaxClass(SyntaxClass.RELEXP);
        relExp.setCurEnv(curEnv);
        SyntaxClass addExp;
        // 修改文法，消除左递归
        // 需要一个AddExp
        addExp = readAddExp(curEnv);
        if (addExp == null) {
            throw new SyntaxException();
        }
        relExp.appendSonNode(addExp);
        int nextTokenType = tokenList.get(pos).getTokenType();
        // <,>,<=,>=
        while (nextTokenType == Token.LSS || nextTokenType == Token.GRE ||
                nextTokenType == Token.LEQ || nextTokenType == Token.GEQ) {
            // 先封装自己
            relExp.setFirstAsLineNo();
            SyntaxClass tmpRelExp = new SyntaxClass(SyntaxClass.RELEXP);
            tmpRelExp.appendSonNode(relExp);
            relExp = tmpRelExp;
            Token token = tokenList.get(pos++);
            relExp.appendSonNode(token);
            // AddExp
            addExp = readAddExp(curEnv);
            if (addExp == null) {
                throw new SyntaxException();
            }
            relExp.appendSonNode(addExp);
            nextTokenType = tokenList.get(pos).getTokenType();
        }
        relExp.setFirstAsLineNo();
        return relExp;
    }

    public SyntaxClass readEqExp(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass eqExp = new SyntaxClass(SyntaxClass.EQEXP);
        eqExp.setCurEnv(curEnv);
        SyntaxClass relExp;
        // 修改文法，消除左递归
        // 需要一个RelExp
        relExp = readRelExp(curEnv);
        if (relExp == null) {
            throw new SyntaxException();
        }
        eqExp.appendSonNode(relExp);
        int nextTokenType = tokenList.get(pos).getTokenType();
        // ==,!=
        while (nextTokenType == Token.EQL || nextTokenType == Token.NEQ) {
            // 先封装自己
            eqExp.setFirstAsLineNo();
            SyntaxClass tmpEqExp = new SyntaxClass(SyntaxClass.EQEXP);
            tmpEqExp.appendSonNode(eqExp);
            eqExp = tmpEqExp;
            Token token = tokenList.get(pos++);
            eqExp.appendSonNode(token);
            // RelExp
            relExp = readRelExp(curEnv);
            if (relExp == null) {
                throw new SyntaxException();
            }
            eqExp.appendSonNode(relExp);
            nextTokenType = tokenList.get(pos).getTokenType();
        }
        eqExp.setFirstAsLineNo();
        return eqExp;
    }

    public SyntaxClass readLAndExp(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass lAndExp = new SyntaxClass(SyntaxClass.LANDEXP);
        lAndExp.setCurEnv(curEnv);
        SyntaxClass eqExp;
        // 修改文法，消除左递归
        // 需要一个EqExp
        eqExp = readEqExp(curEnv);
        if (eqExp == null) {
            throw new SyntaxException();
        }
        lAndExp.appendSonNode(eqExp);
        int nextTokenType = tokenList.get(pos).getTokenType();
        // &&
        while (nextTokenType == Token.AND) {
            // 先封装自己
            lAndExp.setFirstAsLineNo();
            SyntaxClass tmpLAndExp = new SyntaxClass(SyntaxClass.LANDEXP);
            tmpLAndExp.appendSonNode(lAndExp);
            lAndExp = tmpLAndExp;
            Token token = tokenList.get(pos++);
            lAndExp.appendSonNode(token);
            // EqExp
            eqExp = readEqExp(curEnv);
            if (eqExp == null) {
                throw new SyntaxException();
            }
            lAndExp.appendSonNode(eqExp);
            nextTokenType = tokenList.get(pos).getTokenType();
        }
        lAndExp.setFirstAsLineNo();
        return lAndExp;
    }

    public SyntaxClass readLOrExp(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass lOrExp = new SyntaxClass(SyntaxClass.LOREXP);
        lOrExp.setCurEnv(curEnv);
        SyntaxClass lAndExp;
        // 修改文法，消除左递归
        // 需要一个LAndExp
        lAndExp = readLAndExp(curEnv);
        if (lAndExp == null) {
            throw new SyntaxException();
        }
        lOrExp.appendSonNode(lAndExp);
        int nextTokenType = tokenList.get(pos).getTokenType();
        // ||
        while (nextTokenType == Token.OR) {
            // 先封装自己
            lOrExp.setFirstAsLineNo();
            SyntaxClass tmpLOrExp = new SyntaxClass(SyntaxClass.LOREXP);
            tmpLOrExp.appendSonNode(lOrExp);
            lOrExp = tmpLOrExp;
            Token token = tokenList.get(pos++);
            lOrExp.appendSonNode(token);
            // LAndExp
            lAndExp = readLAndExp(curEnv);
            if (lAndExp == null) {
                throw new SyntaxException();
            }
            lOrExp.appendSonNode(lAndExp);
            nextTokenType = tokenList.get(pos).getTokenType();
        }
        lOrExp.setFirstAsLineNo();
        return lOrExp;
    }

    public SyntaxClass readConstExp(SymbolTable curEnv) throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass constExp = new SyntaxClass(SyntaxClass.CONSTEXP);
        constExp.setCurEnv(curEnv);
        // AddExp
        SyntaxClass addExp;
        addExp = readAddExp(curEnv);
        if (addExp == null) {
            throw new SyntaxException();
        }
        constExp.appendSonNode(addExp);
        constExp.setFirstAsLineNo();
        return constExp;
    }
}
