package com.neuromancer42.tea.program.cdt.parser.evaluation;

import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IType;

public class CastEval implements IEval {
    private final IASTExpression debugExpr;
    private final int operand;

    public CastEval(IASTExpression debugExpr, int reg) {
        this.debugExpr = debugExpr;
        this.operand = reg;
    }

    @Override
    public String toDebugString() {
        return "cast(" + getType() + ",#" + operand + ")" + debugExpr.getClass().getSimpleName() +  "[" + debugExpr.getRawSignature() + "]";
    }

    @Override
    public int[] getOperands() {
        return new int[]{operand};
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
