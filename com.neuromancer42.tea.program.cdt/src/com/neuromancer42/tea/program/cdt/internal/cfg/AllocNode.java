package com.neuromancer42.tea.program.cdt.internal.cfg;

import com.neuromancer42.tea.program.cdt.internal.memory.IMemObj;
import org.eclipse.cdt.codan.internal.core.cfg.PlainNode;

public class AllocNode extends PlainNode implements ICFGNode {
    private final int ptr;
    private final IMemObj obj;

    public AllocNode(int ptr, IMemObj obj) {
        this.ptr = ptr;
        this.obj = obj;
    }

    @Override
    public String toDebugString() {
        return "alloc:*@" + ptr + "=={" + obj.toDebugString() + "}";
    }

    public int getRegister() {
        return ptr;
    }

    public IMemObj getMemObj() {
        return obj;
    }
}
