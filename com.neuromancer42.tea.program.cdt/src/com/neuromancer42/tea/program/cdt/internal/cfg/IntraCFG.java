package com.neuromancer42.tea.program.cdt.internal.cfg;


import org.eclipse.cdt.codan.core.model.cfg.IExitNode;
import org.eclipse.cdt.codan.core.model.cfg.IStartNode;
import org.eclipse.cdt.codan.internal.core.cfg.ControlFlowGraph;

import java.util.Collection;

public class IntraCFG extends ControlFlowGraph {
    public IntraCFG(IStartNode start, Collection<IExitNode> exitNodes) {
        super(start, exitNodes);
    }
}
