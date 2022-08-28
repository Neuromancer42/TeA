package com.neuromancer42.tea.program.cdt.internal.cfg;


import com.neuromancer42.tea.program.cdt.internal.evaluation.IEval;
import org.eclipse.cdt.codan.internal.core.cfg.PlainNode;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IType;

// Note: evaluate the expression over previous state, and store the result in the specified register
public class EvalNode extends PlainNode implements ICFGNode {
    private final int registerId;
    private final IEval evaluation;

    public EvalNode(IEval eval, int target) {
        this.evaluation = eval;
        this.registerId = target;
    }

    public IEval getEvaluation() {
        return evaluation;
    }

    public int getRegister() {
        return registerId;
    }

    public IType getType() {
        return evaluation.getType();
    }

    public String toDebugString() {
        return "eval:" + getType().toString() + "@" + registerId + ":=" + evaluation.toDebugString();
    }
}
