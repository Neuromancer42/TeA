package com.neuromancer42.tea.program.cdt.internal.evaluation;

import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IType;

public class BinaryEval implements IEval {

    private final String operator;
    private final int operandL;
    private final int operandR;
    private final IASTExpression debugExpr;

    public static final String op_plus = "+";
    public static final String op_minus = "-";
    public static final String op_multiply = "*";
    public static final String op_divide = "div";
    public static final String op_modulo = "mod";
    public static final String op_and = "&&";
    public static final String op_or = "||";
    public static final String op_eq = "==";
    public static final String op_ne = "!=";
    public static final String op_lt = "<";
    public static final String op_le = "<=";
    public static final String op_bit = "bit";
    public BinaryEval(IASTExpression expr, String op, int regL, int regR) {
        this.operator = op;
        this.operandL = regL;
        this.operandR= regR;
        this.debugExpr = expr;
    }

    public String getOperator() {
        return operator;
    }

    public int getLeftOperand() {
        return operandL;
    }

    public int getRightOperand() {
        return operandR;
    }

    @Override
    public String toDebugString() {
        return operator + "(#" + operandL + ",#" + operandR + ")" + debugExpr.getClass().getSimpleName() +  "[" + debugExpr.getRawSignature() + "]";
    }

    @Override
    public int[] getOperands() {
        return new int[]{operandL, operandR};
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
