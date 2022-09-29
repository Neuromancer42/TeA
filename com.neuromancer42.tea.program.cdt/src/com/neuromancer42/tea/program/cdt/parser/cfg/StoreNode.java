package com.neuromancer42.tea.program.cdt.parser.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.PlainNode;

// Store the value in register to target (variable/pointer/array/field)
public class StoreNode extends PlainNode implements ICFGNode {
    private final int value;
    private final int pointer;
    public StoreNode(int ptrReg, int valReg) {
        this.value = valReg;
        this.pointer = ptrReg;
    }

    public int getValue() {
        return value;
    }

    public int getPointer() {
        return pointer;
    }

    @Override
    public String toDebugString() {
        return "store:" + "*@" + pointer + "<-" + "@" + value;
    }
}
