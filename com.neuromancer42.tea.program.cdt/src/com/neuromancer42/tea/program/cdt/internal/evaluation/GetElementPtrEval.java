package com.neuromancer42.tea.program.cdt.internal.evaluation;

import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.IVariable;

public abstract class GetElementPtrEval implements IEval {
    private final IASTExpression baseExpr;
    private final IVariable baseVar;
    protected final int basePtrReg;

    public GetElementPtrEval(IASTExpression expr, int reg) {
        this.baseExpr = expr;
        this.baseVar = null;
        this.basePtrReg = reg;
    }

    public GetElementPtrEval(IVariable variable, int refReg) {
        this.baseExpr = null;
        this.baseVar = variable;
        this.basePtrReg = refReg;

    }

    public int getBasePtr() {
        return basePtrReg;
    }

    protected String toBaseDebugString() {
        if (baseExpr != null) {
            return baseExpr.getClass().getSimpleName() +  "[" + baseExpr.getRawSignature() + "]";
        } else {
            assert baseVar != null;
            return "ref-" + baseVar.getClass().getSimpleName() + "[" + baseVar + "]";
        }
    }

    protected IType getBaseType() {
        if (baseExpr != null) {
            return baseExpr.getExpressionType();
        } else {
            assert baseVar != null;
            return baseVar.getType();
        }
    }
}
