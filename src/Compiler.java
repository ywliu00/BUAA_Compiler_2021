import Exceptions.LexicalException;
import Exceptions.SyntaxException;
import SyntaxClasses.SyntaxClass;
import SyntaxClasses.Token;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedList;

public class Compiler {
    public static void main(String[] argv) {
        PrintStream ps = null;
        try {
            ps = new PrintStream(new FileOutputStream("output.txt"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        System.setOut(ps);

        ReadFile readFile = new ReadFile("testfile.txt");
        LexicalAnalyzer lexicalAnalyzer = new LexicalAnalyzer();
        SyntaxAnalyzer syntaxAnalyzer = new SyntaxAnalyzer();
        ArrayList<Error> errorList = new ArrayList<>();
        syntaxAnalyzer.setErrorList(errorList);
        StringBuilder myProgram = readFile.readFile();
        lexicalAnalyzer.setProgramStr(myProgram);
        try {
            lexicalAnalyzer.lexicalAnalyze();
        } catch (LexicalException e) {
            e.printStackTrace();
        }
        LinkedList<Token> tokenList = lexicalAnalyzer.getTokenList();
        /*for (Token token : tokenList) {
            System.out.println(token);
        }*/
        syntaxAnalyzer.setTokenList(tokenList);
        try {
            syntaxAnalyzer.syntaxAnalyze();
        } catch (SyntaxException e) {
            System.out.println(tokenList.get(syntaxAnalyzer.getPos()));
            e.printStackTrace();
        }
        SyntaxClass compUnit = syntaxAnalyzer.getGlobalCompUnit();
        System.out.println(compUnit);
    }
}
