package com.neuromancer42.tea.program.cdt.parser.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.ConnectorNode;

public class PhiNode extends ConnectorNode implements ICFGNode {
    private final String info;
    public PhiNode() {
        info = "";
    }

    public PhiNode(String info) {
        this.info = info;
    }
    @Override
    public String toDebugString() {
        return "phi:" + info;
    }
}
