package com.neuromancer42.tea.program.cdt.internal.cfg;

import com.neuromancer42.tea.program.cdt.internal.memory.ILocation;
import org.eclipse.cdt.codan.internal.core.cfg.PlainNode;
import org.eclipse.cdt.core.dom.ast.IType;

// Store the value in register to target (variable/pointer/array/field)
public class StoreNode extends PlainNode implements ICFGNode {
    private final int registerId;
    private final ILocation location;
    public StoreNode(ILocation location, int register) {
        this.registerId = register;
        this.location = location;
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
        return "store:" + location.toDebugString() + "<-" + location.getType() + "@" + registerId;
    }
}
