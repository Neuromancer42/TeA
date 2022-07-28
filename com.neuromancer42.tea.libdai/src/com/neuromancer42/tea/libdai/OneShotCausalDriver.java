package com.neuromancer42.tea.libdai;

import com.neuromancer42.tea.core.inference.AbstractCausalDriver;
import com.neuromancer42.tea.core.inference.CausalGraph;
import com.neuromancer42.tea.core.project.Messages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class OneShotCausalDriver extends AbstractCausalDriver {
    private final Queue<Map<String, Boolean>> obsHistory = new LinkedList<>();
    private boolean updated;
    private DAIMetaNetwork metaNetwork;

    public OneShotCausalDriver(String name, CausalGraph<String> causalGraph) {
        super(name, causalGraph);
        Path workDir1 = null;
        try {
            workDir1 = Files.createDirectories(DAIRuntime.g().getWorkDir().resolve(name));
        } catch (IOException e) {
            Messages.error("IteratingInferer: failed to create working directory");
            Messages.fatal(e);
        }
        workDir = workDir1;
        updated = false;
    }


    @Override
    protected void appendObservation(Map<String, Boolean> obs) {
        obsHistory.offer(obs);
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
        // 1. dump N-factor-graph
        if (metaNetwork != null) {
            metaNetwork = DAIMetaNetwork.createDAIMetaNetwork(workDir, name+"_"+obsHistory.size(), causalGraph, obsHistory.size());
        }
        // 2. dump observation
        for (int timeId = 1; !obsHistory.isEmpty(); timeId++) {
            Map<String, Boolean> obs = obsHistory.poll();
            for (var obsEntry : obs.entrySet()) {
                Integer nodeId = causalGraph.getNodeId(obsEntry.getKey());
                metaNetwork.observeNode(nodeId, timeId, obsEntry.getValue());
            }
        }
        // Note: invoking inference engine is done when querying
    }

}