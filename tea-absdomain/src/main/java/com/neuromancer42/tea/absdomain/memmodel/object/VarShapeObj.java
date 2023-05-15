package com.neuromancer42.tea.absdomain.memmodel.object;

public class VarShapeObj extends StackObj {
    final String varNumElems;

    public VarShapeObj(String access, String type, String var) {
        super(access, type);
        varNumElems = var;
    }

    public String getVarNumElems() {
        return varNumElems;
    }

    @Override
    public String toString() {
        return varNumElems + "*" + super.toString();
    }
}
