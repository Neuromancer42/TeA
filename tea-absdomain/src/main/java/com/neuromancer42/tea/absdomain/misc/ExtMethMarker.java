package com.neuromancer42.tea.absdomain.misc;

import com.neuromancer42.tea.commons.analyses.AbstractAnalysis;
import com.neuromancer42.tea.commons.analyses.annotations.ConsumeDom;
import com.neuromancer42.tea.commons.analyses.annotations.ConsumeRel;
import com.neuromancer42.tea.commons.analyses.annotations.ProduceRel;
import com.neuromancer42.tea.commons.analyses.annotations.TeAAnalysis;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;

import java.nio.file.Path;
import java.util.Map;

@TeAAnalysis(name = "ExtMethMarker")
public class ExtMethMarker extends AbstractAnalysis {

    public static final String name = "ExtMethMarker";
    public final Path workPath;

    public ExtMethMarker(Path path) {
        workPath = path;
    }

    @ConsumeDom(description = "all functions")
    public ProgramDom domM;

    @ConsumeDom(description = "function arity")
    public ProgramDom domZ;

    @ConsumeRel(doms = {"M"}, description = "marked external functions")
    public ProgramRel relExtMeth;

    @ProduceRel(name = "retInput", doms = {"M"}, description = "input by function retval")
    public ProgramRel relRetInput;

    @ProduceRel(name = "argInput", doms = {"M", "Z"}, description = "input by function argument")
    public ProgramRel relArgInput;

    @ProduceRel(name = "mallocFunc", doms = {"M"})
    public ProgramRel relMallocFunc;
    @ProduceRel(name = "callocFunc", doms = {"M"})
    public ProgramRel relCallocFunc;
    @ProduceRel(name = "reallocFunc", doms = {"M"})
    public ProgramRel relReallocFunc;
    @ProduceRel(name = "freeFunc", doms = {"M"})
    public ProgramRel relFreeFunc;
    @ProduceRel(name = "memcpyFunc", doms = {"M"})
    public ProgramRel relMemcpyFunc;
    @ProduceRel(name = "memmoveFunc", doms = {"M"})
    public ProgramRel relMemmoveFunc;
    @ProduceRel(name = "memsetFunc", doms = {"M"})
    public ProgramRel relMemsetFunc;
    @ProduceRel(name = "memchrFunc", doms = {"M"})
    public ProgramRel relMemchrFunc;

    public void run(Map<String, ProgramDom> inputDoms, Map<String, ProgramRel> inputRels) {

        ProgramDom domM = inputDoms.get("M");
        ProgramDom domZ = inputDoms.get("Z");
        ProgramRel relRetInput = new ProgramRel("retInput", domM);
        ProgramRel relArgInput = new ProgramRel("argInput", domM, domZ);
        ProgramRel[] genRels = new ProgramRel[]{relRetInput, relArgInput};
        for (var rel : genRels) {
            rel.init();
        }

        ProgramRel relExtMeth = inputRels.get("ExtMeth");
        relExtMeth.load();
        relPhase();
        relExtMeth.close();
        for (var rel: genRels) {
            rel.save(getOutDir());
            rel.close();
        }
    }

    @Override
    protected void domPhase() {
        // no new domain generated
    }

    @Override
    protected void relPhase() {
        for (Object[] tuple : relExtMeth.getValTuples()) {
            String name = (String) tuple[0];
            switch (name) {
                case "rand" -> relRetInput.add(name);
                case "scanf" -> {
                    for (String z : domZ) {
                        if (!z.equals("0")) {
                            relArgInput.add(name, z);
                        }
                    }
                }
                case "strlen" -> relRetInput.add(name);
                case "malloc" -> relMallocFunc.add(name);
                case "calloc" -> relCallocFunc.add(name);
                case "realloc" -> relReallocFunc.add(name);
                case "free" -> relFreeFunc.add(name);
                case "memcpy" -> relMemcpyFunc.add(name);
                case "memmove" -> relMemmoveFunc.add(name);
                case "memset" -> relMemsetFunc.add(name);
                case "memchr" -> relMemchrFunc.add(name);
            }
        }
    }

    @Override
    protected String getOutDir() {
        return workPath.toAbsolutePath().toString();
    }
}
