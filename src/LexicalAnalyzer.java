import Exceptions.LexicalException;
import SyntaxClasses.Token;

import java.util.ArrayList;
import java.util.LinkedList;

public class LexicalAnalyzer {
    private StringBuilder programStr;
    private LinkedList<Token> tokenList;
    private ArrayList<Error> errorList;

    public LexicalAnalyzer() {
        this.tokenList = new LinkedList<>();
        this.errorList = new ArrayList<>();
    }

    public void setProgramStr(StringBuilder programStr) {
        this.programStr = programStr;
    }

    public LinkedList<Token> getTokenList() {
        return tokenList;
    }

    public boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    public boolean isIdentNonDigit(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c == '_');
    }

    public boolean isIdentChar(char c) {
        return isIdentNonDigit(c) || isDigit(c);
    }

    public boolean isSpace(char c) {
        return c == ' ' || c == '\r' || c == '\t';
    }

    public void lexicalAnalyze() throws LexicalException {
        int lineNum = 1, pos = 0, progLen = this.programStr.length();
        char readChr;
        Token curToken;
        while (pos < progLen) {
            readChr = this.programStr.charAt(pos++);
            if (readChr == '+') {
                curToken = new Token(Token.PLUS, lineNum, "+");
            } else if (readChr == '-') {
                curToken = new Token(Token.MINU, lineNum, "-");
            } else if (readChr == '*') {
                curToken = new Token(Token.MULT, lineNum, "*");
            } else if (readChr == '/') {
                curToken = new Token(Token.DIV, lineNum, "/");
            } else if (readChr == '%') {
                curToken = new Token(Token.MOD, lineNum, "%");
            } else if (readChr == ';') {
                curToken = new Token(Token.SEMICN, lineNum, ";");
            } else if (readChr == ',') {
                curToken = new Token(Token.COMMA, lineNum, ",");
            } else if (readChr == '(') {
                curToken = new Token(Token.LPARENT, lineNum, "(");
            } else if (readChr == ')') {
                curToken = new Token(Token.RPARENT, lineNum, ")");
            } else if (readChr == '[') {
                curToken = new Token(Token.LBRACK, lineNum, "[");
            } else if (readChr == ']') {
                curToken = new Token(Token.RBRACK, lineNum, "]");
            } else if (readChr == '{') {
                curToken = new Token(Token.LBRACE, lineNum, "{");
            } else if (readChr == '}') {
                curToken = new Token(Token.RBRACE, lineNum, "}");
            } else if (readChr == '<') {
                if (pos == progLen || this.programStr.charAt(pos) != '=') {
                    curToken = new Token(Token.LSS, lineNum, "<");
                } else {
                    curToken = new Token(Token.LEQ, lineNum, "<=");
                    pos++;
                }
            } else if (readChr == '>') {
                if (pos == progLen || this.programStr.charAt(pos) != '=') {
                    curToken = new Token(Token.GRE, lineNum, ">");
                } else {
                    curToken = new Token(Token.GEQ, lineNum, ">=");
                    pos++;
                }
            } else if (readChr == '=') {
                if (pos == progLen || this.programStr.charAt(pos) != '=') {
                    curToken = new Token(Token.ASSIGN, lineNum, "=");
                } else {
                    curToken = new Token(Token.EQL, lineNum, "==");
                    pos++;
                }
            } else if (readChr == '!') {
                if (pos == progLen || this.programStr.charAt(pos) != '=') {
                    curToken = new Token(Token.NOT, lineNum, "!");
                } else {
                    curToken = new Token(Token.NEQ, lineNum, "!=");
                    pos++;
                }
            } else if (readChr == '&') {
                if (pos < progLen && this.programStr.charAt(pos) == '&') {
                    curToken = new Token(Token.AND, lineNum, "&&");
                    pos++;
                } else {
                    throw new LexicalException(lineNum);
                }
            } else if (readChr == '|') {
                if (pos < progLen && this.programStr.charAt(pos) == '|') {
                    curToken = new Token(Token.OR, lineNum, "||");
                    pos++;
                } else {
                    throw new LexicalException(lineNum);
                }
            } else if (readChr == '"') {
                int start = pos - 1;
                int status = 0;
                while (pos < progLen) {
                    char curChar = this.programStr.charAt(pos);
                    if (curChar == '"') {
                        break;
                    } else {
                        switch (status) {
                            case 0: // 正常状态
                                if (curChar == '\\') {
                                    status = 1;
                                } else if (curChar == '%') {
                                    status = 2;
                                } else if ((int) curChar < 40 || (int) curChar > 126) {
                                    if ((int) curChar != 32 && (int) curChar != 33) {
                                        status = 3;
                                    }
                                }
                                break;
                            case 1: // 收到一个\。需要一个n恢复正常
                                if (curChar == 'n') {
                                    status = 0;
                                } else {
                                    status = 3;
                                }
                                break;
                            case 2: // 收到一个%。需要一个d恢复正常
                                if (curChar == 'd') {
                                    status = 0;
                                } else {
                                    status = 3;
                                }
                                break;
                            case 3:
                                break;
                        }
                    }
                    ++pos;
                }
                if (status != 0) {
                    Error strconErr = new Error(0, lineNum);
                    errorList.add(strconErr);
                }
                if (this.programStr.charAt(pos - 1) == '"') {
                    String context = this.programStr.substring(start, pos);
                    curToken = new Token(Token.STRCON, lineNum, context);
                } else {
                    throw new LexicalException(lineNum);
                }
            } else if (isDigit(readChr)) {
                int start = pos - 1;
                while (pos < progLen) {
                    if (!isDigit(this.programStr.charAt(pos++))) {
                        pos--;
                        break;
                    }
                }
                curToken = new Token(Token.INTCON, lineNum, this.programStr.substring(start, pos));
                curToken.setConstValue(Integer.parseInt(curToken.getTokenContext())); // 词法分析时求IntConst的值
            } else if (isIdentNonDigit(readChr)) {
                int start = pos - 1;
                while (pos < progLen) {
                    if (!isIdentChar(this.programStr.charAt(pos++))) {
                        pos--;
                        break;
                    }
                }
                curToken = identAnalyze(lineNum, this.programStr.substring(start, pos));
            } else {
                if (readChr == '\n') {
                    lineNum++;
                }
                continue;
            }
            tokenList.addLast(curToken);
        }
    }

    public Token identAnalyze(int lineNum, String identStr) {
        if (identStr.matches("const")) {
            return new Token(Token.CONSTTK, lineNum, identStr);
        } else if (identStr.matches("int")) {
            return new Token(Token.INTTK, lineNum, identStr);
        } else if (identStr.matches("void")) {
            return new Token(Token.VOIDTK, lineNum, identStr);
        } else if (identStr.matches("if")) {
            return new Token(Token.IFTK, lineNum, identStr);
        } else if (identStr.matches("else")) {
            return new Token(Token.ELSETK, lineNum, identStr);
        } else if (identStr.matches("while")) {
            return new Token(Token.WHILETK, lineNum, identStr);
        } else if (identStr.matches("break")) {
            return new Token(Token.BREAKTK, lineNum, identStr);
        } else if (identStr.matches("continue")) {
            return new Token(Token.CONTINUETK, lineNum, identStr);
        } else if (identStr.matches("getint")) {
            return new Token(Token.GETINTTK, lineNum, identStr);
        } else if (identStr.matches("printf")) {
            return new Token(Token.PRINTFTK, lineNum, identStr);
        } else if (identStr.matches("return")) {
            return new Token(Token.RETURNTK, lineNum, identStr);
        } else if (identStr.matches("main")) {
            return new Token(Token.MAINTK, lineNum, identStr);
        } else {
            return new Token(Token.IDENFR, lineNum, identStr);
        }
    }
}
