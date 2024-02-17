package com.neuromancer42.tea.libdai;

import com.neuromancer42.tea.commons.inference.AbstractCausalDriver;
import com.neuromancer42.tea.commons.inference.CausalGraph;
import com.neuromancer42.tea.commons.configs.Messages;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

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
    public void appendObservation(Map<Object, Boolean> observations) {
        // 1. dumping factor graph
        dumpNetwork();
        ++updateCnt;
        // TODO: dump parameter weights only
        // 2. dumping observations
        for (var obsEntry : observations.entrySet()) {
            Integer nodeId = causalGraph.getNodeId(obsEntry.getKey());
            // Note: Observations may return nodes not in the derivation due to mix of instruments
            if (nodeId != null)
                metaNetwork.observeNode(nodeId, 0, obsEntry.getValue());
        }
        // 2.(1) (debug) dumping intermediate results for previous queries
        Map<Integer, Double> debugQueryProbs = new HashMap<>();
        for (Integer nodeId : debugQueries.keySet()) {
            double prob = metaNetwork.predictNode(nodeId);
            debugQueryProbs.put(nodeId, prob);
        }
        List<Map.Entry<Integer, Double>> sortedDebugQueryProbs = debugQueryProbs.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .toList();
        List<String> debugLines = new ArrayList<>();
        for (var debugQueryProb : sortedDebugQueryProbs) {
            Integer nodeId = debugQueryProb.getKey();
            Double prob = debugQueryProb.getValue();
            Object repr = debugQueries.get(nodeId);
            debugLines.add(nodeId + "\t" + prob + "\t" + repr.hashCode());
        }
        try {
            Path debugListPath = workDir.resolve(String.format("%03d", updateCnt)+".local.list");
            Files.write(debugListPath, debugLines, StandardCharsets.UTF_8);
            Messages.debug("IteratingCausalDriver %s: dump local ranking of queries to path %s", name, debugListPath.toAbsolutePath());
        } catch (IOException e) {
            Messages.error("IteratingCausalDriver %s: failed to dump local ranking, skip. ErrMsg: %s", name, e.getMessage());
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
            if (metaNetwork != null) {
                metaNetwork.dumpQueries(workDir.resolve(String.format("%03d.query", updateCnt)));
                metaNetwork.release();
                metaNetwork = null;
            }
            metaNetwork = DAIMetaNetwork.createDAIMetaNetwork(workDir, String.format("%03d.post", updateCnt), causalGraph, 0, true, false);
            updated = true;
        }
    }
}
