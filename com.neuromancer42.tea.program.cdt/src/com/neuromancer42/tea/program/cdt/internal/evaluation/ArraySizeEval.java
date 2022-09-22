package com.neuromancer42.tea.program.cdt.internal.evaluation;

import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IType;

public class ArraySizeEval implements IEval {
    private final IASTExpression debugExpr;
    private final int arrReg;

    public ArraySizeEval(IASTExpression debugExpr, int arrReg) {
        this.debugExpr = debugExpr;
        this.arrReg = arrReg;
    }

    @Override
    public String toDebugString() {
        return "sizeof(#" + arrReg + ")" + debugExpr.getClass().getSimpleName() + "[" + debugExpr.getRawSignature() + "]";
    }

    @Override
    public int[] getOperands() {
        return new int[]{arrReg};
    }

    @Override
    public IASTExpression getExpression() {
        return debugExpr;
    }

    @Override
    public IType getType() {
        return debugExpr.getExpressionType();
    }
}
