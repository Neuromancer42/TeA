package com.neuromancer42.tea.absdomain.memmodel.object;

import com.neuromancer42.tea.commons.configs.Constants;

public class SpecialObj implements IMemObj {

    public static SpecialObj nullObj = new SpecialObj(Constants.NULL);
    public static SpecialObj unknownObj = new SpecialObj(Constants.UNKNOWN);

    private final String str;

    private SpecialObj(String str) {
        this.str = str;
    }

    @Override
    public String getObjectType() {
        return "void";
    }

    @Override
    public String toString() {
        return str;
    }
}
