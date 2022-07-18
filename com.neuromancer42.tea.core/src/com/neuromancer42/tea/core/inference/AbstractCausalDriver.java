package com.neuromancer42.tea.core.inference;

import com.neuromancer42.tea.core.project.Config;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.ProcessExecutor;
import com.neuromancer42.tea.core.util.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.*;

public abstract class AbstractCausalDriver {
    protected final CausalGraph<String> causalGraph;

    protected Path workDir;

    protected AbstractCausalDriver(CausalGraph<String> causalGraph) {
        this.causalGraph = causalGraph;
    }

    public void run(List<Map<String, Boolean>> traces) {
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
    }


    protected abstract void appendObservation(Map<String, Boolean> obs);

    protected abstract Double queryPossibilityById(int nodeId);

    protected Double queryPossibility(String node) {
        Integer nodeId = causalGraph.getNodeId(node);
        return queryPossibilityById(nodeId);
    }

    protected Map<String, Double> queryPossibilities(Collection<String> nodes) {
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
        for (int distId = 0; distId < causalGraph.getAllDistNodes().size(); distId++) {
            double[] factor = queryFactorById(distId);
            causalGraph.getAllDistNodes().get(distId).updateProbs(factor);
        }
    }

}
