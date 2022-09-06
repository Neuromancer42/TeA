package com.neuromancer42.tea.program.cdt.internal.evaluation;

import com.neuromancer42.tea.program.cdt.internal.memory.ILocation;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IType;

public class LoadEval implements IEval {
    private final ILocation location;
    private final IASTExpression debugExpr;

    public LoadEval(IASTExpression expr, ILocation loc) {
        this.location = loc;
        this.debugExpr = expr;
    }

    public ILocation getLocation() {
        return location;
    }

    @Override
    public String toDebugString() {
        return "load(" + location.toDebugString() + ")" + debugExpr.getClass().getSimpleName() + "[" + debugExpr.getRawSignature() + "]";
    }

    @Override
    public int[] getOperands() {
        return location.getParameters();
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
