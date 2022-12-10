package com.neuromancer42.tea.memmodel.object;

import com.neuromancer42.tea.commons.configs.Constants;

public class DummyPtrObj implements IMemObj{
    private final String pointedPath;
    private final String pointedType;

    public DummyPtrObj(String access, String type) {
        this.pointedPath = access;
        this.pointedType = type;
    }


    @Override
    public String toString() {
        return Constants.PREFIX_DUMMY + getObjectType() + ":" + "&(" + pointedPath + ")";
    }

    @Override
    public String getObjectType() {
        return pointedType + "*";
    }
}
