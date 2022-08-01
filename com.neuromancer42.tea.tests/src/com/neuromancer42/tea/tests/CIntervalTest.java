package com.neuromancer42.tea.tests;

import com.neuromancer42.tea.core.analyses.AnalysesUtil;
import com.neuromancer42.tea.core.inference.AbstractCausalDriver;
import com.neuromancer42.tea.core.inference.Categorical01;
import com.neuromancer42.tea.core.inference.CausalGraph;
import com.neuromancer42.tea.core.project.*;
import com.neuromancer42.tea.core.provenance.Provenance;
import com.neuromancer42.tea.libdai.DAIRuntime;
import com.neuromancer42.tea.libdai.OneShotCausalDriver;
import com.neuromancer42.tea.program.cdt.CParser;
import com.neuromancer42.tea.souffle.SouffleAnalysis;
import com.neuromancer42.tea.souffle.SouffleRuntime;
import org.junit.jupiter.api.*;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import java.io.File;
import java.util.Set;

public class CIntervalTest {
    static BundleContext context = FrameworkUtil.getBundle(CIntervalTest.class).getBundleContext();

    @BeforeAll
    public static void registerAnalyses() {
        Messages.log("Registering CParser");
        CParser cparser = new CParser();
        AnalysesUtil.registerAnalysis(context, cparser);

        Messages.log("Registering Interval");
        String dlogName = System.getProperty("dlog");
        SouffleAnalysis interval = SouffleRuntime.g().createSouffleAnalysisFromFile("Interval", "interval", new File(dlogName));
        AnalysesUtil.registerAnalysis(context, interval);

        OsgiProject.init();
    }

    @Test
    @DisplayName("Run analysis")
    public void runAnalyses() {
        Set<String> tasks = Project.g().getTasks();
        Messages.log("Found %d tasks", tasks.size());
        Assertions.assertTrue(tasks.contains("CParser"));
        Assertions.assertTrue(tasks.contains("Interval"));
        String[] taskSet = new String[1];
        taskSet[0] = "Interval";
        Project.g().run(taskSet);
        //TODO: souffle-generated relations cannot be loaded, need to fix this
        //Project.g().printRels(new String[]{"MP"});

        Assertions.assertEquals(2, OsgiProject.g().getDoneTasks().size());
        for (ITask task : OsgiProject.g().getDoneTasks()) {
            // TODO: add "Provenance-able (Trackable?)" interface
            if (task instanceof SouffleAnalysis) {
                Assertions.assertEquals("Interval", task.getName());
                Provenance provenance = ((SouffleAnalysis) task).getProvenance();
                CausalGraph<String> causalGraph = CausalGraph.buildCausalGraph(provenance,
                        cons -> new Categorical01(new double[]{0.1,0.5,0.9}),
                        input -> new Categorical01(new double[]{0.1,0.9})
                );
                AbstractCausalDriver causalDriver = new OneShotCausalDriver("test-oneshot-interval", causalGraph);
                // TODO: load observations
            }
        }
    }
}
