package com.neuromancer42.tea.program.cdt.internal.evaluation;

import com.neuromancer42.tea.program.cdt.internal.memory.ILocation;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.IVariable;

public class AddressEval implements IEval {
    private final IASTExpression debugExpr;
    private final IVariable variable;
    private final ILocation location;

    public AddressEval(IASTExpression expr, ILocation loc) {
        this.debugExpr = expr;
        this.variable = null;
        this.location = loc;
    }

    public AddressEval(IVariable variable, ILocation loc) {
        this.debugExpr = null;
        this.variable = variable;
        this.location = loc;

    }

    @Override
    public String toDebugString() {
        if (debugExpr != null) {
            return "addr(" + location.toDebugString() + ")" + debugExpr.getClass().getSimpleName() +  "[" + debugExpr.getRawSignature() + "]";
        } else {
            assert variable != null;
            return "addr(" + location.toDebugString() + ")" + "ref-" + variable.getClass().getSimpleName() + "[" + variable + "]";
        }
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
        if (debugExpr != null) {
            return debugExpr.getExpressionType();
        } else {
            assert variable != null;
            return variable.getType();
        }
    }

    public ILocation getLocation() {
        return location;
    }
}
