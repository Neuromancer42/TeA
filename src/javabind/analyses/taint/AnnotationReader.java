package javabind.analyses.taint;

import chord.project.Chord;
import javabind.analyses.base.DomM;
import javabind.program.Program;
import javabind.program.binddefs.BindUtils;
import chord.project.ClassicProject;
import chord.analyses.JavaAnalysis;
import chord.analyses.ProgramRel;
import soot.Scene;
import soot.SootMethod;

import java.util.*;

/*
 * read source/sink labels from {chord.work.dir}/annotations.txt
 * @author Saswat Anand
 * @author Yifan Chen
 */
@Chord(name = "annot-java",
    consumes = { "M", "Z" },
    produces = { "L",
            "SrcLabel", "SnkLabel",
            "SrcMeth", "SnkMeth" },
    namesOfTypes = { "L" },
    types = { DomL.class },
    namesOfSigns = { "SrcLabel", "SnkLabel",
            "SrcMeth", "SnkMeth" },
    signs = { "L0:L0", "L0:L0",
            "L0,M0:M0_L0", "L0,M0:M0_L0" }
)
public class AnnotationReader extends JavaAnalysis {
    private ProgramRel rel;

    private Map<SootMethod,String> srcLabels = new HashMap<>();
    private Map<SootMethod,String> snkLabels = new HashMap<>();

    public void run() {
        DomM domM = (DomM) ClassicProject.g().getTrgt("M");

        Scene scene = Program.g().scene();

        DomL domL = (DomL) ClassicProject.g().getTrgt("L");

        for (String srcMethName : BindUtils.getSrcMethodNames()) {
            SootMethod srcMeth = BindUtils.sig2Meth(scene, srcMethName);
            if (srcMeth != null && domM.contains(srcMeth)) {
                String src = "$" + srcMethName;
                domL.add(src);
                srcLabels.put(srcMeth, src);
            }
        }
        for (String snkMethName : BindUtils.getSnkMethodNames()) {
            SootMethod snkMeth = BindUtils.sig2Meth(scene, snkMethName);
            if (snkMeth != null && domM.contains(snkMeth)) {
                String snk = "!" + snkMethName;
                domL.add(snk);
                snkLabels.put(snkMeth, snk);
            }
        }

        domL.save();


        ProgramRel relSrcLabel = (ProgramRel) ClassicProject.g().getTrgt("SrcLabel");
        relSrcLabel.zero();
        ProgramRel relSrcMeth = (ProgramRel) ClassicProject.g().getTrgt("SrcMeth");
        relSrcMeth.zero();
        for (SootMethod srcMeth : srcLabels.keySet()) {
            String srcLabel = srcLabels.get(srcMeth);
            relSrcLabel.add(srcLabel);
            relSrcMeth.add(srcLabel,srcMeth);
        }
        relSrcLabel.save();
        relSrcMeth.save();

        ProgramRel relSnkLabel = (ProgramRel) ClassicProject.g().getTrgt("SnkLabel");
        relSnkLabel.zero();
        ProgramRel relSnkMeth = (ProgramRel) ClassicProject.g().getTrgt("SnkMeth");
        relSnkMeth.zero();
        for (SootMethod snkMeth : snkLabels.keySet()) {
            String snkLabel = snkLabels.get(snkMeth);
            relSnkLabel.add(snkLabel);
            relSnkMeth.add(snkLabel,snkMeth);
        }
        relSnkLabel.save();
        relSnkMeth.save();
    }
}
