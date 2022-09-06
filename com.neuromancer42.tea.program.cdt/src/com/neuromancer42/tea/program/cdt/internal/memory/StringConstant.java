package com.neuromancer42.tea.program.cdt.internal.memory;

import org.eclipse.cdt.core.dom.ast.IType;

public class StringConstant implements ILocation {
    private final String constant;
    private final IType type;

    public StringConstant(String str, IType type) {
        this.constant = str;
        this.type = type;
    }

    public String getConstant() {
        return constant;
    }

    @Override
    public boolean isStatic() {
        return true;
    }

    @Override
    public String toDebugString() {
        return "Constant:\"" + constant + "\"";
    }

    @Override
    public IType getType() {
        return type;
    }

    @Override
    public int[] getParameters() {
        return new int[0];
    }
}
