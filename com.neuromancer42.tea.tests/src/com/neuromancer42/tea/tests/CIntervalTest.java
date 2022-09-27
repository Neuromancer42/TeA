package com.neuromancer42.tea.tests;

import com.neuromancer42.tea.core.analyses.AnalysesUtil;
import com.neuromancer42.tea.core.inference.AbstractCausalDriver;
import com.neuromancer42.tea.core.inference.Categorical01;
import com.neuromancer42.tea.core.inference.CausalGraph;
import com.neuromancer42.tea.core.project.*;
import com.neuromancer42.tea.core.provenance.Provenance;
import com.neuromancer42.tea.core.provenance.Tuple;
import com.neuromancer42.tea.libdai.IteratingCausalDriver;
import com.neuromancer42.tea.libdai.OneShotCausalDriver;
import com.neuromancer42.tea.program.cdt.*;
import com.neuromancer42.tea.souffle.SouffleAnalysis;
import com.neuromancer42.tea.souffle.SouffleRuntime;
import org.junit.jupiter.api.*;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

public class CIntervalTest {
    static BundleContext context = FrameworkUtil.getBundle(CIntervalTest.class).getBundleContext();

    @BeforeAll
    public static void registerAnalyses() {
        Messages.log("Registering CParser");
        CParserAnalysis cparser = new CParserAnalysis();
        AnalysesUtil.registerAnalysis(context, cparser);

        Messages.log("Registering CMemModel");
        CMemoryModel cMemModel = new CMemoryModel();
        AnalysesUtil.registerAnalysis(context, cMemModel);

        Messages.log("Registering PreDataflow");
        PreDataflowAnalysis preDataflow = new PreDataflowAnalysis();
        AnalysesUtil.registerAnalysis(context, preDataflow);

        Messages.log("Registering PreInterval");
        PreIntervalAnalysis preInterval = new PreIntervalAnalysis();
        AnalysesUtil.registerAnalysis(context, preInterval);

        Messages.log("Registering pre_pt.dl");
        String dlogName0 = System.getProperty("dlog0");
        SouffleAnalysis prePT = SouffleRuntime.g().createSouffleAnalysisFromFile("PrePointer", "pre_pt", new File(dlogName0));
        AnalysesUtil.registerAnalysis(context, prePT);

        Messages.log("Registering cipa_cg.dl");
        String dlogName1 = System.getProperty("dlog1");
        SouffleAnalysis cipa = SouffleRuntime.g().createSouffleAnalysisFromFile("ciPointerAnalysis", "cipa_cg", new File(dlogName1));
        AnalysesUtil.registerAnalysis(context, cipa);

        Messages.log("Registering interval.dl");
        String dlogName2 = System.getProperty("dlog2");
        SouffleAnalysis interval = SouffleRuntime.g().createSouffleAnalysisFromFile("interval", "interval", new File(dlogName2));
        AnalysesUtil.registerAnalysis(context, interval);

        Messages.log("Registering InputMarker");
        InputMarker marker = new InputMarker();
        AnalysesUtil.registerAnalysis(context, marker);
        OsgiProject.init();
    }

    @Test
    @DisplayName("Run analysis")
    public void runAnalyses() {
        Set<String> tasks = Project.g().getTasks();
        Messages.log("Found %d tasks", tasks.size());
        Assertions.assertTrue(tasks.contains("CParser"));
        Assertions.assertTrue(tasks.contains("PrePointer"));
        Assertions.assertTrue(tasks.contains("ciPointerAnalysis"));
        Assertions.assertTrue(tasks.contains("interval"));
        String[] taskSet = new String[3];
        taskSet[0] = "ciPointerAnalysis";
        taskSet[1] = "interval";
        taskSet[2] = "InputMarker";
        Project.g().run(taskSet);
        Project.g().printRels(new String[]{"ExtMeth", "MP", "ci_IM", "ci_hpt", "ci_pt", "StorePtr", "MaySat", "MayUnsat", "evalBinopU", "evalUnaryU", "P_strong_update", "P_weak_update", "PredL", "PredR", "Pred2", "ci_PHval", "ci_Vval", "retInput", "argInput"});

        Assertions.assertEquals(8, OsgiProject.g().getDoneTasks().size());
        ITask task = OsgiProject.g().getTask("interval");
        Provenance provenance = ((SouffleAnalysis) task).getProvenance();
        CausalGraph<String> causalGraph = CausalGraph.buildCausalGraph(provenance,
                cons -> new Categorical01(new double[]{0.1,0.5,1.0}),
                input -> new Categorical01(new double[]{0.1,0.5,1.0})
        );
        causalGraph.dump(Path.of(Config.v().outDirName));
        AbstractCausalDriver causalDriver = new IteratingCausalDriver("test-iterating-interval", causalGraph);
        // TODO: load observations
        Tuple cretP = new Tuple("ci_PHval", 22, 4, 11);
        Tuple cretN = new Tuple("ci_PHval", 22, 4, 3);
        Tuple cret0 = new Tuple("ci_PHval", 22, 4, 7);
        Tuple bretP = new Tuple("ci_PHval", 22, 3, 10);
        Tuple bretN = new Tuple("ci_PHval", 22, 3, 4);
        List<String> queries = Arrays.asList(
                provenance.encodeTuple(bretP),
                provenance.encodeTuple(bretN),
                provenance.encodeTuple(cret0),
                provenance.encodeTuple(cretP),
                provenance.encodeTuple(cretN)
        );
        Map<String, Double> prior = causalDriver.queryPossibilities(queries);
        Messages.log("Prior:");
        Messages.log("P(c=0) = %f%%", 100.0 * prior.get(provenance.encodeTuple(cret0)));
        Messages.log("P(c>0) = %f%%", 100.0 * prior.get(provenance.encodeTuple(cretP)));
        Messages.log("P(c<0) = %f%%", 100.0 * prior.get(provenance.encodeTuple(cretN)));
        Messages.log("P(b>0) = %f%%", 100.0 * prior.get(provenance.encodeTuple(bretP)));
        Messages.log("P(b<0) = %f%%", 100.0 * prior.get(provenance.encodeTuple(bretN)));

        ArrayList<Map<String, Boolean>> trace = new ArrayList<>();
        for (int i = 0; i < 10; ++i) {
            Map<String, Boolean> obs = new HashMap<>();
            obs.put(provenance.encodeTuple(cret0), false);
            if (i % 2 == 0) {
                obs.put(provenance.encodeTuple(cretN), true);
                obs.put(provenance.encodeTuple(cretP), false);
            } else {
                obs.put(provenance.encodeTuple(cretN), false);
                obs.put(provenance.encodeTuple(cretP), true);
            }
            trace.add(obs);
        }
        causalDriver.run(trace);
        Map<String, Double> post = causalDriver.queryPossibilities(queries);
        Messages.log("Posterior:");
        Messages.log("P(c=0) = %f%%", 100.0 * post.get(provenance.encodeTuple(cret0)));
        Messages.log("P(c>0) = %f%%", 100.0 * post.get(provenance.encodeTuple(cretP)));
        Messages.log("P(c<0) = %f%%", 100.0 * post.get(provenance.encodeTuple(cretN)));
        Messages.log("P(b>0) = %f%%", 100.0 * post.get(provenance.encodeTuple(bretP)));
        Messages.log("P(b<0) = %f%%", 100.0 * post.get(provenance.encodeTuple(bretN)));
    }
}
