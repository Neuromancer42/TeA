package com.neuromancer42.tea.program.cdt.internal.evaluation;

import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IType;

public class UnaryEval implements IEval {
    private final String operator;
    private final int operand;
    private final IASTExpression debugExpr;

    public static final String op_incr = "++";
    public static final String op_decr = "--";
    public static final String op_plus = "0+";
    public static final String op_minus = "0-";
    public static final String op_not = "!";

    public UnaryEval(IASTExpression expr, String op, int reg) {
        this.operator = op;
        this.operand = reg;
        this.debugExpr = expr;
    }

    public String getOperator() {
        return operator;
    }

    public int getOperand() {
        return operand;
    }

    @Override
    public String toDebugString() {
        return operator + "(#" + operand + ")" + debugExpr.getClass().getSimpleName() +  "[" + debugExpr.getRawSignature() + "]";
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
