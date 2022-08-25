package com.neuromancer42.tea.program.cdt.internal.memory;

import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.internal.core.dom.parser.ITypeContainer;

// a memory location referenced by a pointer
public class PointerStorage implements IStorage {
    private final IASTExpression ptrExpr;
    private final int ptrReg;

    public PointerStorage(IASTExpression ptrExpr, int ptrReg) {
        this.ptrExpr = ptrExpr;
        this.ptrReg = ptrReg;
    }

    public IASTExpression getExpression() {
        return ptrExpr;
    }

    public int getRegister() {
        return ptrReg;
    }

    @Override
    public IType getType() {
        return ((ITypeContainer) ptrExpr.getExpressionType()).getType();
    }

    @Override
    public boolean isStatic() {
        return false;
    }

    @Override
    public String toDebugString() {
        return "*" + ptrExpr.getClass().getSimpleName() + "[" + ptrExpr.getRawSignature() + "]@" + ptrReg;
    }
}
