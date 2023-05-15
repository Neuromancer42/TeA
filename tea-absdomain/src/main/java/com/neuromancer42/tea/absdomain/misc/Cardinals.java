package com.neuromancer42.tea.absdomain.misc;

import com.neuromancer42.tea.commons.analyses.AbstractAnalysis;
import com.neuromancer42.tea.commons.analyses.annotations.ConsumeDom;
import com.neuromancer42.tea.commons.analyses.annotations.ProduceRel;
import com.neuromancer42.tea.commons.analyses.annotations.TeAAnalysis;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;

import java.nio.file.Path;

@TeAAnalysis(name = "cardinals")
public class Cardinals extends AbstractAnalysis {
    public static final String name = "cardinals";
    private final Path workPath;

    @ConsumeDom
    ProgramDom domZ;

    @ProduceRel(doms = {"Z", "Z"})
    ProgramRel relZnext;

    @ProduceRel(doms = {"Z"})
    ProgramRel relZ0;
    @ProduceRel(doms = {"Z"})
    ProgramRel relZ1;
    @ProduceRel(doms = {"Z"})
    ProgramRel relZ2;
    @ProduceRel(doms = {"Z"})
    ProgramRel relZ3;
    @ProduceRel(doms = {"Z"})
    ProgramRel relZ4;
    @ProduceRel(doms = {"Z"})
    ProgramRel relZ5;
    @ProduceRel(doms = {"Z"})
    ProgramRel relZ6;

    @ProduceRel(doms = {"Z", "Z", "Z"})
    ProgramRel relZadd;

    private int max_z = 0;

    public Cardinals(Path workPath) {
        this.workPath = workPath;
    }

    @Override
    protected String getOutDir() {
        return workPath.toAbsolutePath().toString();
    }

    @Override
    protected void domPhase() {
        for (String z : domZ) {
            int zval = Integer.parseInt(z);
            if (zval > max_z) {
                max_z = zval;
            }
        }
    }

    @Override
    protected void relPhase() {
        relZ0.add(Integer.toString(0));
        if (max_z >= 1) relZ1.add(Integer.toString(1));
        if (max_z >= 2) relZ2.add(Integer.toString(2));
        if (max_z >= 3) relZ3.add(Integer.toString(3));
        if (max_z >= 4) relZ4.add(Integer.toString(4));
        if (max_z >= 5) relZ5.add(Integer.toString(5));
        if (max_z >= 6) relZ6.add(Integer.toString(6));
        for (int i = 1; i <= max_z; ++i) {
            relZnext.add(Integer.toString(i - 1), Integer.toString(i));
            for (int j = 0; j <= i; ++j) {
                relZadd.add(Integer.toString(j), Integer.toString(i - j), Integer.toString(i));
            }
        }
    }

}
