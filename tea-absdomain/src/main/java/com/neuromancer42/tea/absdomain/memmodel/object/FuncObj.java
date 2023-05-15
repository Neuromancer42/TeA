package com.neuromancer42.tea.absdomain.memmodel.object;

public class FuncObj implements IMemObj {
    private final String func;
    private static final String TYPE_FPTR = "funcptr";

    public FuncObj(String func) {
        this.func = func;
    }

    public String getFunc() {
        return func;
    }

    @Override
    public String getObjectType() {
        return TYPE_FPTR;
    }

    @Override
    public String toString() {
        return TYPE_FPTR + ":" + func;
    }
}
