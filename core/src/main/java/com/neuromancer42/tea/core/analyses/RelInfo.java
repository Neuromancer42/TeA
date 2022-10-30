package com.neuromancer42.tea.core.analyses;

import com.neuromancer42.tea.core.bddbddb.RelSign;
import com.neuromancer42.tea.core.project.TrgtInfo;

import java.util.Arrays;

public class RelInfo extends TrgtInfo {
    private final RelSign relSign;

    public RelInfo(String location, RelSign relSign) {
        super(ProgramRel.class, location);
        assert relSign != null;
        this.relSign = relSign;
    }

    @Override
    public boolean consumable(TrgtInfo providerInfo) {
        if (!(providerInfo instanceof RelInfo))
            return false;
        RelSign providerSign = ((RelInfo) providerInfo).relSign;
        String[] domNames = relSign.val0;
        String[] providerDomNames = providerSign.val0;
        if (!Arrays.equals(domNames, providerDomNames)) {
            return false;
        }
        // TODO: domOrder?
        String domOrder = relSign.val1;;
        String providerDomOrder = providerSign.val1;
        return domOrder.equals(providerDomOrder);
    }

    @Override
    public String toString() {
        return "Rel" + relSign + "@" + location;
    }
}
