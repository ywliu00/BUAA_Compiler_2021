import Exceptions.SyntaxException;
import SyntaxClasses.SyntaxClass;
import SyntaxClasses.Token;

import java.util.LinkedList;

public class SyntaxAnalyzer {
    private LinkedList<Token> tokenList;
    private SyntaxClass globalCompUnit;
    private int pos;

    public void setTokenList(LinkedList<Token> tokenList) {
        this.tokenList = tokenList;
        this.globalCompUnit = null;
        this.pos = 0;
    }

    public SyntaxClass readCompUnit() throws SyntaxException {
        int startPos = this.pos;
        SyntaxClass compUnit = new SyntaxClass();
        compUnit.setSyntaxType(SyntaxClass.COMPUNIT);
        if (pos >= tokenList.size()) {
            return null;
        }
        // 检查是否有Decl成分
        SyntaxClass decl = null;
        while (true) {
            decl = readDecl();
            if (decl == null) break;
            compUnit.appendSonNode(decl);
        }
        // 检查是否有FuncDef成分
        SyntaxClass funcDef = null;
        while (true) {
            funcDef = readFuncDef();
            if (funcDef == null) break;
            compUnit.appendSonNode(funcDef);
        }
        // 分析MainFuncDef成分
        SyntaxClass mainFuncDef;
        mainFuncDef = readMainFuncDef();
        if (mainFuncDef == null) {
            throw new SyntaxException();
        }
        compUnit.appendSonNode(mainFuncDef);
        compUnit.setFirstAsLineNo(); // 设置非终结符行号
        return compUnit;
    }

    public SyntaxClass readDecl() throws SyntaxException {
        SyntaxClass decl = new SyntaxClass(SyntaxClass.DECL);
        if (pos >= tokenList.size()) {
            return null;
        }
        // 开头是const，说明是ConstDecl
        if (tokenList.get(pos).getTokenType() == Token.CONSTTK) {
            SyntaxClass constDecl;
            constDecl = readConstDecl();
            if (constDecl == null) {
                throw new SyntaxException();
            } else {
                decl.appendSonNode(constDecl);
            }
        } else { // 否则是VarDecl
            SyntaxClass varDecl;
            varDecl = readVarDecl();
            if (varDecl == null) {
                throw new SyntaxException();
            } else {
                decl.appendSonNode(varDecl);
            }
        }
        decl.setFirstAsLineNo();
        return decl;
    }

