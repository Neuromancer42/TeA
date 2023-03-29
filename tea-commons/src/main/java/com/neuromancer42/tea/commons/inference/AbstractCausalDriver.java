package com.neuromancer42.tea.commons.inference;

import com.neuromancer42.tea.commons.util.IndexMap;

import java.nio.file.Path;
import java.util.*;

public abstract class AbstractCausalDriver {
    protected final CausalGraph causalGraph;

    protected Path workDir;
    protected final String name;

    // For debug only
    protected final Map<Integer, Object> debugQueries = new LinkedHashMap<>();

    protected AbstractCausalDriver(String name, Path path, CausalGraph causalGraph) {
        this.name = name;
        this.workDir = path;
        this.causalGraph = new CausalGraph(causalGraph);
    }

    public void appendObservations(List<Map<Object, Boolean>> traces) {
        // For debug only
//        Map<String, Boolean> obsTrace = new HashMap<>();
//        for (String tupleId : allTupleIds) {
//            if (provenance.unfoldId(tupleId).contains("labelPrimFld")) {
//                obsTrace.put(tupleId, true);
//            }
//        }
        for (Map<Object, Boolean> trace : traces) {
            appendObservation(trace);
        }
    }


    protected abstract void appendObservation(Map<Object, Boolean> obs);

    protected abstract Double queryPossibilityById(int nodeId);

    protected Double queryPossibility(Object node) {
        Integer nodeId = causalGraph.getNodeId(node);
        debugQueries.putIfAbsent(nodeId, node);
        return queryPossibilityById(nodeId);
    }

    public Map<Object, Double> queryPossibilities(Collection<Object> nodes) {
        Map<Object, Double> queryResults = new HashMap<>();
        for (Object node : nodes) {
            Double possibility = queryPossibility(node);
            if (possibility != null)
                queryResults.put(node, possibility);
        }
        return queryResults;
    }

    protected Map<Object, Double> queryAllPossibilities() {
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
