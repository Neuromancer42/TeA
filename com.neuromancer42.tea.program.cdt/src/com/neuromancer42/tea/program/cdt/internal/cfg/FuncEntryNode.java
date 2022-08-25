package com.neuromancer42.tea.program.cdt.internal.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.StartNode;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;

public class FuncEntryNode extends StartNode {
    private final IASTFunctionDefinition curFunc;

    public FuncEntryNode(IASTFunctionDefinition fDef) {
        curFunc = fDef;
    }
}
