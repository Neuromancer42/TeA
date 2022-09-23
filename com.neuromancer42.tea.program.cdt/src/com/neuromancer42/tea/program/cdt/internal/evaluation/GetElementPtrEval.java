package com.neuromancer42.tea.program.cdt.internal.evaluation;

import org.eclipse.cdt.core.dom.ast.IType;

public abstract class GetElementPtrEval implements IEval {
    protected final int basePtrReg;
    private final IType baseType;

    public GetElementPtrEval(IType type, int reg) {
        this.baseType = type;
        this.basePtrReg = reg;
    }

    public int getBasePtr() {
        return basePtrReg;
    }

    protected String toBaseDebugString() {
        return "base:" + baseType + "@" + basePtrReg;
    }

    protected IType getBaseType() {
        return baseType;
    }
}
