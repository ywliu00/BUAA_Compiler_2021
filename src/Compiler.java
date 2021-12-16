import Exceptions.LexicalException;
import Exceptions.SyntaxException;
import IR.CompUnitSimplifyer;
import IR.IRTranslater;
import MIPSTranslatePackage.IRProcessor;
import Optimizer.IROptimizer;
import SyntaxClasses.SyntaxClass;
import SyntaxClasses.Token;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

public class Compiler {
    public static void main(String[] argv) {
        boolean isDebug = false;

        /*PrintStream ps = null;
        try {
            ps = new PrintStream(new FileOutputStream("mips.txt"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
        System.setOut(ps);*/

        ReadFile readFile;
        if (!isDebug) {
            readFile = new ReadFile("testfile.txt");
        } else {
            readFile = new ReadFile("C/testfile28.txt");
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
            StringBuilder irStr = irTranslater.outputIR(irTranslater.getIRList());
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

        IROptimizer optimizer = new IROptimizer(irTranslater);
        optimizer.doOptimize();


        if (isDebug) {
            //StringBuilder irStr = irTranslater.outputIR();
            StringBuilder irStr = optimizer.printBasicBlock(true, optimizer.getBlockList());
            File irFile = new File("IR_Opt_Block.txt");
            try {
                FileOutputStream irOutput = new FileOutputStream(irFile);
                irOutput.write(irStr.toString().getBytes());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (isDebug) {
            StringBuilder irStr = irTranslater.outputIR(irTranslater.getIRList());
            File irFile = new File("IR_Opt.txt");
            try {
                FileOutputStream irOutput = new FileOutputStream(irFile);
                irOutput.write(irStr.toString().getBytes());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        /*IRProcessor irProcessor = new IRProcessor(irTranslater);
        irProcessor.flushEnv();
        if (isDebug) {
            StringBuilder processedStr = optimizer.printBasicBlock(false, irProcessor.getBlockList());
            File irFile2 = new File("IR_Opt_processed.txt");
            try {
                FileOutputStream irOutput = new FileOutputStream(irFile2);
                irOutput.write(processedStr.toString().getBytes());
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

            StringBuilder irStr = irTranslater.outputIR(irTranslater.getIRList());
            File irFile = new File("finalIR.txt");
            try {
                FileOutputStream irOutput = new FileOutputStream(irFile);
                irOutput.write(irStr.toString().getBytes());
                irOutput.close();
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }*/

        //MIPSTranslater mipsTranslater = new MIPSTranslater(irTranslater);
        MIPSTranslatorWithReg mipsTranslater = new MIPSTranslatorWithReg(irTranslater);
        StringBuilder mipsStr = mipsTranslater.iRTranslate();

        //System.out.println(mipsStr);
        //StringBuilder irStr = irTranslater.outputIR();
        File mipsFile = new File("mips.txt");
        try {
            FileOutputStream irOutput = new FileOutputStream(mipsFile);
            irOutput.write(mipsStr.toString().getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Collections.sort(errorList);
        /*for (Error err : errorList) {
            System.out.println(err);
        }*/
        /*StringBuilder afterStrBuilder = IR.CompUnitSimplifyer.printUnit(compUnit);
        System.out.println(afterStrBuilder);*/
        // System.out.println(compUnit);
    }
}
