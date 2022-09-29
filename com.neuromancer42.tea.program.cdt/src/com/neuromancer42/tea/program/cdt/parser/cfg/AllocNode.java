package com.neuromancer42.tea.program.cdt.parser.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.PlainNode;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.IVariable;

public class AllocNode extends PlainNode implements ICFGNode {
    private final int ptr;
    private final IVariable variable;

    public AllocNode(int ptr, IVariable var) {
        this.ptr = ptr;
        this.variable = var;
    }

    @Override
    public String toDebugString() {
        return "alloc:*@" + ptr + "=={" + variable + "}";
    }

    public int getRegister() {
        return ptr;
    }

    public IVariable getVariable() {
        return variable;
    }

    public IType getAllocType() {
        return variable.getType();
    }
}
