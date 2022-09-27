package com.neuromancer42.tea.core.inference;

import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.util.IndexMap;
import com.neuromancer42.tea.core.util.Timer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public abstract class AbstractCausalDriver {
    protected final CausalGraph<String> causalGraph;

    protected Path workDir;
    protected final String name;

    protected AbstractCausalDriver(String name, CausalGraph<String> causalGraph) {
        this.name = name;
        this.causalGraph = causalGraph;
    }

    public void run(List<Map<String, Boolean>> traces) {
        com.neuromancer42.tea.core.util.Timer timer = new Timer("causal-driver");
        Messages.log("ENTER: causal-driver at " + (new Date()));
        timer.init();

        Map<String, Double> queryResults = queryAllPossibilities();
        Map<String, Double> priorQueryResults = queryResults;
        //causalGraph.dumpDot("causal_prior.dot", (idx) -> (provenance.unfoldId(idx) + "\n" + priorQueryResults.get(idx)), Categorical01::toString);
        causalGraph.dumpDot(workDir.resolve("causal_prior.dot"), (idx) -> (idx + "\n" + priorQueryResults.get(idx)), Categorical01::toString);

        // For debug only
//        Map<String, Boolean> obsTrace = new HashMap<>();
//        for (String tupleId : allTupleIds) {
//            if (provenance.unfoldId(tupleId).contains("labelPrimFld")) {
//                obsTrace.put(tupleId, true);
//            }
//        }
        for (int i = 0; i < traces.size(); i++) {
            appendObservation(traces.get(i));
            queryResults = queryAllPossibilities();
            Map<String, Double> curQueryResults = queryResults;
            causalGraph.dumpDot(workDir.resolve("causal_post-" + i + ".dot"), (idx) -> (idx + "\n" + curQueryResults.get(idx)), Categorical01::toString);
        }

        timer.done();
        Messages.log("LEAVE: causal-driver");
        Timer.printTimer(timer);
    }


    protected abstract void appendObservation(Map<String, Boolean> obs);

    protected abstract Double queryPossibilityById(int nodeId);

    protected Double queryPossibility(String node) {
        Integer nodeId = causalGraph.getNodeId(node);
        return queryPossibilityById(nodeId);
    }

    public Map<String, Double> queryPossibilities(Collection<String> nodes) {
        Map<String, Double> queryResults = new HashMap<>();
        for (String node : nodes) {
            Double possibility = queryPossibility(node);
            if (possibility != null)
                queryResults.put(node, possibility);
        }
        return queryResults;
    }

    protected Map<String, Double> queryAllPossibilities() {
        return queryPossibilities(causalGraph.getAllNodes());
    }

    protected abstract double[] queryFactorById(int distId);

    protected void updateAllFactors() {
        IndexMap<Categorical01> distNodes = causalGraph.getAllDistNodes();
        for (int distId = 0; distId < distNodes.size(); distId++) {
            double[] factor = queryFactorById(distId);
            distNodes.get(distId).updateProbs(factor);
        }
    }

}
