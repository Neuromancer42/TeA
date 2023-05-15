package com.neuromancer42.tea.absdomain.memmodel.object;

public class DynamicObj implements IMemObj {
    private final String allocLoc;

    public DynamicObj(String allocLoc) {
        this.allocLoc = allocLoc;
    }

    @Override
    public String getObjectType() {
        return "void";
    }

    public String toString() {
        return "void:dyn-" + allocLoc;
    }
}
