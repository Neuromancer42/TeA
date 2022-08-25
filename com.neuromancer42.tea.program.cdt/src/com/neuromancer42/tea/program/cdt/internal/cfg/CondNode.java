package com.neuromancer42.tea.program.cdt.internal.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.DecisionNode;
import org.eclipse.cdt.core.dom.ast.IASTExpression;

// Note: transfer to next node per the condition value; the expression only for debug, actually it depends on the value of register
public class CondNode extends DecisionNode {
    private final IASTExpression condExpr;
    private final int registerId;

    public CondNode(IASTExpression expr, int reg) {
        this.condExpr = expr;
        this.registerId = reg;
    }

    public IASTExpression getExpression() {
        return condExpr;
    }

    public int getRegister() {
        return registerId;
    }
}
