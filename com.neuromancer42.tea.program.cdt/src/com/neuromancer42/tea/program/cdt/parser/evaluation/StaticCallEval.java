package com.neuromancer42.tea.program.cdt.parser.evaluation;

import org.eclipse.cdt.core.dom.ast.IASTExpression;
import org.eclipse.cdt.core.dom.ast.IFunction;
import org.eclipse.cdt.core.dom.ast.IType;

public class StaticCallEval implements IEval {
    private final IFunction function;
    private final int[] arguments;
    private final IASTExpression debugExpr;

    public StaticCallEval(IASTExpression expr, IFunction func, int[] aRegs) {
        this.function = func;
        this.arguments = aRegs;
        this.debugExpr = expr;
    }

    public IFunction getFunction() {
        return function;
    }

    public int[] getArguments() {
        return arguments;
    }

    @Override
    public String toDebugString() {
        StringBuilder sb = new StringBuilder();
        sb.append("call(@").append(function.getName());
        for (int arg : arguments) {
            sb.append(",#").append(arg);
        }
        sb.append(")");
        sb.append(debugExpr.getClass().getSimpleName());
        sb.append("[");
        sb.append(debugExpr.getRawSignature());
        sb.append("]");
        return sb.toString();
    }

    @Override
    public int[] getOperands() {
        int[] operands = new int[arguments.length];
        System.arraycopy(arguments, 0, operands, 0, arguments.length);
        return operands;
    }

    @Override
    public IASTExpression getExpression() {
        return debugExpr;
    }

    @Override
    public IType getType() {
        return debugExpr.getExpressionType();
    }
}

