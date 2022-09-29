package com.neuromancer42.tea.program.cdt.dataflow;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.analyses.ProgramRel;
import org.eclipse.cdt.core.dom.ast.IFunction;

public class InputMarker extends JavaAnalysis {
    public InputMarker() {
        this.name = "InputMarker";

        createDomConsumer("M", IFunction.class);
        createDomConsumer("Z", Integer.class);

        createRelConsumer("ExtMeth", "M");

        createRelProducer("retInput", "M");
        createRelProducer("argInput", "M", "Z");
    }

    @Override
    public void run() {

        ProgramDom<IFunction> domM = consume("M");
        ProgramDom<Integer> domZ = consume("Z");
        ProgramRel relRetInput = new ProgramRel("retInput", domM);
        ProgramRel relArgInput = new ProgramRel("argInput", domM, domZ);
        ProgramRel[] genRels = new ProgramRel[]{relRetInput, relArgInput};
        for (var rel : genRels) {
            rel.init();
        }

        ProgramRel relExtMeth = consume("ExtMeth");
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
            produceRel(rel);
        }
    }
}
