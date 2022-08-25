package com.neuromancer42.tea.program.cdt.internal.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.PlainNode;
import org.eclipse.cdt.core.dom.ast.IASTExpression;

// Store the value in register to target (lvalue)
public class StoreNode extends PlainNode {
    private final IASTExpression object;
    private final int registerId;
    public StoreNode(IASTExpression lvalue, int register) {
        this.object = lvalue;
        this.registerId = register;
    }

    public IASTExpression getObject() {
        return object;
    }

    public int getRegister() {
        return registerId;
    }
}
