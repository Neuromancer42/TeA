package com.neuromancer42.tea.program.cdt.parser.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.DecisionNode;

// Note: transfer to next node per the condition value; the expression only for debug, actually it depends on the value of register
public class CondNode extends DecisionNode implements ICFGNode {
    private final int registerId;

    public CondNode(int reg) {
        this.registerId = reg;
    }

    public int getRegister() {
        return registerId;
    }

    @Override
    public String toDebugString() {
        return "cond:@" + registerId;
    }
}
