package com.neuromancer42.tea.program.cdt.internal.memory;

import org.eclipse.cdt.core.dom.ast.IFunction;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.IVariable;

public class StackVarObj implements IMemObj {
    private final IVariable var;
    private final IFunction inFunc;

    public StackVarObj(IVariable var, IFunction inFunc) {
        this.var = var;
        this.inFunc = inFunc;
    }

    @Override
    public String toDebugString() {
        return "StackVarObj:" + getObjectType() + "$[" + (inFunc == null ? "" : inFunc.getName()) + "." + var + "]";
    }

    @Override
    public IType getObjectType() {
        return var.getType();
    }

    @Override
    public boolean isStatic() {
        return var.isStatic() || inFunc == null;
    }
}
