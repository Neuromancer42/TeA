package com.neuromancer42.tea.program.cdt.internal.cfg;

import com.neuromancer42.tea.program.cdt.internal.memory.IStorage;
import org.eclipse.cdt.codan.internal.core.cfg.PlainNode;
import org.eclipse.cdt.core.dom.ast.IASTExpression;

// Load the stored value in storage and store it in register i
public class LoadNode extends PlainNode {
    private final IStorage location;
    private final int registerId;

    public LoadNode(int i, IStorage loc) {
        this.location = loc;
        this.registerId = i;
    }

    public IStorage getStorage() {
        return location;
    }

    public int getRegister() {
        return registerId;
    }
}
