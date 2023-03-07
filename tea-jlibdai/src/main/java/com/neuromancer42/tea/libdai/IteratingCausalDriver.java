package com.neuromancer42.tea.libdai;

import com.neuromancer42.tea.commons.inference.AbstractCausalDriver;
import com.neuromancer42.tea.commons.inference.CausalGraph;
import com.neuromancer42.tea.commons.configs.Messages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class IteratingCausalDriver extends AbstractCausalDriver {

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
        if (!updated) {
            Messages.log("IteratingDriver: dumping updated factor graph of previous observations");
            if (metaNetwork != null)
                metaNetwork.release();
            metaNetwork = DAIMetaNetwork.createDAIMetaNetwork(workDir, name + "." + updateCnt + ".post", causalGraph, 0);
            updated = true;
        }
        ++updateCnt;
        // TODO: dump parameter weights only
        // 2. dumping observations
        for (var obsEntry : observations.entrySet()) {
            Integer nodeId = causalGraph.getNodeId(obsEntry.getKey());
            metaNetwork.observeNode(nodeId, 0, obsEntry.getValue());
        }
        // 3. inference and update parameters
        for (int distId = 0; distId < causalGraph.distSize(); ++distId) {
            double[] weights = metaNetwork.queryParamPosterior(distId);
            causalGraph.getAllDistNodes().get(distId).updateProbs(weights);
        }
        updated = false;
    }

    @Override
    public Double queryPossibilityById(int nodeId) {
        // 1. dumping updated factor graph
        if (!updated) {
            if (metaNetwork != null)
                metaNetwork.release();
            metaNetwork = DAIMetaNetwork.createDAIMetaNetwork(workDir, name+"."+updateCnt+".post", causalGraph, 0);
            updated = true;
        }
        // 2. predicting specific node
        return metaNetwork.predictNode(nodeId);
    }

    @Override
    public double[] queryFactorById(int paramId) {
        // fetching from updated causal graph
        return causalGraph.getAllDistNodes().get(paramId).getProbabilitis();
    }

}
