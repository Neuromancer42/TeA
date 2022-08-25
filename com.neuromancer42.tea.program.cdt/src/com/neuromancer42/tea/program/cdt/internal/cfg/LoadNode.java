package com.neuromancer42.tea.program.cdt.internal.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.PlainNode;
import org.eclipse.cdt.core.dom.ast.IASTExpression;

// Load the stored value referenced in object according to previous state, and store it in register i
public class LoadNode extends PlainNode {
    private final IASTExpression object;
    private final int registerId;

    public LoadNode(IASTExpression expr, int i) {
        this.object = expr;
        this.registerId = i;
        super.setData(expr);
    }

    public IASTExpression getObject() {
        return object;
    }

    public int getRegister() {
        return registerId;
    }
}
