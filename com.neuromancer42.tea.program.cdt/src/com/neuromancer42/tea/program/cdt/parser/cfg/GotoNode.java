package com.neuromancer42.tea.program.cdt.parser.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.JumpNode;

public class GotoNode extends JumpNode implements ICFGNode {
    @Override
    public String toDebugString() {
        return "goto";
    }
}
