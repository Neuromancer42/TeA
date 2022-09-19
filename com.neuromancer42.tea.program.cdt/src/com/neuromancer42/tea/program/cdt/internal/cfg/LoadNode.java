package com.neuromancer42.tea.program.cdt.internal.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.PlainNode;
import org.eclipse.cdt.core.dom.ast.IType;

public class LoadNode extends PlainNode implements ICFGNode {
    private final int value;
    private final int pointer;
    public LoadNode(int valReg, int ptrReg) {
        this.value = valReg;
        this.pointer = ptrReg;
    }

    public int getValue() {
        return value;
    }

    public int getPointer() {
        return pointer;
    }

    public IType getType() {
        return null;
    }

    @Override
    public String toDebugString() {
        return "load:" + getType() + "@" + value + "<-*@" + pointer;
    }
}
