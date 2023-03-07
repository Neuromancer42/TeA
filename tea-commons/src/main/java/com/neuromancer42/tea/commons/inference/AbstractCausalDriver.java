package com.neuromancer42.tea.commons.inference;

import com.neuromancer42.tea.commons.util.IndexMap;

import java.nio.file.Path;
import java.util.*;

public abstract class AbstractCausalDriver {
    protected final CausalGraph causalGraph;

    protected Path workDir;
    protected final String name;

    protected AbstractCausalDriver(String name, Path path, CausalGraph causalGraph) {
        this.name = name;
        this.workDir = path;
        this.causalGraph = new CausalGraph(causalGraph);
    }

    public void run(List<Map<String, Boolean>> traces) {
        // For debug only
//        Map<String, Boolean> obsTrace = new HashMap<>();
//        for (String tupleId : allTupleIds) {
//            if (provenance.unfoldId(tupleId).contains("labelPrimFld")) {
//                obsTrace.put(tupleId, true);
//            }
//        }
        for (Map<String, Boolean> trace : traces) {
            appendObservation(trace);
        }
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

    public String getName() {
        return name;
    }
}
