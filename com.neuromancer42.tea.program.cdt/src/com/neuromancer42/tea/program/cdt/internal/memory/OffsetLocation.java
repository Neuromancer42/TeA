package com.neuromancer42.tea.program.cdt.internal.memory;

import com.neuromancer42.tea.core.project.Messages;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IArrayType;
import org.eclipse.cdt.core.dom.ast.IPointerType;
import org.eclipse.cdt.core.dom.ast.IType;

public class OffsetLocation implements ILocation {
    private final ILocation base;
    private final IASTExpression offsetExpr;
    private final int offsetReg;

    public OffsetLocation(ILocation base, IASTExpression offsetExpr, int offsetReg) {
        this.base = base;
        this.offsetExpr = offsetExpr;
        this.offsetReg = offsetReg;
    }

    public ILocation getBaseLocation() {
        return base;
    }

    public IASTExpression getOffsetExpression() {
        return offsetExpr;
    }

    public int getOffsetRegister() {
        return offsetReg;
    }

    @Override
    public boolean isStatic() {
        return base.isStatic();
    }

    @Override
    public String toDebugString() {
        return base.toDebugString() + "+" + offsetExpr.getClass().getSimpleName() + "[" + offsetExpr.getRawSignature() + "]@" + offsetReg;
    }

    @Override
    public IType getType() {
        return base.getType();
    }

    @Override
    public int[] getParameters() {
        int[] baseParams = base.getParameters();
        int[] params = new int[baseParams.length + 1];
        params[0] = offsetReg;
        System.arraycopy(baseParams, 0, params, 1, baseParams.length);
        return params;
    }

}
