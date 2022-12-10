package com.neuromancer42.tea.commons.analyses;

import com.neuromancer42.tea.commons.analyses.annotations.*;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Constants;

import java.util.Map;

@TeAAnalysis(name = "phony")
public class PhonyAnalysis extends AbstractAnalysis {
    private final String location;

    public PhonyAnalysis(String workdir) {
        location = workdir;
    }

    @ConsumeDom(name = "I")
    private ProgramDom domI;

    @ConsumeRel(name = "P", doms = {"I"})
    private ProgramRel relP;

    @ProduceDom
    private ProgramDom domO;

    @ProduceRel(doms = {"I", "O"})
    private ProgramRel relPP;

    @Override
    protected void domPhase() {
        for (String i : domI) {
            if (relP.contains(i)) {
                domO.add(i+"*");
            }
        }
    }

    @Override
    protected void relPhase() {
        for (String i : domI) {
            if (relP.contains(i)) {
                relPP.add(i, i+"*");
            }
        }
    }

    @Override
    protected String getOutDir() {
        return location;
    }
}
