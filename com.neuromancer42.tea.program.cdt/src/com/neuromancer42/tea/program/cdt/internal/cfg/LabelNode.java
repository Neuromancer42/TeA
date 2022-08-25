package com.neuromancer42.tea.program.cdt.internal.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.BranchNode;

public class LabelNode extends BranchNode {
    protected LabelNode(String label) {
        super(label);
    }
}
