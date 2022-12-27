package com.neuromancer42.tea.libdai;

import com.neuromancer42.tea.commons.inference.CausalGraph;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.libdai.swig.DoubleVector;
import com.neuromancer42.tea.libdai.swig.LibDAISWIGFactorGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class DAIMetaNetwork {
    // A MetaNetwork associate a causal-graph to a concrete (LibDAI) factor graph
    // It binds the causal-graph nodes to factor graph variables
    // Parameter: distId --> distId
    // ObserveNode: <nodeId, time> --> distSize + (time + 1) * shift + nodeId (time starts from 0)
    // PredictNode: nodeId -->  distSize + nodeId

    private final CausalGraph<String> causalGraph;
    private final LibDAISWIGFactorGraph swigFactorGraph;
    private final int offset;
    private final int shift;
    private final int numRepeats;
    private boolean activated;

    public static DAIMetaNetwork createDAIMetaNetwork(Path dumpDir, String name, CausalGraph<String> causalGraph, int numRepeats) {
        Path fgFilePath = dumpDir.resolve(name+".fg");
        int subSize = causalGraph.nodeSize();
        try {
            BufferedWriter bw = Files.newBufferedWriter(fgFilePath, StandardCharsets.UTF_8);
            PrintWriter pw = new PrintWriter(bw);
            subSize = DAIRuntime.dumpRepeatedFactorGraph(pw, causalGraph, numRepeats);
            Messages.debug("DAIFactorGraph: dumping factor graph to path " + fgFilePath);
        } catch (IOException e) {
            Messages.error("DAIFacetorGraph: failed to dump factor graph.");
            Messages.fatal(e);
        }
        return new DAIMetaNetwork(fgFilePath, causalGraph, numRepeats, subSize);
    }

    private DAIMetaNetwork(Path fgFilePath, CausalGraph<String> causalGraph, int numRepeats, int subSize) {
        this.causalGraph = causalGraph;
        this.swigFactorGraph = new LibDAISWIGFactorGraph(fgFilePath.toAbsolutePath().toString());
        this.shift = subSize;
        this.offset = causalGraph.distSize();
        this.numRepeats = numRepeats;
        this.activated = false;
    }

    public void observeNode(int nodeId, int time, boolean value) {
        if (activated) {
            Messages.fatal("DAIMetaNetwork: inference has been activated, no observation.");
        }
        if (nodeId >= causalGraph.nodeSize() || nodeId < 0) {
            Messages.fatal("DAIMetaNetwork: observing node " + nodeId + " is not a causal node.");
        }
        if (time > numRepeats) {
            Messages.fatal("DAIMetaNetwork: observation time " + time + " exceeds meta-network size " + numRepeats + ".");
        }
        int fgVarId = offset + time * shift + nodeId;
        swigFactorGraph.observeBernoulli(fgVarId, value);
    }

    public double predictNode(int nodeId) {
        if (!activated) {
            swigFactorGraph.runBP();
            activated = true;
        }
        if (nodeId >= causalGraph.nodeSize() || nodeId < 0) {
            Messages.fatal("DAIMetaNetwork: querying node " + nodeId + " is not a causal node.");
        }
        int fgVarId = offset + nodeId;
        return swigFactorGraph.queryBernoulliParam(fgVarId);
    }

    public double[] queryParamPosterior(int distId) {
        if (!activated) {
            swigFactorGraph.runBP();
            activated = true;
        }
        if (distId >= offset || distId < 0) {
            Messages.fatal("DAIMetaNetwork: querying node " + distId + " is not a distribution node.");
        }
        DoubleVector factor = swigFactorGraph.queryParamFactor(distId);
        double[] weights = new double[factor.size()];
        for (int i = 0; i < factor.size(); ++i) {
            weights[i] = factor.get(i);
        }
        return weights;
    }

}
