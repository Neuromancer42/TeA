package com.neuromancer42.tea.program.cdt.internal.evaluation;

import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IType;

public interface IEval {
    String toDebugString();
    int[] getOperands();
    IASTExpression getExpression();
    IType getType();
}
