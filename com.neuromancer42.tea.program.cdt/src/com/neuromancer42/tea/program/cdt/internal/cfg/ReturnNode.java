package com.neuromancer42.tea.program.cdt.internal.cfg;

import org.eclipse.cdt.codan.internal.core.cfg.ExitNode;
import org.eclipse.cdt.core.dom.ast.IASTExpression;

public class ReturnNode extends ExitNode implements ICFGNode {
    private final IASTExpression retExpr;
    private final int retReg;

    public ReturnNode(IASTExpression expr, int reg) {
        this.retExpr = expr;
        this.retReg = reg;
    }

    public ReturnNode() {
        this.retExpr = null;
        this.retReg = -1;
    }

    public IASTExpression getExpression() {
        return retExpr;
    }

    public int getRegister() {
        return retReg;
    }

    @Override
    public String toDebugString() {
        if (retExpr == null) {
            return "return:void";
        } else {
            return "return:" + retExpr.getExpressionType() + "@" + retReg + ":" + retExpr.getClass().getSimpleName() + "[" + retExpr.getRawSignature() + "]";
        }
    }
}
