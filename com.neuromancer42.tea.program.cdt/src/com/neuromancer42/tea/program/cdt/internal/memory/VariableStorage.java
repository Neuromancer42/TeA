package com.neuromancer42.tea.program.cdt.internal.memory;

import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.IVariable;

// A memory storage location referenced by a variable (i.e. its target is known)
public class VariableStorage implements IStorage {
    private final IVariable variable;

    public VariableStorage(IVariable variable) {
        this.variable = variable;
    }

    public IType getType() {
        return variable.getType();
    }

    @Override
    public boolean isStatic() {
        return true;
    }

    @Override
    public String toDebugString() {
        if (variable != null) {
            return variable.getClass().getSimpleName() + "[" + variable.getName() + "]";
        }else {
            return "Unknown@" + this.hashCode();
        }
    }
}
