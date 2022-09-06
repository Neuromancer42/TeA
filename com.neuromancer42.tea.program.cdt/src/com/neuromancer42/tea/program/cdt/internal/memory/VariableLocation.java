package com.neuromancer42.tea.program.cdt.internal.memory;

import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.IVariable;

// A memory storage location referenced by a variable (i.e. its target is known)
public class VariableLocation implements ILocation {
    private final IVariable variable;

    public VariableLocation(IVariable variable) {
        this.variable = variable;
    }

    public IType getType() {
        return variable.getType();
    }

    public IVariable getVariable() {
        return variable;
    }

    @Override
    public boolean isStatic() {
        return true;
    }

    @Override
    public String toDebugString() {
        if (variable != null) {
            return variable.getClass().getSimpleName() + "[" + variable.getName() + "]";
        } else {
            return "Unknown@" + this.hashCode();
        }
    }

    @Override
    public int[] getParameters() {
        return new int[0];
    }

}
