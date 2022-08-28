package com.neuromancer42.tea.program.cdt.internal.evaluation;

import com.neuromancer42.tea.core.project.Messages;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IType;

public class BlackholeEval implements IEval {
    private final IASTExpression debugExpr;

    public BlackholeEval(IASTExpression expression) {
        this.debugExpr = expression;
    }

    @Override
    public String toDebugString() {
        return "blackhole";
    }

    @Override
    public int[] getOperands() {
        return new int[0];
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
