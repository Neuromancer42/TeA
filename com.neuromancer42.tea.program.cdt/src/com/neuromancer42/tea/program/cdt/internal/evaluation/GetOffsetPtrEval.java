package com.neuromancer42.tea.program.cdt.internal.evaluation;

import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.internal.core.dom.parser.ITypeContainer;
import org.eclipse.cdt.internal.core.dom.parser.c.CPointerType;

public class GetOffsetPtrEval extends GetElementPtrEval {
    private final IASTExpression offsetExpr;
    private final int offsetReg;
    private final int literalOffset;

    // offset as a offset
    public GetOffsetPtrEval(IType baseType, int baseReg, IASTExpression offsetExpr, int offsetReg) {
        super(baseType, baseReg);
        this.offsetExpr = offsetExpr;
        this.offsetReg = offsetReg;
        this.literalOffset = -1;
    }

    // common list initialization
    public GetOffsetPtrEval(IType baseType, int baseReg, int literalOffset) {
        super(baseType, baseReg);
        this.offsetExpr = null;
        this.offsetReg = -1;
        this.literalOffset = literalOffset;
    }

    public int getOffset() {
        return offsetReg;
    }

    @Override
    public int[] getOperands() {
        int[] oprands;
        if (offsetReg >= 0) {
            oprands = new int[]{basePtrReg, offsetReg};
        } else {
            oprands = new int[]{basePtrReg};
        }
        return oprands;
    }

    @Override
    public IASTExpression getExpression() {
        return null;
    }

    @Override
    public IType getType() {
        IType pointedType = ((ITypeContainer) getBaseType()).getType();
        return new CPointerType(pointedType, 0);
    }

    @Override
    public String toDebugString() {
        return "gepOffset(" + toBaseDebugString() + "+#" + offsetReg + ")";
    }
}
