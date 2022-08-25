package com.neuromancer42.tea.program.cdt.internal.cfg;


import org.eclipse.cdt.codan.internal.core.cfg.PlainNode;
import org.eclipse.cdt.core.dom.ast.IASTExpression;

// Note: evaluate the expression over previous state, and store the result in the specified register
public class EvalNode extends PlainNode {
    private final IASTExpression expression;
    private final int registerId;

    public EvalNode(IASTExpression expr, int i) {
        this.expression = expr;
        this.registerId = i;
        super.setData(expr);
    }

    public IASTExpression getExpression() {
        return expression;
    }

    public int getRegister() {
        return registerId;
    }
}
