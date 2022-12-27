package com.neuromancer42.tea.absdomain.dataflow;

import com.neuromancer42.tea.commons.analyses.AbstractAnalysis;
import com.neuromancer42.tea.commons.analyses.annotations.ConsumeDom;
import com.neuromancer42.tea.commons.analyses.annotations.ConsumeRel;
import com.neuromancer42.tea.commons.analyses.annotations.ProduceRel;
import com.neuromancer42.tea.commons.analyses.annotations.TeAAnalysis;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;

import java.nio.file.Path;
import java.util.Map;

@TeAAnalysis(name = "InputMarker")
public class InputMarker extends AbstractAnalysis {

    public static final String name = "InputMarker";
    public final Path workPath;

    public InputMarker(Path path) {
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
            if (name.equals("scanf")) {
                for (String z : domZ) {
                    if(!z.equals("0")) {
                        relArgInput.add(name, z);
                    }
                }
            }
        }
    }

    @Override
    protected String getOutDir() {
        return workPath.toAbsolutePath().toString();
    }
}
