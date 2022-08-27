package com.neuromancer42.tea.program.cdt.internal.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.StartNode;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.IFunction;

public class FuncEntryNode extends StartNode implements ICFGNode {
    private final IASTFunctionDefinition curFunc;

    public FuncEntryNode(IASTFunctionDefinition fDef) {
        curFunc = fDef;
    }

    @Override
    public String toDebugString() {
        return "entry:" + curFunc.getDeclarator().getName();
    }
}
