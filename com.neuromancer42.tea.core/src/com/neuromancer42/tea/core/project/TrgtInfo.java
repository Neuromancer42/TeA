package com.neuromancer42.tea.core.project;

import com.neuromancer42.tea.core.util.Utils;

public class TrgtInfo {
    private final Class<?> type;
    public final String location;

    public TrgtInfo(Class<?> type, String location) {
        this.type = type;
        this.location = location;
    }

    public boolean consumable(TrgtInfo providerInfo) {
        return Utils.isSubclass(providerInfo.type, this.type);
    }

    @Override
    public String toString() {
        return type + "@" + location;
    }
}
