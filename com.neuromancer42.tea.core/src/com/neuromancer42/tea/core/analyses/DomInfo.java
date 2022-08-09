package com.neuromancer42.tea.core.analyses;

import com.neuromancer42.tea.core.project.TrgtInfo;
import com.neuromancer42.tea.core.util.Utils;

public class DomInfo extends TrgtInfo {
    public final Class<?> domType;

    public DomInfo(String location, Class<?> domType) {
        super(ProgramDom.class, location);
        this.domType = domType;
    }

    @Override
    public boolean consumable(TrgtInfo providerInfo) {
        if (!(providerInfo instanceof DomInfo))
            return false;
        Class<?> providerDomType = ((DomInfo) providerInfo).domType;
        return Utils.isSubclass(providerDomType, domType);
    }

    @Override
    public String toString() {
        return "Dom<" + domType + ">@" + location;
    }
}
