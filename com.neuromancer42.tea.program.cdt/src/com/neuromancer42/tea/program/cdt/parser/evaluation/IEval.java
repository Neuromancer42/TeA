package com.neuromancer42.tea.program.cdt.parser.evaluation;

import com.neuromancer42.tea.core.analyses.IDebuggable;
import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IType;

public interface IEval extends IDebuggable {
    int[] getOperands();
    IASTExpression getExpression();
    IType getType();
}
