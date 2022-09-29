package com.neuromancer42.tea.program.cdt.parser.evaluation;

import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IField;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.internal.core.dom.parser.c.CPointerType;

public class GetFieldPtrEval extends GetElementPtrEval {
    private final IField field;

    public GetFieldPtrEval(IType baseType, int baseReg, IField field) {
        super(baseType, baseReg);
        this.field = field;
    }

    public IField getField() {
        return field;
    }
    @Override
    public int[] getOperands() {
        return new int[]{basePtrReg};
    }

    @Override
    public IASTExpression getExpression() {
        return null;
    }

    @Override
    public IType getType() {
        IType pointedType = field.getType();
        return new CPointerType(pointedType, 0);
    }

    @Override
    public String toDebugString() {
        return "gepField(" + toBaseDebugString() + "." + field + ")";
    }
}
