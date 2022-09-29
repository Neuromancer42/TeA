package com.neuromancer42.tea.program.cdt.parser.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.BranchNode;

public class LabelNode extends BranchNode implements ICFGNode {
    protected LabelNode(String label) {
        super(label);
    }

    @Override
    public String toDebugString() {
        return "label:"+label;
    }
}
