package com.neuromancer42.tea.program.cdt.parser.evaluation;

import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IASTLiteralExpression;
import org.eclipse.cdt.core.dom.ast.IType;

public class ConstantEval implements IEval {
    private final IASTExpression expr;
    private final String val;

    public ConstantEval(IASTLiteralExpression expr) {
        this.expr = expr;
        this.val = String.valueOf(expr.getValue());
    }

    public ConstantEval(IASTExpression expr, String val) {
        this.expr = expr;
        this.val = val;
    }

    public String getValue() {
        return val;
    }

    @Override
    public String toDebugString() {
        return "literal:" + val + "=" + expr.getClass().getSimpleName() + "[" + expr.getRawSignature() + "]";
    }

    @Override
    public int[] getOperands() {
        return new int[0];
    }

    @Override
    public IASTExpression getExpression() {
        return expr;
    }

    @Override
    public IType getType() {
        return expr.getExpressionType();
    }
}
