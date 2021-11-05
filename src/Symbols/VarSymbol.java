package Symbols;

import SyntaxClasses.Token;

import java.util.ArrayList;

public class VarSymbol extends Symbol {
    /*
     * dim约定：
     * 一维数组a[n]，长度n是第0维长度
     * 二维数组a[m][n]，m是第1维长度，n是第0维长度
     */
    private SymbolType varType;
    private int[] dimLength;
    private boolean constHasValue;
    private int[][] constValue;

    public VarSymbol(Token token, int isVar, int dimType) {
        super(token, 0);
        varType = new SymbolType(dimType, isVar);
        dimLength = new int[]{0, 0, 0};
        constHasValue = false;
    }

    public void setDimLengthByDim(int dim, int length) {
        this.dimLength[dim] = length;
    }

    public boolean isVar() {
        return varType.isVar();
    }

    public int getDimType() {
        return varType.getDimType();
    }

    public int getDimLength(int dim) {
        return dimLength[dim];
    }

    public void setConstValue(int[][] constValue) {
        this.constValue = constValue;
    }

    public void set0DimConstValue(int value) {
        constValue = new int[1][1];
        constValue[0][0] = value;
        constHasValue = true;
    }

    public void set1DimConstValue(ArrayList<Integer> initValArr, int dim0Length) {
        constValue = new int[1][dim0Length];
        for (int i = 0; i < dim0Length; ++i) {
            constValue[0][i] = initValArr.get(i);
        }
        constHasValue = true;
    }

    public void set2DimConstValue(ArrayList<ArrayList<Integer>> initValArr, int dim1Length, int dim0Length) {
        constValue = new int[dim1Length][dim0Length];
        for (int i = 0; i < dim1Length; ++i) {
            ArrayList<Integer> curList = initValArr.get(i);
            for (int j = 0; j < dim0Length; ++j) {
                constValue[i][j] = curList.get(j);
            }
        }
        constHasValue = true;
    }

    public boolean hasConstValue() {
        return constHasValue;
    }

    public int constGetValue() {
        return constValue[0][0];
    }

    public int constGetValue(int dim0) {
        return constValue[0][dim0];
    }

    public int constGetValue(int dim1, int dim0) {
        return constValue[dim1][dim0];
    }
}