    public SyntaxClass readConstDecl() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }

        // 检查是否以const开头
        if (tokenList.get(pos).getTokenType() != Token.CONSTTK) {
            return null;
        }
        int startPos = this.pos;
        SyntaxClass constDecl = new SyntaxClass(SyntaxClass.CONSTDECL),
                bType, constDef;
        constDecl.appendSonNode(tokenList.get(this.pos++));
        // 检查BType
        bType = readBType();
        if (bType == null) {
            throw new SyntaxException();
        } else {
            constDecl.appendSonNode(bType);
        }
        // 检查必需的constDef
        constDef = readConstDef();
        if (constDef == null) {
            throw new SyntaxException();
        } else {
            constDecl.appendSonNode(constDef);
        }

        // 如果有逗号就继续
        while (tokenList.get(pos).getTokenType() == Token.COMMA) {
            Token comma = tokenList.get(pos++);
            constDef = readConstDef();
            if (constDef == null) {
                throw new SyntaxException();
            } else {
                constDecl.appendSonNode(comma);
                constDecl.appendSonNode(constDef);
            }
        }

        // 检测分号
        if (tokenList.get(pos).getTokenType() != Token.SEMICN) {
            throw new SyntaxException();
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

    public SyntaxClass readConstDef() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass constDef = new SyntaxClass(SyntaxClass.CONSTDEF);
        Token ident = tokenList.get(pos);
        // 检查是否是标识符
        if (ident.getTokenType() != Token.IDENFR) {
            throw new SyntaxException();
        } else {
            ++pos;
            constDef.appendSonNode(ident);
        }
        // 检查是否有左中括号
        Token token = tokenList.get(pos);
        while (token.getTokenType() == Token.LBRACK) {
            // 若有，检查ConstExp和右中括号
            ++pos;
            SyntaxClass constExp;
            constExp = readConstExp();
            Token rbrack = tokenList.get(pos);
            if (rbrack.getTokenType() != Token.RBRACK) {
                throw new SyntaxException();
            } else {
                ++pos;
                constDef.appendSonNode(token);
                constDef.appendSonNode(constExp);
                constDef.appendSonNode(rbrack);
            }
        }
        // 检查是否是 =
        if (token.getTokenType() != Token.EQL) {
            throw new SyntaxException();
        } else {
            ++pos;
            constDef.appendSonNode(token);
            SyntaxClass constInitVal;
            // 检查constInitVal
            constInitVal = readConstInitVal();
            if (constInitVal == null) {
                throw new SyntaxException();
            } else {
                constDef.appendSonNode(constInitVal);
            }
        }
        constDef.setFirstAsLineNo();
        return constDef;
    }

    public SyntaxClass readConstInitVal() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass constInitVal = new SyntaxClass(SyntaxClass.CONSTINITVAL);
        // 检查是否是左花括号
        if (tokenList.get(pos).getTokenType() == Token.LBRACE) {
            Token token = tokenList.get(pos++);
            constInitVal.appendSonNode(token);
            // 先看看是不是右花括号，如果不是，说明中间有内容
            if (tokenList.get(pos).getTokenType() != Token.RBRACE) {
                // 检查是否是ConstInitVal
                SyntaxClass subConstInitVal;
                subConstInitVal = readConstInitVal();
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
                    subConstInitVal = readConstInitVal();
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
            constExp = readConstExp();
            if (constExp == null) {
                throw new SyntaxException();
            } else {
                constInitVal.appendSonNode(constExp);
            }
        }
        constInitVal.setFirstAsLineNo();
        return constInitVal;
    }

    public SyntaxClass readVarDecl() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass varDecl = new SyntaxClass(SyntaxClass.VARDECL);
        SyntaxClass bType, varDef;
        // 检查BType
        bType = readBType();
        if (bType == null) {
            throw new SyntaxException();
        } else {
            varDecl.appendSonNode(bType);
        }
        // 检查VarDef
        varDef = readVarDef();
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
            varDef = readVarDef();
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
        } else {
            throw new SyntaxException();
        }
        varDecl.setFirstAsLineNo();
        return varDecl;
    }

    public SyntaxClass readVarDef() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass varDef = new SyntaxClass(SyntaxClass.VARDEF)
        Token ident = tokenList.get(pos);
        // 检查是否是标识符
        if (ident.getTokenType() != Token.IDENFR) {
            throw new SyntaxException();
        } else {
            ++pos;
            varDef.appendSonNode(ident);
        }
        // 检查是否有左中括号
        Token token = tokenList.get(pos);
        while (token.getTokenType() == Token.LBRACK) {
            // 若有，检查ConstExp和右中括号
            ++pos;
            SyntaxClass constExp;
            constExp = readConstExp();
            Token rbrack = tokenList.get(pos);
            if (rbrack.getTokenType() != Token.RBRACK) {
                throw new SyntaxException();
            } else {
                ++pos;
                varDef.appendSonNode(token);
                varDef.appendSonNode(constExp);
                varDef.appendSonNode(rbrack);
            }
        }
        // 如果是 =
        if (token.getTokenType() == Token.EQL) {
            ++pos;
            varDef.appendSonNode(token);
            SyntaxClass initVal;
            // 检查InitVal
            initVal = readInitVal();
            if (initVal == null) {
                throw new SyntaxException();
            } else {
                varDef.appendSonNode(initVal);
            }
        }
        varDef.setFirstAsLineNo();
        return varDef;
    }

    public SyntaxClass readInitVal() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass initVal = new SyntaxClass(SyntaxClass.INITVAL);
        Token token;
        // 检查是否是左花括号
        if (tokenList.get(pos).getTokenType() == Token.LBRACE) {
            token = tokenList.get(pos++);
            initVal.appendSonNode(token);
            // 如果不是右花括号，说明中间有东西
            if (tokenList.get(pos).getTokenType() != Token.RBRACE) {
                // 检查必须存在的InitVal
                SyntaxClass subInitVal;
                subInitVal = readInitVal();
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
                    subInitVal = readInitVal();
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
            exp = readExp();
            if (exp == null) {
                throw new SyntaxException();
            } else {
                initVal.appendSonNode(exp);
            }
        }
        initVal.setFirstAsLineNo();
        return initVal;
    }

    public SyntaxClass readFuncDef() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass funcDef = new SyntaxClass(SyntaxClass.FUNCDEF);
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
            funcFParams = readFuncFParams();
            if (funcFParams == null) {
                throw new SyntaxException();
            } else {
                funcDef.appendSonNode(funcFParams);
            }
        }
        // 检查右括号
        if (tokenList.get(pos).getTokenType() != Token.RPARENT) {
            throw new SyntaxException();
        } else {
            Token ident;
            ident = tokenList.get(pos++);
            funcDef.appendSonNode(ident);
        }
        // 检查Block
        block = readBlock();
        if (block == null) {
            throw new SyntaxException();
        } else {
            funcDef.appendSonNode(block);
        }
        funcDef.setFirstAsLineNo();
        return funcDef;
    }

    public SyntaxClass readMainFuncDef() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass mainFuncDef = new SyntaxClass(SyntaxClass.MAINFUNCDEF);
        SyntaxClass block;
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
            Token ident;
            ident = tokenList.get(pos++);
            mainFuncDef.appendSonNode(ident);
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
            throw new SyntaxException();
        } else {
            Token ident;
            ident = tokenList.get(pos++);
            mainFuncDef.appendSonNode(ident);
        }
        // 检查Block
        block = readBlock();
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

    public SyntaxClass readFuncFParams() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass funcFParams = new SyntaxClass(SyntaxClass.FUNCFPARAMS);
        SyntaxClass param;
        // 检查必须有的FuncFParam
        param = readFuncFParam();
        if (param == null) {
            throw new SyntaxException();
        } else {
            funcFParams.appendSonNode(param);
        }
        // 如果有逗号，说明后面还有
        while (tokenList.get(pos).getTokenType() == Token.COMMA) {
            Token token = tokenList.get(pos++);
            funcFParams.appendSonNode(token);
            param = readFuncFParam();
            if (param == null) {
                throw new SyntaxException();
            } else {
                funcFParams.appendSonNode(param);
            }
        }
        funcFParams.setFirstAsLineNo();
        return funcFParams;
    }

    public SyntaxClass readFuncFParam() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass funcFParam = new SyntaxClass(SyntaxClass.FUNCFPARAM);
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
            Token ident = tokenList.get(pos++);
            funcFParam.appendSonNode(ident);
        } else {
            throw new SyntaxException();
        }
        // 如果有左中括号
        if (tokenList.get(pos).getTokenType() == Token.LBRACK) {
            Token ident = tokenList.get(pos++);
            funcFParam.appendSonNode(ident);
            // 需要跟一个右中括号
            if (tokenList.get(pos).getTokenType() == Token.RBRACK) {
                ident = tokenList.get(pos++);
                funcFParam.appendSonNode(ident);
            } else {
                throw new SyntaxException();
            }
            // 如果还有左中括号
            while(tokenList.get(pos).getTokenType() == Token.LBRACK) {
                ident = tokenList.get(pos++);
                funcFParam.appendSonNode(ident);
                // 检查一个ConstExp
                SyntaxClass constExp;
                constExp = readConstExp();
                if (constExp == null) {
                    throw new SyntaxException();
                }
                funcFParam.appendSonNode(constExp);
                // 检查右中括号
                if (tokenList.get(pos).getTokenType() == Token.RBRACK) {
                    ident = tokenList.get(pos++);
                    funcFParam.appendSonNode(ident);
                } else {
                    throw new SyntaxException();
                }
            }
        }
        funcFParam.setFirstAsLineNo();
        return funcFParam;
    }

    public SyntaxClass readBlock() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass block = new SyntaxClass(SyntaxClass.BLOCK);
        // 检查左花括号
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
                blockItem = readBlockItem();
                if (blockItem == null) {
                    throw new SyntaxException();
                } else {
                    block.appendSonNode(blockItem);
                }
            }
            // 右花括号
            if (tokenList.get(pos).getTokenType() == Token.RBRACE) {
                brace = tokenList.get(pos++);
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

    public SyntaxClass readBlockItem() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass blockItem = new SyntaxClass(SyntaxClass.BLOCKITEM);
        SyntaxClass syntaxClass;
        // 尝试解析Decl
        syntaxClass = readDecl();
        if (syntaxClass != null) {
            blockItem.appendSonNode(syntaxClass);
        } else {
            // 尝试解析Stmt
            syntaxClass = readStmt();
            if (syntaxClass != null) {
                blockItem.appendSonNode(syntaxClass);
            } else {
                throw new SyntaxException();
            }
        }
        blockItem.setFirstAsLineNo();
        return blockItem;
    }

    public SyntaxClass readStmt() throws SyntaxException {
        if (pos >= tokenList.size()) {
            return null;
        }
        SyntaxClass stmt = new SyntaxClass(SyntaxClass.STMT);
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
            SyntaxClass cond = readCond();
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
                throw new SyntaxException();
            }
            // Stmt
            SyntaxClass subStmt = readCond();
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
                subStmt = readCond();
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
            SyntaxClass cond = readCond();
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
                throw new SyntaxException();
            }
            // Stmt
            SyntaxClass subStmt = readCond();
            if (subStmt == null) {
                throw new SyntaxException();
            } else {
                stmt.appendSonNode(subStmt);
            }
        } else if (nextTokenType == Token.BREAKTK) {
            // Break
            Token token = tokenList.get(pos++);
            stmt.appendSonNode(token);
            // 分号
            if (tokenList.get(pos).getTokenType() == Token.LPARENT) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                throw new SyntaxException();
            }
        } else if (nextTokenType == Token.CONTINUETK) {
            // Continue
            Token token = tokenList.get(pos++);
            stmt.appendSonNode(token);
            // 分号
            if (tokenList.get(pos).getTokenType() == Token.LPARENT) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                throw new SyntaxException();
            }
        } else if (nextTokenType == Token.RETURNTK) {
            // Return
            Token token = tokenList.get(pos++);
            stmt.appendSonNode(token);
            // 分号
            if (tokenList.get(pos).getTokenType() == Token.LPARENT) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                // 没有分号，说明有返回值
                SyntaxClass exp;
                exp = readExp();
                if (exp == null) {
                    throw new SyntaxException();
                } else {
                    stmt.appendSonNode(exp);
                }
                // 分号
                if (tokenList.get(pos).getTokenType() == Token.LPARENT) {
                    token = tokenList.get(pos++);
                    stmt.appendSonNode(token);
                } else {
                    throw new SyntaxException();
                }
            }
        } else if (nextTokenType == Token.PRINTFTK) {
            // Printf
            Token token = tokenList.get(pos++);
            stmt.appendSonNode(token);
            // 左括号
            if (tokenList.get(pos).getTokenType() == Token.LPARENT) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                throw new SyntaxException();
            }
            // FormatString
            if (tokenList.get(pos).getTokenType() == Token.STRCON) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                throw new SyntaxException();
            }
            // 如果有逗号
            while (tokenList.get(pos).getTokenType() == Token.COMMA) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
                // 需要有Exp
                SyntaxClass formatStr = readExp();
                if (formatStr == null) {
                    throw new SyntaxException();
                } else {
                    stmt.appendSonNode(formatStr);
                }
            }
            // 右括号
            if (tokenList.get(pos).getTokenType() == Token.RPARENT) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                throw new SyntaxException();
            }
            // 分号
            if (tokenList.get(pos).getTokenType() == Token.SEMICN) {
                token = tokenList.get(pos++);
                stmt.appendSonNode(token);
            } else {
                throw new SyntaxException();
            }
        }
    }

    public SyntaxClass readExp() {
    }

    public SyntaxClass readCond() {}

    public SyntaxClass readConstExp() {
    }
}
