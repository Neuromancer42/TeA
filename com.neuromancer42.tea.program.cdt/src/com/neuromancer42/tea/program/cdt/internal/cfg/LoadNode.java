package com.neuromancer42.tea.program.cdt.internal.cfg;

import com.neuromancer42.tea.program.cdt.internal.memory.ILocation;
import org.eclipse.cdt.codan.internal.core.cfg.PlainNode;
import org.eclipse.cdt.core.dom.ast.IType;

// Load the stored value in storage and store it in register i
public class LoadNode extends PlainNode implements ICFGNode {
    private final ILocation location;
    private final int registerId;

    public LoadNode(int i, ILocation loc) {
        this.location = loc;
        this.registerId = i;
    }

    public ILocation getStorage() {
        return location;
    }

    public int getRegister() {
        return registerId;
    }

    public IType getType() {
        return location.getType();
    }

    @Override
    public String toDebugString() {
        return "load:" + location.toDebugString() + "->" + location.getType() + "@" + registerId;
    }
}
