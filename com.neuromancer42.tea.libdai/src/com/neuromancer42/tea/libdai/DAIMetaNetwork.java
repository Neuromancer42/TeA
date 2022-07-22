package com.neuromancer42.tea.libdai;

import com.neuromancer42.tea.core.inference.Categorical01;
import com.neuromancer42.tea.core.inference.CausalGraph;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.util.IndexMap;
import com.neuromancer42.tea.libdai.swig.DoubleVector;
import com.neuromancer42.tea.libdai.swig.LibDAISWIGFactorGraph;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class DAIMetaNetwork {
    // A MetaNetwork associate a causal-graph to a concrete (LibDAI) factor graph
    // It binds the causal-graph nodes to factor graph variables
    // Parameter: distId --> distId
    // ObserveNode: <nodeId, time> --> distSize + (time + 1) * shift + nodeId (time starts from 0)
    // PredictNode: nodeId -->  distSize + nodeId

    // TODO: clauseLimit should be moved to some other config places
    private static final int clauseLimit = Integer.getInteger("chord.dai.clause.limit", 15);

    public static DAIMetaNetwork createDAIMetaNetwork(Path workdir, String name, CausalGraph<String> causalGraph, int numRepeats) {
        Path fgFilePath = workdir.resolve(name+".fg");
        int subSize = causalGraph.nodeSize();
        try {
            BufferedWriter bw = Files.newBufferedWriter(fgFilePath, StandardCharsets.UTF_8);
            PrintWriter pw = new PrintWriter(bw);
            subSize = dumpRepeatedFactorGraph(pw, causalGraph, numRepeats);
        } catch (IOException e) {
            Messages.error("DAIFacetorGraph: failed to dump factor graph.");
            Messages.fatal(e);
        }
        return new DAIMetaNetwork(fgFilePath, causalGraph, numRepeats, subSize);
    }

    private final CausalGraph<String> causalGraph;
    private final LibDAISWIGFactorGraph swigFactorGraph;
    private final int offset;
    private final int shift;
    private final int numRepeats;
    private boolean activated;

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
        if (time >= numRepeats) {
            Messages.fatal("DAIMetaNetwork: observation time " + time + " exceeds meta-network size " + numRepeats + ".");
        }
        int fgVarId = offset + (1 + time) * shift + nodeId;
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

    public static int dumpRepeatedFactorGraph(PrintWriter pw, CausalGraph<String> causalGraph, int numRepeats) throws IOException {
        assert (clauseLimit > 1);
        int numPhony = 0;
        // each phony reduces subnums by (clauseLimit-1)
        for (var sumIter = causalGraph.getSumIter(); sumIter.hasNext();) {
            int subNum = sumIter.next().getValue().size();
            while (subNum > clauseLimit) {
                numPhony++;
                subNum -= clauseLimit - 1;
            }
        }
        for (var prodIter = causalGraph.getProdIter(); prodIter.hasNext(); ) {
            int subNum = prodIter.next().getValue().size();
            while (subNum > clauseLimit) {
                numPhony++;
                subNum -= clauseLimit - 1;
            }
        }

        // all non-singleton node needs an latent node to connect to random node
        // and singletons can be random nodes by themselves
        IndexMap<Integer> latentMap = new IndexMap<>();
        for (int nodeId = 0; nodeId < causalGraph.nodeSize(); ++nodeId) {
            if (causalGraph.isStochNode(nodeId) && !causalGraph.isSingleton(nodeId)) {
                latentMap.add(nodeId);
            }
        }

        final int subSize = latentMap.size() + causalGraph.nodeSize() + numPhony;
        int numFactors = causalGraph.distSize() + subSize * (numRepeats + 1);
        pw.println(numFactors);
        pw.flush();
        // each distnode has a factor block
        for (int distId = 0; distId < causalGraph.getAllDistNodes().size(); distId++) {
            Categorical01 distNode = causalGraph.getAllDistNodes().get(distId);
            double[] probs = distNode.getProbabilitis();
//            fw.println("# DistNode " + i + " " + distNode.toString())
            pw.println();
            dumpCategoricalFactor(pw, distId, probs);
        }

        int offset = causalGraph.distSize();

        offset = dumpSubFactorGraph(pw, causalGraph, latentMap, offset);

        for (int r = 0; r < numRepeats; r++) {
            offset = dumpSubFactorGraph(pw, causalGraph, latentMap, offset);
        }
        assert offset == numFactors;

        pw.flush();
        pw.close();
        Messages.log("FactorGraph consisting of "+ causalGraph.distSize() + " dist nodes, (1+" + numRepeats + ")x(" + latentMap.size() + " latent nodes, " + causalGraph.nodeSize() + " nodes and " + numPhony + " phony nodes)." );
        return subSize;
    }

    private static int dumpSubFactorGraph(PrintWriter pw, CausalGraph<String> causalGraph, IndexMap<Integer> latentMap, int offset) {
        int offsetNodes = offset;
        int offsetLatent = offsetNodes + causalGraph.nodeSize();
        int offsetPhony = offsetLatent + latentMap.size();
        for (int i = 0; i < latentMap.size(); i++) {
            int latentId = i + offsetLatent;
            Integer nodeId = latentMap.get(i);
            Integer distId = causalGraph.getNodesDistId(nodeId);
            assert (distId != null);
            Categorical01 dist = causalGraph.getAllDistNodes().get(distId);
            pw.println();
            dumpBernoulliFactor(pw, latentId, distId, dist.getSupports());
        }
        int phonyId = offsetPhony;
        for (var sumIter = causalGraph.getSumIter(); sumIter.hasNext();) {
            var sumEntry = sumIter.next();
            Integer headId = sumEntry.getKey();
            int sumHead = offsetNodes + headId;
            List<Integer> sumBody = new ArrayList<>();
            for (Integer subId : sumEntry.getValue()) {
                sumBody.add(offsetNodes + subId);
            }
            int limit = clauseLimit;
            if (latentMap.contains(headId))
                limit -= 1;
            while (sumBody.size() > limit) {
                Integer phonyHead = phonyId++;
                List<Integer> phonyBody = sumBody.subList(sumBody.size() - clauseLimit, sumBody.size());
                Messages.log("CausalGraph: Create sum phony node " + phonyHead);
                pw.println();
                dumpSumFactor(pw, phonyHead, phonyBody);
                sumBody = sumBody.subList(0, sumBody.size() - clauseLimit);
                sumBody.add(phonyHead);
            }
            int latentId = latentMap.indexOf(headId);
            if (latentId < 0) {
                pw.println();
                dumpSumFactor(pw, sumHead, sumBody);
            } else {
                pw.println();
                dumpSumFactor(pw, sumHead, sumBody, offsetLatent + latentId);
            }
        }
        for (var prodIter = causalGraph.getProdIter(); prodIter.hasNext();) {
            var prodEntry = prodIter.next();
            Integer headId = prodEntry.getKey();
            int prodHead = offsetNodes + headId;
            List<Integer> prodBody = new ArrayList<>();
            for (Integer subId : prodEntry.getValue()) {
                prodBody.add(offsetNodes + subId);
            }
            int limit = clauseLimit;
            if (latentMap.contains(headId))
                limit -= 1;
            while (prodBody.size() > limit) {
                Integer phonyHead = phonyId++;
                List<Integer> phonyBody = prodBody.subList(prodBody.size() - clauseLimit, prodBody.size());
                Messages.log("CausalGraph: Create product phony node " + phonyHead);
                pw.println();
                dumpProdFactor(pw, phonyHead, phonyBody);
                prodBody = prodBody.subList(0, prodBody.size() - clauseLimit);
                prodBody.add(phonyHead);
            }
            int latentId = latentMap.indexOf(headId);
            if (latentId < 0) {
                pw.println();
                dumpProdFactor(pw, prodHead, prodBody);
            } else {
                pw.println();
                dumpProdFactor(pw, prodHead, prodBody, offsetLatent + latentId);
            }
        }
        // singleton nodes are directly linked to distNodes
        for (var singletonIter = causalGraph.getSingletonIter(); singletonIter.hasNext();) {
            Integer nodeId = singletonIter.next();
            int singletonId = offsetNodes + nodeId;
            Integer distId = causalGraph.getNodesDistId(nodeId);
            if (distId != null) {
                Categorical01 dist = causalGraph.getAllDistNodes().get(distId);
                pw.println();
                dumpBernoulliFactor(pw, singletonId, distId, dist.getSupports());
            } else {
                pw.println();
                dumpConstantFactor(pw, singletonId, 2, 1);
            }
        }

        return phonyId;
    }

    private static void dumpConstantFactor(PrintWriter pw, int singletonId, long dim, long value) {
        pw.println(1);
        pw.println(singletonId);
        pw.println(dim);
        pw.println(1);
        pw.println(value + " " + 1);
        pw.flush();
    }

    private static void dumpCategoricalFactor(PrintWriter pw, int distId, double[] weights) {
        pw.println(1);// variable numbers
        pw.println(distId); // variable IDs
        int cardinality = weights.length;
        pw.println(cardinality); // cardinalities
        pw.println(cardinality); // number of non-zero entries
        for (int i = 0; i < cardinality; i++) {
            pw.print(i);
            pw.print(" ");
            pw.println(weights[i]);
        }
        pw.flush();
    }

    private static void dumpBernoulliFactor(PrintWriter pw, int latentId, Integer distId, double[] params) {
        pw.println(2);
        pw.println(latentId + " " + distId);
        pw.println(2 + " " + params.length);
        List<String> entries = new ArrayList<>();
        for (int j = 0; j < params.length; j++) {
            long falseRep = (long) j * 2;
            long trueRep = (long) j * 2 + 1;
            double trueProb = params[j];
            double falseProb = 1 - trueProb;
            if (falseProb > 0)
                entries.add(falseRep + " " + falseProb);
            if (trueProb > 0)
                entries.add(trueRep + " " + trueProb);
        }
        pw.println(entries.size());
        for (String e : entries)
            pw.println(e);
        pw.flush();
    }

    private static void dumpSumFactor(PrintWriter pw, Integer head, List<Integer> body) {
        dumpClauseVariables(pw, head, body);
        long allRep = (1L << body.size()) - 1;
        pw.println(allRep + 1);
        long noneRep = 0;
        dumpClauseWithoutControl(pw, noneRep, 0);
        for (long subRep = 1; subRep <= allRep; subRep++) {
            dumpClauseWithoutControl(pw, subRep, 1);
        }
        pw.flush();
    }

    private static void dumpSumFactor(PrintWriter pw, Integer head, List<Integer> body, Integer control) {
        if (control == null) dumpSumFactor(pw, head, body);
        dumpClauseVariables(pw, head, body, control);
        long allRep = (1L << body.size()) - 1;
        pw.println((allRep + 1) * 2);
        long noneRep = 0;
        dumpClauseWithControl(pw, noneRep, 0);
        for (long subRep = 1; subRep <= allRep; subRep++) {
            dumpClauseWithControl(pw, subRep, 1);
        }
        pw.flush();
    }

    private static void dumpProdFactor(PrintWriter pw, Integer head, List<Integer> body) {
        dumpClauseVariables(pw, head, body);
        long allRep = (1L << body.size()) - 1;
        pw.println(allRep + 1);
        long noneRep = 0;
        for (long subRep = noneRep; subRep < allRep; subRep++) {
            dumpClauseWithoutControl(pw, subRep, 0);
        }
        dumpClauseWithoutControl(pw, allRep, 1);
        pw.flush();
    }

    private static void dumpProdFactor(PrintWriter pw, Integer head, List<Integer> body, Integer control) {
        if (control == null) dumpProdFactor(pw, head, body);
        dumpClauseVariables(pw, head, body, control);
        long allRep = (1L << body.size()) - 1;
        pw.println((allRep + 1) * 2);
        long noneRep = 0;
        for (long subRep = noneRep; subRep < allRep; subRep++) {
            dumpClauseWithControl(pw, subRep, 0);
        }
        dumpClauseWithControl(pw, allRep, 1);
        pw.flush();
    }

    private static void dumpClauseVariables(PrintWriter pw, Integer head, List<Integer> body) {
        pw.println(body.size() + 1);
        pw.print(head);
        for (int sub : body) {
            pw.print(" ");
            pw.print(sub);
        }
        pw.println();
        pw.print(2);
        for (int j = 0; j < body.size(); j++) {
            pw.print(" ");
            pw.print(2);
        }
        pw.println();
    }

    private static void dumpClauseVariables(PrintWriter pw, Integer head, List<Integer> body, Integer control) {
        pw.println(body.size() + 2);
        pw.print(head);
        pw.print(" ");
        pw.print(control);
        for (int sub : body) {
            pw.print(" ");
            pw.print(sub);
        }
        pw.println();
        pw.print(2);
        pw.print(" ");
        pw.print(2);
        for (int j = 0; j < body.size(); j++) {
            pw.print(" ");
            pw.print(2);
        }
        pw.println();
    }

    private static void dumpClauseWithoutControl(PrintWriter pw, long condRep, int result) {
        long rep = condRep * 2 + result;
        pw.println(rep + " " + 1);
    }

    private static void dumpClauseWithControl(PrintWriter pw, long condRep, int result) {
        long blockRep = condRep * 2 * 2;
        long passRep = blockRep + 2 + result;
        pw.println(blockRep + " " + 1);
        pw.println(passRep + " " + 1);
    }
}
