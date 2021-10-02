import Exceptions.LexicalException;
import SyntaxClasses.Token;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
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
        StringBuilder myProgram = readFile.readFile();
        lexicalAnalyzer.setProgramStr(myProgram);
        try {
            lexicalAnalyzer.lexicalAnalyze();
        } catch (LexicalException e) {
            e.printStackTrace();
        }
        LinkedList<Token> tokenList = lexicalAnalyzer.getTokenList();
        for (Token token : tokenList) {
            System.out.println(token);
        }

    }
}
