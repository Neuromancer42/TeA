package com.neuromancer42.tea.libdai;


import com.neuromancer42.tea.commons.inference.CausalGraph;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.libdai.swig.DoubleVector;
import com.neuromancer42.tea.libdai.swig.LibDAISWIGFactorGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class DAIMetaNetwork {
    // A MetaNetwork associate a causal-graph to a concrete (LibDAI) factor graph
    // It binds the causal-graph nodes to factor graph variables
    // Parameter: distId --> distId
    // ObserveNode: <nodeId, time> --> distSize + (time + 1) * shift + nodeId (time starts from 0)
    // PredictNode: nodeId -->  distSize + nodeId

    private final CausalGraph causalGraph;
    private final LibDAISWIGFactorGraph swigFactorGraph;
    private final int offset;
    private final int shift;
    private final int numRepeats;
    private boolean activated;

    static int maxiter = 10000000;
    static int maxtime = 10800;
    static double tol = 1e-6;

    public static DAIMetaNetwork createDAIMetaNetwork(Path dumpDir, String name, CausalGraph causalGraph, int numRepeats, boolean bayes) {
        Path fgFilePath = dumpDir.resolve(name+".fg");
        int subSize = causalGraph.nodeSize();
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(fgFilePath, StandardCharsets.UTF_8))){
            subSize = DAIRuntime.dumpRepeatedFactorGraph(pw, causalGraph, numRepeats, bayes);
            Messages.debug("DAIFactorGraph: dumping factor graph to path " + fgFilePath);
        } catch (IOException e) {
            Messages.error("DAIFacetorGraph: failed to dump factor graph.");
            Messages.fatal(e);
        }
        return new DAIMetaNetwork(fgFilePath, causalGraph, numRepeats, subSize);
    }

    private DAIMetaNetwork(Path fgFilePath, CausalGraph causalGraph, int numRepeats, int subSize) {
        this.causalGraph = causalGraph;
        this.swigFactorGraph = new LibDAISWIGFactorGraph(fgFilePath.toAbsolutePath().toString(), maxiter, maxtime, tol);
        this.shift = subSize;
        this.offset = causalGraph.distSize();
        this.numRepeats = numRepeats;
        this.activated = false;
    }

    public void observeNode(int nodeId, int time, boolean value) {
        if (activated) {
            Messages.log("DAIMetaNetwork: inference has been activated, reset algorithm.");
            swigFactorGraph.resetBP();
            activated = false;
        }
        if (nodeId >= causalGraph.nodeSize() || nodeId < 0) {
            Messages.fatal("DAIMetaNetwork: observing node " + nodeId + " is not a causal node.");
        }
        if (time > numRepeats) {
            Messages.fatal("DAIMetaNetwork: observation time " + time + " exceeds meta-network size " + numRepeats + ".");
        }
        int fgVarId = offset + time * shift + nodeId;
        Messages.debug("IteratingInferer: observing node %d(%d@%d) to be %s", fgVarId, nodeId, time, value);
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
        Messages.debug("DAIMetaNetwork: quering node %d, fgVarId %d", nodeId, fgVarId);
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
        Messages.debug("DAIMetaNetwork: quering factor %d", distId);
        DoubleVector factor = swigFactorGraph.queryParamFactor(distId);
        double[] weights = new double[factor.size()];
        for (int i = 0; i < factor.size(); ++i) {
            weights[i] = factor.get(i);
        }
        return weights;
    }

    public void release() {
        swigFactorGraph.delete();
    }

    public void runEM(Path dumpDir, String name, List<Map<Object, Boolean>> obsHistory) {
        if (activated) {
            Messages.warn("DAIFactorGraph: learning has been activated before, are you sure to run it again?");
        }
        // 1. dump evidence file
        Path tabFilePath = dumpDir.resolve(name + ".tab");
        Messages.log("DAIFactorGraph: dumping evidence to %s", tabFilePath.toAbsolutePath().toString());
        Set<Integer> obsNodeIds = new LinkedHashSet<>();
        for (Map<Object, Boolean> obs : obsHistory) {
            for (Object obsNode : obs.keySet()) {
                obsNodeIds.add(causalGraph.getNodeId(obsNode));
            }
        }
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(tabFilePath, StandardCharsets.UTF_8))) {
            // The header line consists of the variable labels, followed by an empty line
            boolean firstItem = true;
            for (var nodeId : obsNodeIds) {
                if (firstItem) {
                    firstItem = false;
                } else {
                    pw.print("\t");
                }
                pw.print(nodeId + offset);
            }
            pw.println();
            // The other lines are observed joint states of the variables
            for (var obs : obsHistory) {
                pw.println();
                firstItem = true;
                for (var nodeId : obsNodeIds) {
                    if (firstItem) {
                        firstItem = false;
                    } else {
                        pw.print("\t");
                    }
                    Object obsNode = causalGraph.getNode(nodeId);
                    if (obs.containsKey(obsNode)) {
                        pw.print(obs.get(obsNode) ? 1 : 0);
                    }
                    // Missing data is handled simply by having two consecutive tab characters
                }
            }
        } catch (IOException e) {
            Messages.error("DAIFacetorGraph: failed to dump evidence.");
            Messages.fatal(e);
        }

        // 2. dump em specification, see libdai/examples
        Path emFilePath = dumpDir.resolve(name + ".em");
        Messages.log("DAIFactorGraph: dumping em spec to %s", emFilePath.toAbsolutePath().toString());

        Map<Integer, Integer> paramFactors = new LinkedHashMap<>();
        for (int distId = 0; distId < causalGraph.distSize(); ++distId) {
            paramFactors.put(distId, distId);
        }
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(emFilePath, StandardCharsets.UTF_8))) {
            pw.println(1);
            pw.println();
            pw.println(paramFactors.size());
            for (var entry : paramFactors.entrySet()) {
                int bernoulli_dim = 2;
                pw.println(String.format("CondProbEstimation [target_dim=%d,total_dim=%d]", bernoulli_dim, bernoulli_dim));
                pw.println(1); // causal beliefs are separated with each other
                pw.println(String.format("%d\t%d", entry.getKey(), entry.getValue()));
            }
        } catch (IOException e) {
            Messages.error("DAIFacetorGraph: failed to dump EM spec.");
            Messages.fatal(e);
        }

        // 3. run EM algorithm
        swigFactorGraph.runEM(tabFilePath.toAbsolutePath().toString(), emFilePath.toAbsolutePath().toString(), DAIRuntime.g().getNumThreads());
        activated = true;
    }
}
