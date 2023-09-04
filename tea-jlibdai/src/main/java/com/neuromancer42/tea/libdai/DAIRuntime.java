package com.neuromancer42.tea.libdai;

import com.neuromancer42.tea.commons.inference.Categorical01;
import com.neuromancer42.tea.commons.inference.CausalGraph;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.util.IndexMap;
import com.neuromancer42.tea.commons.util.Timer;
import org.apache.commons.lang3.SystemUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class DAIRuntime {
    private static final int clauseLimit = Integer.getInteger("tea.dai.clause.limit", 4);
    private static DAIRuntime runtime = null;

    public static DAIRuntime g() {
        if (runtime == null) {
            Messages.fatal("DAIRuntime: libDAI runtime should be inited first");
        }
        return runtime;
    }

    public static void init(Path baseWorkDirPath, long num_jobs) {
        Timer timer = new Timer("libdai");
        Messages.log("ENTER: LibDAI Runtime Initialization started at " + (new Date()));
        timer.init();
        if (runtime != null) {
            Messages.warn("DAIRuntime: runtime has been built before, are you sure to rebuild it?");
        }

        // 1. new runtime instance, setting paths
        Path tmpWorkDir = null;
        try {
            tmpWorkDir = Files.createDirectories(baseWorkDirPath.resolve("dai"));
        } catch (IOException e) {
            Messages.error("DAIRuntime: failed to create working directory");
            Messages.fatal(e);
        }
        runtime = new DAIRuntime(tmpWorkDir, num_jobs);

        try {
            String libraryFileName = null;
            if (SystemUtils.IS_OS_MAC_OSX) {
                libraryFileName = "libdaifg.jnilib";
            } else if (SystemUtils.IS_OS_LINUX) {
                libraryFileName = "libdaifg.so";
            } else {
                throw new RuntimeException("Not supported yet!");
            }
            // 2. copy jnilib from bundles
            Messages.debug("DAIRuntime: loading libdai runtime file " + libraryFileName);
            InputStream daifgJNIStream = DAIRuntime.class.getResourceAsStream("swig/jnilib/" + libraryFileName);
            Path daifgJNIPath = runtime.workDir.resolve(libraryFileName);
            assert daifgJNIStream != null;
            Files.copy(daifgJNIStream, daifgJNIPath, StandardCopyOption.REPLACE_EXISTING);

            // 3. load library
            System.load(daifgJNIPath.toAbsolutePath().toString());
            Messages.debug("DAIRuntime: libdai runtime has been loaded");
        } catch (IOException | RuntimeException e ) {
            Messages.error("DAIRuntime: failed to initialize libdai runtime.");
            Messages.fatal(e);
        }
        timer.done();
        Messages.log("LEAVE: LibDAI Runtime Initialization finished");
        Timer.printTimer(timer);
    }

    public Path getWorkDir() {
        return workDir;
    }

    private final Path workDir;
    private final long num_jobs;

    public DAIRuntime(Path workDir, long num_jobs) {
        this.workDir = workDir;
        this.num_jobs = num_jobs > 0 ? num_jobs : 1;
    }

    public static int dumpRepeatedFactorGraph(PrintWriter pw, CausalGraph causalGraph, int numRepeats, boolean bayes) throws IOException {
        assert (clauseLimit > 1);
        Messages.debug("DAIRuntime: current clause limit %d", clauseLimit);

        // all non-singleton node needs an latent node to connect to random node
        // and singletons can be random nodes by themselves
        IndexMap<Integer> latentMap = new IndexMap<>();
        for (int nodeId = 0; nodeId < causalGraph.nodeSize(); ++nodeId) {
            if (causalGraph.isStochNode(nodeId) && !causalGraph.isSingleton(nodeId)) {
                latentMap.add(nodeId);
            }
        }

        int numPhony = 0;
        // each phony reduces subnums by (clauseLimit-1)
        for (var sumIter = causalGraph.getSumIter(); sumIter.hasNext();) {
            Map.Entry<Integer, List<Integer>> sub = sumIter.next();
            int headId = sub.getKey();
            int subNum = new HashSet<>(sub.getValue()).size();
            int limit = clauseLimit;
            if (latentMap.contains(headId))
                limit -= 1;
            while (subNum > limit) {
//                Messages.debug("DAIRuntime: replace %d/%d nodes with phony node %d", clauseLimit, subNum, numPhony);
                numPhony++;
                subNum -= clauseLimit - 1;
            }
        }
        for (var prodIter = causalGraph.getProdIter(); prodIter.hasNext(); ) {
            Map.Entry<Integer, List<Integer>> sub = prodIter.next();
            int headId = sub.getKey();
            int subNum = new HashSet<>(sub.getValue()).size();
            int limit = clauseLimit;
            if (latentMap.contains(headId))
                limit -= 1;
            while (subNum > limit) {
//                Messages.debug("DAIRuntime: replace %d/%d nodes with phony node %d", clauseLimit, subNum, numPhony);
                numPhony++;
                subNum -= clauseLimit - 1;
            }
        }

        final int subSize = (bayes ? latentMap.size() : 0) + causalGraph.nodeSize() + numPhony;
        int numFactors = causalGraph.distSize() + subSize * (numRepeats + 1);
        pw.println(numFactors);
        pw.flush();
        // each distnode has a factor block
        for (int distId = 0; distId < causalGraph.getAllDistNodes().size(); distId++) {
            Categorical01 distNode = causalGraph.getAllDistNodes().get(distId);
//            fw.println("# DistNode " + i + " " + distNode.toString())
            pw.println();
            if (bayes) {
                double[] probs = distNode.getProbabilitis();
                dumpCategoricalFactor(pw, distId, probs);
            } else {
                // if using EM, only a (0,1)-factor is needed
//                double e = distNode.estimation();
//                if (e < Categorical01.epsilon)
//                    e = Categorical01.epsilon;
//                if (e > 1 - Categorical01.epsilon)
//                    e = 1 - Categorical01.epsilon;
                double e = distNode.estimation();
                dumpCategoricalFactor(pw, distId, new double[]{1-e, e});
            }
        }

        int offset = causalGraph.distSize();

        offset = dumpSubFactorGraph(pw, causalGraph, latentMap, offset, bayes);

        for (int r = 0; r < numRepeats; r++) {
            offset = dumpSubFactorGraph(pw, causalGraph, latentMap, offset, bayes);
        }
        assert offset == numFactors;

        pw.flush();
        pw.close();
        Messages.debug("DAIRuntime: FactorGraph consisting of "+ causalGraph.distSize() + " dist nodes, (1+" + numRepeats + ")x(" + (bayes ? (latentMap.size() + " latent nodes, ") : "") + causalGraph.nodeSize() + " nodes and " + numPhony + " phony nodes)." );
        return subSize;
    }

    private static int dumpSubFactorGraph(PrintWriter pw, CausalGraph causalGraph, IndexMap<Integer> latentMap, int offset, boolean bayes) {
        int offsetNodes = offset;
        // if using bayesian learning, a latent variable is needed to bridge between parameters and clauses
        // if using em-learning or inference-only, clauses are directly connected to parameters;
        int offsetLatent = bayes ? (offsetNodes + causalGraph.nodeSize()) : 0;
        if (bayes) {
            for (int i = 0; i < latentMap.size(); i++) {
                int latentId = i + offsetLatent;
                Integer nodeId = latentMap.get(i);
                Integer distId = causalGraph.getNodesDistId(nodeId);
                assert (distId != null);
                Categorical01 dist = causalGraph.getAllDistNodes().get(distId);
                pw.println();
                dumpBernoulliFactor(pw, latentId, distId, dist.getSupports());
            }
        }

        int offsetPhony = offsetNodes + causalGraph.nodeSize() + (bayes ? latentMap.size() : 0);
        int phonyId = offsetPhony;
        for (var sumIter = causalGraph.getSumIter(); sumIter.hasNext();) {
            var sumEntry = sumIter.next();
            Integer headId = sumEntry.getKey();
            int sumHead = offsetNodes + headId;
            Set<Integer> subIds = new LinkedHashSet<>(sumEntry.getValue());
            List<Integer> sumBody = subIds.stream().map(subId -> offsetNodes + subId).toList();
            int limit = clauseLimit;
            if (latentMap.contains(headId))
                limit -= 1;
            while (sumBody.size() > limit) {
                List<Integer> phonyHeads = new ArrayList<>();
                for (int phonyBodyStart = 0; phonyBodyStart < sumBody.size(); phonyBodyStart += clauseLimit) {
                    int phonyBodyEnd = phonyBodyStart + clauseLimit;
                    if (phonyBodyEnd > sumBody.size()) {
                        phonyHeads.addAll(sumBody.subList(phonyBodyStart, sumBody.size()));
                    } else {
                        Integer phonyHead = phonyId++;
                        List<Integer> phonyBody = sumBody.subList(phonyBodyStart, phonyBodyEnd);
                        Messages.debug("CausalGraph: Create sum phony node " + phonyHead);
                        pw.println();
                        dumpSumFactor(pw, phonyHead, phonyBody);
                        phonyHeads.add(phonyHead);
                    }
                }
                sumBody = phonyHeads;
            }
            int latentId = latentMap.indexOf(headId);
            if (latentId < 0) {
                pw.println();
                dumpSumFactor(pw, sumHead, sumBody);
            } else {
                pw.println();
                dumpSumFactor(pw, sumHead, sumBody, bayes ? (offsetLatent + latentId) : causalGraph.getNodesDistId(headId));
            }
        }
        for (var prodIter = causalGraph.getProdIter(); prodIter.hasNext();) {
            var prodEntry = prodIter.next();
            Integer headId = prodEntry.getKey();
            int prodHead = offsetNodes + headId;
            Set<Integer> subIds = new LinkedHashSet<>(prodEntry.getValue());
            List<Integer> prodBody = subIds.stream().map(subId -> offsetNodes + subId).toList();
            int limit = clauseLimit;
            if (latentMap.contains(headId))
                limit -= 1;
            while (prodBody.size() > limit) {
                List<Integer> phonyHeads = new ArrayList<>();
                for (int phonyBodyStart = 0; phonyBodyStart < prodBody.size(); phonyBodyStart += clauseLimit) {
                    int phonyBodyEnd = phonyBodyStart + clauseLimit;
                    if (phonyBodyEnd > prodBody.size()) {
                        phonyHeads.addAll(prodBody.subList(phonyBodyStart, prodBody.size()));
                    } else {
                        Integer phonyHead = phonyId++;
                        List<Integer> phonyBody = prodBody.subList(phonyBodyStart, phonyBodyEnd);
                        Messages.debug("CausalGraph: Create prod phony node " + phonyHead);
                        pw.println();
                        dumpProdFactor(pw, phonyHead, phonyBody);
                        phonyHeads.add(phonyHead);
                    }
                }
                prodBody = phonyHeads;
            }
            int latentId = latentMap.indexOf(headId);
            if (latentId < 0) {
                pw.println();
                dumpProdFactor(pw, prodHead, prodBody);
            } else {
                pw.println();
                dumpProdFactor(pw, prodHead, prodBody, bayes ? (offsetLatent + latentId) : causalGraph.getNodesDistId(headId));
            }
        }
        // singleton nodes are directly linked to distNodes
        for (var singletonIter = causalGraph.getSingletonIter(); singletonIter.hasNext();) {
            Integer nodeId = singletonIter.next();
            int singletonId = offsetNodes + nodeId;
            Integer distId = causalGraph.getNodesDistId(nodeId);
            if (distId != null) {
                pw.println();
                if (bayes) {
                    Categorical01 dist = causalGraph.getAllDistNodes().get(distId);
                    dumpBernoulliFactor(pw, singletonId, distId, dist.getSupports());
                } else {
                    // if no bayesian is needed, singleton node is directly connected to parameter
                    dumpBernoulliFactor(pw, singletonId, distId, new double[]{0.0, 1.0});
                }
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
        pw.println(Long.toUnsignedString(rep) + " " + 1);
    }

    private static void dumpClauseWithControl(PrintWriter pw, long condRep, int result) {
        long blockRep = condRep * 2 * 2;
        long passRep = blockRep + 2 + result;
        pw.println(Long.toUnsignedString(blockRep) + " " + 1);
        pw.println(Long.toUnsignedString(passRep) + " " + 1);
    }

    public long getNumThreads() {
        return num_jobs;
    }
}
