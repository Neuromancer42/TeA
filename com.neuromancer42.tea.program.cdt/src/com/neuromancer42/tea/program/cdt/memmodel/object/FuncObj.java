package com.neuromancer42.tea.program.cdt.memmodel.object;

import org.eclipse.cdt.core.dom.ast.IFunction;
import org.eclipse.cdt.core.dom.ast.IType;

public class FuncObj implements IMemObj {
    private final IFunction func;

    public FuncObj(IFunction func) {
        this.func = func;
    }

    @Override
    public String toDebugString() {
        return "FuncObj:" + getObjectType() + "$[" + func + "]";
    }

    @Override
    public IType getObjectType() {
        return func.getType();
    }

}
