package com.neuromancer42.tea.libdai;

import com.neuromancer42.tea.commons.inference.AbstractCausalDriver;
import com.neuromancer42.tea.commons.inference.CausalGraph;

import java.nio.file.Path;
import java.util.*;

public class EMCausalDriver extends AbstractCausalDriver {

    public static final String type = "em";
    private final List<Map<Object, Boolean>> obsHistory = new ArrayList<>();

    private boolean updated;
    private DAIMetaNetwork metaNetwork;

    protected EMCausalDriver(String name, Path path, CausalGraph causalGraph) {
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
            invokeLearner();
            updated = true;
        }
        return metaNetwork.predictNode(nodeId);
    }

    @Override
    protected double[] queryFactorById(int distId) {
        if (!updated) {
            invokeLearner();
            updated = true;
        }
        return metaNetwork.queryParamPosterior(distId);
    }

    private void invokeLearner() {
        // release old causal graph for memory performance
        if (metaNetwork != null) {
            metaNetwork.dumpQueries(workDir.resolve("em.query"));
            metaNetwork.release();
            metaNetwork = null;
        }
        String fileName = String.format("em_%03d", obsHistory.size());
        metaNetwork = DAIMetaNetwork.createDAIMetaNetwork(workDir, fileName, causalGraph, 0, false);
        if (obsHistory.size() > 0) {
            metaNetwork.runEM(workDir, fileName, obsHistory);
        }
    }
}
