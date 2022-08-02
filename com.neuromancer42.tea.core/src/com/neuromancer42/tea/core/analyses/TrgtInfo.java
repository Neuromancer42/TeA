package com.neuromancer42.tea.core.analyses;

import com.neuromancer42.tea.core.bddbddb.RelSign;

public class TrgtInfo {
    public Class<?> type;
    public final String location;
    public RelSign sign;
    public TrgtInfo(Class<?> type, String location, RelSign sign) {
        this.type = type;
        this.location = location;
        this.sign = sign;
    }
}
