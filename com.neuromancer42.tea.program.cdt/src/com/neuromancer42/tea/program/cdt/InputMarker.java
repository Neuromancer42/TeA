package com.neuromancer42.tea.program.cdt;

import com.neuromancer42.tea.core.analyses.AnalysesUtil;
import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.project.Trgt;
import org.eclipse.cdt.core.dom.ast.IFunction;

import java.util.*;

public class InputMarker extends JavaAnalysis {
    private final Trgt<ProgramDom<IFunction>> tDomM;
    private final Trgt<ProgramDom<Integer>> tDomZ;

    private final Trgt<ProgramRel> tRelExtMeth;

    private final Trgt<ProgramRel> tRelRetInput;
    private final Trgt<ProgramRel> tRelArgInput;
//    private final Trgt<ProgramRel> tRelTopV;
//    private final Trgt<ProgramRel> tRelTopH;

    public InputMarker() {
        this.name = "InputMarker";

        tDomM = AnalysesUtil.createDomTrgt(name, "M", IFunction.class);
        tDomZ = AnalysesUtil.createDomTrgt(name, "Z", Integer.class);

        tRelExtMeth = AnalysesUtil.createRelTrgt(name, "ExtMeth", "M");

        tRelRetInput = AnalysesUtil.createRelTrgt(name, "retInput", "M");
        tRelArgInput = AnalysesUtil.createRelTrgt(name, "argInput", "M", "Z");

        registerConsumers(tDomM, tDomZ, tRelExtMeth);

        registerProducers(tRelRetInput, tRelArgInput);
    }

    @Override
    public void run() {

        ProgramDom<IFunction> domM = tDomM.get();
        ProgramDom<Integer> domZ = tDomZ.get();
        ProgramRel relRetInput = new ProgramRel("retInput", domM);
        ProgramRel relArgInput = new ProgramRel("argInput", domM, domZ);
        ProgramRel[] genRels = new ProgramRel[]{relRetInput, relArgInput};
        for (var rel : genRels) {
            rel.init();
        }

        ProgramRel relExtMeth = tRelExtMeth.get();
        relExtMeth.load();
        for (Object[] tuple : relExtMeth.getValTuples()) {
            IFunction meth = (IFunction) tuple[0];
            String name = meth.getName();
            if (name.equals("scanf")) {
                for (Integer z : domZ) {
                    if(z > 0) {
                        relArgInput.add(meth, z);
                    }
                }
            }
        }
        relExtMeth.close();
        for (var rel: genRels) {
            rel.save();
            rel.close();
        }

        tRelRetInput.accept(relRetInput);
        tRelArgInput.accept(relArgInput);
    }
}
