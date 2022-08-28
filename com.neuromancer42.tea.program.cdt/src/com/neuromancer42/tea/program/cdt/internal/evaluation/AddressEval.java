package com.neuromancer42.tea.program.cdt.internal.evaluation;

import com.neuromancer42.tea.program.cdt.internal.memory.ILocation;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IType;

public class AddressEval implements IEval {
    private final IASTExpression debugExpr;
    private final ILocation location;

    public AddressEval(IASTExpression expr, ILocation loc) {
        this.debugExpr = expr;
        this.location = loc;
    }

    @Override
    public String toDebugString() {
        return "addr(" + location.toDebugString() + ")" + debugExpr.getClass().getSimpleName() +  "[" + debugExpr.getRawSignature() + "]";
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
