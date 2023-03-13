package com.neuromancer42.tea.libdai;

import com.neuromancer42.tea.commons.inference.AbstractCausalDriver;
import com.neuromancer42.tea.commons.inference.CausalGraph;
import com.neuromancer42.tea.commons.configs.Messages;

import java.nio.file.Path;
import java.util.Map;

public class IteratingCausalDriver extends AbstractCausalDriver {
    public static final String type = "iterating";

    private DAIMetaNetwork metaNetwork;
    private boolean updated;
    private int updateCnt;

    public IteratingCausalDriver(String name, Path path, CausalGraph causalGraph) {
        super(name, path, causalGraph);
        updated = false;
        updateCnt = 0;
    }

    @Override
    public void appendObservation(Map<String, Boolean> observations) {
        // 1. dumping factor graph
        dumpNetwork();
        ++updateCnt;
        // TODO: dump parameter weights only
        // 2. dumping observations
        for (var obsEntry : observations.entrySet()) {
            Integer nodeId = causalGraph.getNodeId(obsEntry.getKey());
            metaNetwork.observeNode(nodeId, 0, obsEntry.getValue());
        }
        // 3. inference and update parameters
        updateAllFactors();
        updated = false;
    }

    @Override
    public Double queryPossibilityById(int nodeId) {
        // 1. dumping updated factor graph
        dumpNetwork();
        // 2. predicting specific node
        return metaNetwork.predictNode(nodeId);
    }

    @Override
    public double[] queryFactorById(int paramId) {
        dumpNetwork();
        return metaNetwork.queryParamPosterior(paramId);
    }

    private void dumpNetwork() {
        if (!updated) {
            Messages.log("IteratingDriver: dumping updated factor graph of previous observations");
            if (metaNetwork != null)
                metaNetwork.release();
            metaNetwork = DAIMetaNetwork.createDAIMetaNetwork(workDir, name+"."+String.format("%03d", updateCnt)+".post", causalGraph, 0);
            updated = true;
        }
    }
}
