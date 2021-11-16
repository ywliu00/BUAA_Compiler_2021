import Exceptions.LexicalException;
import Exceptions.SyntaxException;
import SyntaxClasses.SyntaxClass;
import SyntaxClasses.Token;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;

public class Compiler {
    public static void main(String[] argv) {
        boolean isDebug = false;

        PrintStream ps = null;
        try {
            ps = new PrintStream(new FileOutputStream("mips.txt"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        System.setOut(ps);

        ReadFile readFile;
        if (!isDebug) {
            readFile = new ReadFile("testfile.txt");
        } else {
            readFile = new ReadFile("C/testfile15.txt");
        }
        LexicalAnalyzer lexicalAnalyzer = new LexicalAnalyzer();
        SyntaxAnalyzer syntaxAnalyzer = new SyntaxAnalyzer();
        ArrayList<Error> errorList = new ArrayList<>();
        lexicalAnalyzer.setErrorList(errorList);
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
        CompUnitSimplifyer.compUnitSimplify(compUnit);

        IRTranslater irTranslater = new IRTranslater(compUnit);
        irTranslater.compUnitTrans();

        if (isDebug) {
            StringBuilder irStr = irTranslater.outputIR();
            File irFile = new File("IR.txt");
            try {
                FileOutputStream irOutput = new FileOutputStream(irFile);
                irOutput.write(irStr.toString().getBytes());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        MIPSTranslater mipsTranslater = new MIPSTranslater(irTranslater);
        StringBuilder mipsStr = mipsTranslater.iRTranslate();

        System.out.println(mipsStr);
        //Collections.sort(errorList);
        /*for (Error err : errorList) {
            System.out.println(err);
        }*/
        /*StringBuilder afterStrBuilder = CompUnitSimplifyer.printUnit(compUnit);
        System.out.println(afterStrBuilder);*/
        // System.out.println(compUnit);
    }
}
