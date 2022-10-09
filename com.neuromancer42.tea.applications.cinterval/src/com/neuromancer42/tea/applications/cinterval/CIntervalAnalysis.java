package com.neuromancer42.tea.applications.cinterval;

import com.neuromancer42.tea.core.application.AbstractApplication;
import com.neuromancer42.tea.core.inference.AbstractCausalDriver;
import com.neuromancer42.tea.core.inference.Categorical01;
import com.neuromancer42.tea.core.inference.CausalGraph;
import com.neuromancer42.tea.core.inference.ICausalDriverFactory;
import com.neuromancer42.tea.core.project.*;
import com.neuromancer42.tea.core.provenance.IProvable;
import com.neuromancer42.tea.core.provenance.Provenance;
import com.neuromancer42.tea.core.provenance.Tuple;
import com.neuromancer42.tea.core.util.Timer;

import java.nio.file.Path;
import java.util.*;

public class CIntervalAnalysis extends AbstractApplication {

    private Provenance provenance;

    @Override
    protected String getName() {
        return "CInterval";
    }

    @Override
    public void runAnalyses(OsgiProject project) {
        project.requireTasks("ciPointerAnalysis", "interval", "InputMarker");
        Set<String> tasks = project.getTasks();
        Messages.log("CIntervalAnalysis: Found %d tasks", tasks.size());
        String[] taskSet = new String[3];
        taskSet[0] = "ciPointerAnalysis";
        taskSet[1] = "interval";
        taskSet[2] = "InputMarker";
        project.run(taskSet);
        project.printRels(new String[]{"ExtMeth", "MP", "ci_IM", "ci_hpt", "ci_pt", "StorePtr", "MaySat", "MayUnsat", "evalBinopU", "evalUnaryU", "P_strong_update", "P_weak_update", "PredL", "PredR", "Pred2", "ci_PHval", "ci_Vval", "retInput", "argInput"});

        ITask task = project.getTask("interval");
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

        String driverType = System.getProperty("tea.inference.driver", "oneshot");

        assert Arrays.asList(causalDriverFactory.getAlgorithms()).contains("oneshot");
        assert Arrays.asList(causalDriverFactory.getAlgorithms()).contains("iterating");


        AbstractCausalDriver driver = causalDriverFactory.createCausalDriver(driverType, "test-" + driverType + "-interval", causalGraph);
        runCausalDriver(driver);
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
