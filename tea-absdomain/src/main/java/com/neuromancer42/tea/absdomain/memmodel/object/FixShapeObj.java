package com.neuromancer42.tea.absdomain.memmodel.object;

public class FixShapeObj extends StackObj {
    final String numElems;
    public FixShapeObj(String access, String type) {
        this(access, type, 1);
    }

    public FixShapeObj(String access, String type, int num) {
        this(access, type, Integer.toString(num));
    }

    public FixShapeObj(String access, String type, String num) {
        super(access, type);
        numElems = num;
    }

    public String getNumElems() {
        return numElems;
    }

    @Override
    public String toString() {
        return numElems + "*" + super.toString();
    }
}
