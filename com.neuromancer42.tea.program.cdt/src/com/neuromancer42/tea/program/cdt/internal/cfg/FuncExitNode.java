package com.neuromancer42.tea.program.cdt.internal.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.ExitNode;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;

public class FuncExitNode extends ExitNode {
    private final IASTFunctionDefinition curFunc;

    public FuncExitNode(IASTFunctionDefinition fDef) {
        curFunc = fDef;
    }
}
