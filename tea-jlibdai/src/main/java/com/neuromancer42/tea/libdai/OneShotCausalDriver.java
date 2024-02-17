package com.neuromancer42.tea.libdai;

import com.neuromancer42.tea.commons.inference.AbstractCausalDriver;
import com.neuromancer42.tea.commons.inference.CausalGraph;
import com.neuromancer42.tea.commons.configs.Messages;

import java.nio.file.Path;
import java.util.*;

public class OneShotCausalDriver extends AbstractCausalDriver {
    public static final String type = "oneshot";
    private final List<Map<Object, Boolean>> obsHistory = new ArrayList<>();
    private boolean updated;
    private DAIMetaNetwork metaNetwork;

    public OneShotCausalDriver(String name, Path path, CausalGraph causalGraph) {
        super(name, path, causalGraph);
        updated = false;
    }


    @Override
    protected void appendObservation(Map<Object, Boolean> obs) {
        obsHistory.add(obs);
        // drop previous results
        updated = false;
    }

    @Override
    protected Double queryPossibilityById(int nodeId) {
        if (!updated) {
            invokeUpdater();
            updated = true;
        }
        return metaNetwork.predictNode(nodeId);
    }

    @Override
    protected double[] queryFactorById(int distId) {
        if (!updated) {
            invokeUpdater();
            updated = true;
        }
        return metaNetwork.queryParamPosterior(distId);
    }


    private void invokeUpdater() {
        // 1. dump full N-factor-graph for each time of inference
        if (metaNetwork != null) {
            metaNetwork.dumpQueries(workDir.resolve(name + ".query"));
            metaNetwork.release();
            metaNetwork = null;
        }
        metaNetwork = DAIMetaNetwork.createDAIMetaNetwork(workDir, name+"_"+obsHistory.size(), causalGraph, obsHistory.size(), true, false);
        // 2. dump observation
        for (int timeId = 1; timeId <= obsHistory.size(); timeId++) {
            Map<Object, Boolean> obs = obsHistory.get(timeId - 1);
            for (var obsEntry : obs.entrySet()) {
                Integer nodeId = causalGraph.getNodeId(obsEntry.getKey());
                metaNetwork.observeNode(nodeId, timeId, obsEntry.getValue());
            }
        }
        // Note: invoking inference engine is done when querying
    }

}
