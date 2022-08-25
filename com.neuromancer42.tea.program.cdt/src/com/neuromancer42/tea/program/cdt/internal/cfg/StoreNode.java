package com.neuromancer42.tea.program.cdt.internal.cfg;

import com.neuromancer42.tea.program.cdt.internal.memory.IStorage;
import org.eclipse.cdt.codan.internal.core.cfg.PlainNode;

// Store the value in register to target (variable/pointer/array/field)
public class StoreNode extends PlainNode {
    private final int registerId;
    private final IStorage location;
    public StoreNode(IStorage location, int register) {
        this.registerId = register;
        this.location = location;
    }

    public IStorage getStorage() {
        return location;
    }

    public int getRegister() {
        return registerId;
    }
}
