package com.neuromancer42.tea.tests;

import com.neuromancer42.tea.core.inference.AbstractCausalDriver;
import com.neuromancer42.tea.core.inference.Categorical01;
import com.neuromancer42.tea.core.inference.CausalGraph;
import com.neuromancer42.tea.core.inference.ICausalDriverFactory;
import com.neuromancer42.tea.core.project.*;
import com.neuromancer42.tea.core.provenance.IProvable;
import com.neuromancer42.tea.core.provenance.Provenance;
import com.neuromancer42.tea.core.provenance.Tuple;
import com.neuromancer42.tea.core.util.Timer;
import org.junit.jupiter.api.*;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import java.nio.file.Path;
import java.util.*;

public class CIntervalTest {
    static BundleContext context = FrameworkUtil.getBundle(CIntervalTest.class).getBundleContext();
    private Provenance provenance;

    @BeforeAll
    public static void registerAnalyses() {
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
        provenance = ((IProvable) task).getProvenance();
        CausalGraph<String> causalGraph = CausalGraph.buildCausalGraph(provenance,
                cons -> new Categorical01(0.1,0.5,1.0),
                input -> new Categorical01(0.1,0.5,1.0)
        );
        causalGraph.dump(Path.of(Config.v().outDirName));

        ICausalDriverFactory causalDriverFactory = context.getService(context.getServiceReference(ICausalDriverFactory.class));
        if (causalDriverFactory == null) {
            Messages.error("no causal driver loaded");
            assert false;
        }
        assert Arrays.asList(causalDriverFactory.getAlgorithms()).contains("oneshot");
//        AbstractCausalDriver oneShotCausalDriver = causalDriverFactory.createCausalDriver("oneshot", "test-oneshot-interval", causalGraph);
//        runCausalDriver(oneShotCausalDriver);

        assert Arrays.asList(causalDriverFactory.getAlgorithms()).contains("iterating");
//        AbstractCausalDriver iteratingCausalDriver = causalDriverFactory.createCausalDriver("iterating", "test-iterating-interval", causalGraph);
//        runCausalDriver(iteratingCausalDriver);


        AbstractCausalDriver dynaDriver = causalDriverFactory.createCausalDriver("dynaboost", "test-dynaboost-interval", causalGraph);
        runCausalDriver(dynaDriver);
    }

    private void runCausalDriver(AbstractCausalDriver causalDriver) {
        Timer timer = new Timer(causalDriver.getName());
        Messages.log("ENTER: "+ causalDriver.getName() + " at " + (new Date()));
        timer.init();

        Tuple cretP = new Tuple("ci_PHval", 22, 4, 11);
        Tuple cretN = new Tuple("ci_PHval", 22, 4, 3);
        Tuple cret0 = new Tuple("ci_PHval", 22, 4, 7);
        Tuple[] bPs = new Tuple[8];
        Tuple[] bNs = new Tuple[8];
        for (int i = 15; i <= 22; ++i) {
            bPs[i-15] = new Tuple("ci_PHval", i, 3, 10);
            bNs[i-15] = new Tuple("ci_PHval", i, 3, 4);
        }
        List<String> queries = new ArrayList<>(List.of(provenance.encodeTuple(cretP), provenance.encodeTuple(cretN), provenance.encodeTuple(cret0)));
        for (Tuple t : bPs) {
            queries.add(provenance.encodeTuple(t));
        }
        for (Tuple t: bNs) {
            queries.add(provenance.encodeTuple(t));
        }
        List<Map<String, Boolean>> trace = new ArrayList<>();
        for (int i = 0; i < 10; ++i) {
            Map<String, Boolean> obs = new HashMap<>();
            obs.put(provenance.encodeTuple(cret0), false);
            if (i % 4 == 0) {
                obs.put(provenance.encodeTuple(cretN), true);
                obs.put(provenance.encodeTuple(cretP), false);
            } else {
                obs.put(provenance.encodeTuple(cretN), false);
                obs.put(provenance.encodeTuple(cretP), true);
            }
            trace.add(obs);
        }

        Map<String, Double> prior = causalDriver.queryPossibilities(queries);
        Messages.log("Prior: " + causalDriver.getName());
        Messages.log("P(ret#c=0) = %f%%", 100.0 * prior.get(provenance.encodeTuple(cret0)));
        Messages.log("P(ret#c>0) = %f%%", 100.0 * prior.get(provenance.encodeTuple(cretP)));
        Messages.log("P(ret#c<0) = %f%%", 100.0 * prior.get(provenance.encodeTuple(cretN)));
        for (int i = 15; i <= 22; ++i) {
            Messages.log("P(%d#b>0) = %f%%", i, 100.0 * prior.get(provenance.encodeTuple(bPs[i-15])));
            Messages.log("P(%d#b<0) = %f%%", i, 100.0 * prior.get(provenance.encodeTuple(bNs[i-15])));
        }
        causalDriver.run(trace);
        Map<String, Double> post = causalDriver.queryPossibilities(queries);
        Messages.log("Posterior: " + causalDriver.getName());
        Messages.log("P(ret#c=0) = %f%%", 100.0 * post.get(provenance.encodeTuple(cret0)));
        Messages.log("P(ret#c>0) = %f%%", 100.0 * post.get(provenance.encodeTuple(cretP)));
        Messages.log("P(ret#c<0) = %f%%", 100.0 * post.get(provenance.encodeTuple(cretN)));
        for (int i = 15; i <= 22; ++i) {
            Messages.log("P(%d#b>0) = %f%%", i, 100.0 * post.get(provenance.encodeTuple(bPs[i-15])));
            Messages.log("P(%d#b<0) = %f%%", i, 100.0 * post.get(provenance.encodeTuple(bNs[i-15])));
        }


        timer.done();
        Messages.log("LEAVE: " + causalDriver.getName());
        Timer.printTimer(timer);
    }
}
