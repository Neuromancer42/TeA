package com.neuromancer42.tea.program.cdt.internal.memory;

import org.eclipse.cdt.core.dom.ast.IFunction;
import org.eclipse.cdt.core.dom.ast.IType;

public class FunctionLocation implements ILocation {
    private final IFunction func;

    public FunctionLocation(IFunction func) {
        this.func = func;
    }

    public IFunction getFunc() {
        return func;
    }

    @Override
    public boolean isStatic() {
        return true;
    }

    @Override
    public String toDebugString() {
        return func.toString();
    }

    @Override
    public IType getType() {
        return func.getType();
    }
}
