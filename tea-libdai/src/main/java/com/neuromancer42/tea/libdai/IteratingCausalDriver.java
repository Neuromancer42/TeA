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

    public IteratingCausalDriver(String name, CausalGraph<String> causalGraph) {
        super(name, causalGraph);
        // set up libDAI runtime
        Path workDir1 = null;
        try {
            workDir1 = Files.createDirectories(DAIRuntime.g().getWorkDir().resolve(name));
        } catch (IOException e) {
            Messages.error("IteratingInferer: failed to create working directory");
            Messages.fatal(e);
        }
        workDir = workDir1;
        updated = false;
        updateCnt = 0;
    }

    public IteratingCausalDriver(CausalGraph<String> causalGraph) {
        this(causalGraph.getName(), causalGraph);
    }

    @Override
    public void appendObservation(Map<String, Boolean> observations) {
        // 1. dumping factor graph
        ++updateCnt;
        DAIMetaNetwork metaNetwork = DAIMetaNetwork.createDAIMetaNetwork(workDir, name+"."+updateCnt+".prior", causalGraph, 0);
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
