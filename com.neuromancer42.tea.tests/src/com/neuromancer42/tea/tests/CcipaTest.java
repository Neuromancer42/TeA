package com.neuromancer42.tea.tests;

import com.neuromancer42.tea.core.analyses.AnalysesUtil;
import com.neuromancer42.tea.core.inference.AbstractCausalDriver;
import com.neuromancer42.tea.core.inference.Categorical01;
import com.neuromancer42.tea.core.inference.CausalGraph;
import com.neuromancer42.tea.core.project.*;
import com.neuromancer42.tea.core.provenance.Provenance;
import com.neuromancer42.tea.libdai.OneShotCausalDriver;
import com.neuromancer42.tea.program.cdt.CMemoryModel;
import com.neuromancer42.tea.program.cdt.CParserAnalysis;
import com.neuromancer42.tea.program.cdt.PreIntervalAnalysis;
import com.neuromancer42.tea.souffle.SouffleAnalysis;
import com.neuromancer42.tea.souffle.SouffleRuntime;
import org.junit.jupiter.api.*;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import java.io.File;
import java.util.Set;

public class CcipaTest {
    static BundleContext context = FrameworkUtil.getBundle(CcipaTest.class).getBundleContext();

    @BeforeAll
    public static void registerAnalyses() {
        Messages.log("Registering CParser");
        CParserAnalysis cparser = new CParserAnalysis();
        AnalysesUtil.registerAnalysis(context, cparser);

        Messages.log("Registering CMemModel");
        CMemoryModel cMemModel = new CMemoryModel();
        AnalysesUtil.registerAnalysis(context, cMemModel);

        Messages.log("Registering PreInterval");
        PreIntervalAnalysis preInterval = new PreIntervalAnalysis();
        AnalysesUtil.registerAnalysis(context, preInterval);

        Messages.log("Registering pre_pt.dl");
        String dlogName0 = System.getProperty("dlog0");
        SouffleAnalysis pre = SouffleRuntime.g().createSouffleAnalysisFromFile("PrePointer", "pre_pt", new File(dlogName0));
        AnalysesUtil.registerAnalysis(context, pre);

        Messages.log("Registering cipa_cg.dl");
        String dlogName1 = System.getProperty("dlog1");
        SouffleAnalysis cipa = SouffleRuntime.g().createSouffleAnalysisFromFile("ciPointerAnalysis", "cipa_cg", new File(dlogName1));
        AnalysesUtil.registerAnalysis(context, cipa);

        Messages.log("Registering interval.dl");
        String dlogName2 = System.getProperty("dlog2");
        SouffleAnalysis interval = SouffleRuntime.g().createSouffleAnalysisFromFile("interval", "interval", new File(dlogName2));
        AnalysesUtil.registerAnalysis(context, interval);

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
        String[] taskSet = new String[2];
        taskSet[0] = "ciPointerAnalysis";
        taskSet[1] = "interval";
        Project.g().run(taskSet);
        //TODO: souffle-generated relations cannot be loaded, need to fix this
        Project.g().printRels(new String[]{"MP", "ci_hpt", "ci_Hval", "ci_Vval"});

        Assertions.assertEquals(6, OsgiProject.g().getDoneTasks().size());
        ITask task = OsgiProject.g().getTask("ciPointerAnalysis");
        Provenance provenance = ((SouffleAnalysis) task).getProvenance();
        CausalGraph<String> causalGraph = CausalGraph.buildCausalGraph(provenance,
                cons -> new Categorical01(new double[]{0.1,0.5,0.9}),
                input -> new Categorical01(new double[]{0.1,0.9})
        );
        AbstractCausalDriver causalDriver = new OneShotCausalDriver("test-oneshot-interval", causalGraph);
        // TODO: load observations
    }
}
