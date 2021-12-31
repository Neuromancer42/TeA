package inference;

import chord.Main;
import chord.project.Config;
import chord.project.Messages;
import chord.project.ProcessExecutor;
import chord.util.IndexMap;
import chord.util.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Function;

// Probabilistic Logic Network structure
// (i.e. probabilistic term logic)
// TODO: accept DNF format only
public class PLN<NodeT> {

    // unsigned int has 32 bits
    // 16 bits for probability representation
    // 1 bits for head
    private static final int clauseLimit = Integer.getInteger("chord.pln.clause.limit", 15);
    // count number of phony nodes
    private int numPhony;

    // each node is an object
    private final IndexMap<NodeT> nodes;
    // each node either correspond to a sum or a product, or is a singleton
    private final Map<Integer, Set<Integer>> sums;
    private final Map<Integer, Set<Integer>> prods;
    // some nodes corresponds to a distribution, or is determinant
    private final Map<Integer, Integer> priors;
    private final IndexMap<Categorical01> distNodes;
    private String workdir;

    public PLN(
            Collection<NodeT> nodes,
            Map<NodeT, Set<NodeT>> sums,
            Map<NodeT, Set<NodeT>> prods,
            Map<NodeT, Categorical01> priors
    ) {
        workdir = System.getProperty("chord.pln.work.dir", Config.v().outDirName+File.separator+"pln");
        Utils.mkdirs(workdir);
        this.nodes = new IndexMap<>(nodes.size());
        this.nodes.addAll(nodes);
        this.sums = new HashMap<>(sums.size());
        for (NodeT head : sums.keySet()) {
            int headIdx = this.nodes.indexOf(head);
            if (headIdx < 0) {
                Messages.error("PLN: skip unmet head " + head);
                continue;
            }
            Set<NodeT> subs = sums.get(head);
            this.sums.put(headIdx, new HashSet<>(subs.size()));
            for (NodeT sub : subs) {
                int subIdx = this.nodes.indexOf(sub);
                if (subIdx < 0) {
                    Messages.error("PLN: skip unmet sub " + sub);
                    continue;
                }
                this.sums.get(headIdx).add(subIdx);
            }
        }
        this.prods = new HashMap<>(prods.size());
        for (NodeT head : prods.keySet()) {
            int headIdx = this.nodes.indexOf(head);
            if (headIdx < 0) {
                Messages.error("PLN: skip unmet head " + head);
                continue;
            }
            Set<NodeT> subs = prods.get(head);
            this.prods.put(headIdx, new HashSet<>(subs.size()));
            for (NodeT sub : subs) {
                int subIdx = this.nodes.indexOf(sub);
                if (subIdx < 0) {
                    Messages.error("PLN: skip unmet sub " + sub + " in head " + head);
                }
                this.prods.get(headIdx).add(subIdx);
            }
        }

        this.distNodes = new IndexMap<>();
        this.distNodes.addAll(priors.values());

        this.priors = new HashMap<>(priors.size());
        for (NodeT node : priors.keySet()) {
            int nodeIdx = this.nodes.indexOf(node);
            int distIdx = this.distNodes.indexOf(priors.get(node));
            this.priors.put(nodeIdx, distIdx);
        }

        numPhony = 0;
        // each phony reduces subnums by (clauseLimit-1)
        for (Integer headId : this.sums.keySet()) {
            int subNum = this.sums.get(headId).size();
            while (subNum > clauseLimit) {
                numPhony++;
                subNum -= clauseLimit - 1;
            }
        }
        for (Integer headId : this.prods.keySet()) {
            int subNum = this.prods.get(headId).size();
            while (subNum > clauseLimit) {
                numPhony++;
                subNum -= clauseLimit - 1;
            }
        }
    }

    public void dump() {
        String netFileName = workdir + File.separator + "pln.txt";
        PrintWriter bw = Utils.openOut(netFileName);
        for (int i = 0; i < nodes.size(); i++) {
            bw.print(i + "\t");
            Integer distId = priors.get(i);
            bw.print(distId + "\t");
            Set<Integer> sumNodes = sums.get(i);
            int sumNum = sumNodes == null ? 0 : sumNodes.size();
            Set<Integer> prodNodes = prods.get(i);
            int prodNum = prodNodes == null ? 0 : prodNodes.size();
            if (sumNum > 0 && prodNum > 0)
                Messages.fatal("PLN: sums and products are not disjoint");
            if (sumNum > 0) {
                bw.print(sumNum);
                bw.print("\t+");
                for (Integer sumId : sumNodes) {
                    bw.print("\t" + sumId);
                }
                bw.println();
            } else if (prodNum > 0) {
                bw.print(prodNum);
                bw.print("\t*");
                for (Integer prodId : prodNodes) {
                    bw.print("\t" + prodId);
                }
                bw.println();
            } else {
                bw.println(0);
            }
        }
        bw.flush();
        bw.close();

        String distFileName = workdir + File.separator + "priors.list";
        PrintWriter dw = Utils.openOut(distFileName);
        for (int i = 0; i < distNodes.size(); i++) {
            dw.print(i);
            Categorical01 dist = distNodes.get(i);
            for (Double v : dist.getSupports()) {
                dw.print(" ");
                dw.print(v);
                dw.print(":");
                dw.print(dist.probability(v));
            }
            dw.println();
        }
        dw.flush();
        dw.close();
    }

    private void dumpDotSubgraph(PrintWriter dw, Function<NodeT, String> nodeRepr, Integer trace) {
        String suffix = trace==null ? "" : ("_"+trace);
        dw.println("\tsubgraph cluster"+suffix+" {");
        dw.println("\t\tlabel=\"trace"+suffix+"\";");
        for (int i = 0; i < nodes.size(); i++) {
            dw.print("\t\tn" + i + suffix);
            dw.print(" [");
            NodeT node = nodes.get(i);

            String label = nodeRepr.apply(node);
            dw.print("label=\""+label+"\"");

            String shape = "";
            String style = "";
            Set<Integer> sumNodes = sums.get(i);
            int sumNum = sumNodes == null ? 0 : sumNodes.size();
            Set<Integer> prodNodes = prods.get(i);
            int prodNum = prodNodes == null ? 0 : prodNodes.size();
            if (sumNum > 0 && prodNum > 0)
                Messages.fatal("PLN: sums and products are not disjoint");
            if (sumNum > 0) {
                shape = "ellipse";
            } else if (prodNum > 0) {
                shape = "box";
            } else {
                shape = "ellipse";
                style = "filled";
            }
            dw.print(",shape="+shape);
            if (!style.equals(""))
                dw.print(",style="+style);
            dw.println("];");
        }
        for (Integer headId : sums.keySet())
            for (Integer subId : sums.get(headId)) {
                dw.print("\t");
                dw.print("n"+subId+suffix);
                dw.print(" -> ");
                dw.print("n"+headId+suffix);
                dw.println(" [style=dotted];");
            }
        for (Integer headId  : prods.keySet())
            for (Integer subId : prods.get(headId)) {
                dw.print("\t");
                dw.print("n"+subId+suffix);
                dw.print(" -> ");
                dw.print("n"+headId+suffix);
                dw.println(";");
            }
        for (Integer nodeId : priors.keySet()) {
            int distId = priors.get(nodeId);
            dw.print("\t");
            dw.print("p"+distId);
            dw.print(" -> ");
            dw.print("n"+nodeId+suffix);
            dw.println(" [style=bold];");
        }
        dw.println("\t}");
    }

    public void dumpDot(Function<NodeT, String> nodeRepr, Function<Categorical01, String> distRepr) {
        dumpDot(nodeRepr, distRepr, 0);
    }

    public void dumpDot(Function<NodeT, String> nodeRepr, Function<Categorical01, String> distRepr, int numRepeats) {
        String dotFileName = workdir + File.separator + "pln.dot";
        PrintWriter dw = Utils.openOut(dotFileName);
        dw.println("digraph G{");

        dw.println("subgraph cluster_prior {");
        dw.println("label=params;");
        for (int i = 0; i < distNodes.size(); i++) {
            dw.print("\tp"+i);
            dw.print(" [");
            Categorical01 distNode = distNodes.get(i);
            String label = distRepr.apply(distNode);
            dw.print("label=\""+label+"\"");
            dw.print(",shape=box,style=filled");
            dw.println("];");
        }
        dw.println("}");

        dumpDotSubgraph(dw, nodeRepr, null);

        for (int i = 0; i < numRepeats; i++) {
            dumpDotSubgraph(dw, nodeRepr, i);
        }

        dw.println("}");
        dw.flush();
        dw.close();
    }

    public void dumpFactorGraph() {
        dumpRepeatedFactorGraph(0);
    }

    // TODO: separate conditionally on probabilistic / determinant nodes
    public void dumpRepeatedFactorGraph(int numRepeats) {
        assert (clauseLimit > 1);
        String fgFileName = workdir + File.separator + "pln.fg";
        PrintWriter fw = Utils.openOut(fgFileName);

        int numFacts = distNodes.size() + (nodes.size() + numPhony) * (numRepeats + 1);
        fw.println(numFacts);
        fw.flush();
        // each nodes and each distnode has a factor block
        // additionally record list of distnodes
        String paramFileName = workdir + File.separator + "params.list";
        PrintWriter pw = Utils.openOut(paramFileName);
        for (int i = 0; i < distNodes.size(); i++) {
            pw.println(i);
        }
        pw.flush();
        pw.close();
        for (int i = 0; i < distNodes.size(); i++) {
            fw.println();
            Categorical01 distNode = distNodes.get(i);
//            fw.println("# DistNode " + i + " " + distNode.toString());
            fw.println(1); // variable numbers
            fw.println(i); // variable IDs
            double[] supports = distNode.getSupports();
            int cardinality = supports.length;
            fw.println(cardinality); // cardinalities
            fw.println(cardinality); // number of non-zero entries
            for (int j = 0; j < cardinality; j++) {
                fw.print(j);
                fw.print(" ");
                fw.println(distNode.probability(supports[j]));
            }
            fw.flush();
        }

        int offset = distNodes.size();

        offset = dumpSubFactorGraph(clauseLimit, fw, offset);

        for (int r = 0; r < numRepeats; r++) {
            offset = dumpSubFactorGraph(clauseLimit,fw, offset);
        }
        assert offset == numFacts;

        fw.flush();
        fw.close();
        Messages.log("FactorGraph consisting of "+ distNodes.size() + " dist nodes, (1+" + numRepeats + ")x(" + nodes.size() + " nodes and " + numPhony + " phony nodes)." );
    }

    private int dumpSubFactorGraph(int clauseLimit, PrintWriter fw, int offset) {
        int phonyId = offset + nodes.size();
        for (int i = 0; i < nodes.size(); i++) {
            double[] deterministic = {1.0};
            Integer distId = priors.get(i);
            double[] probs = distId == null ? deterministic : distNodes.get(distId).getSupports();

            Set<Integer> sum = sums.get(i);
            int sumNum = sum == null ? 0 : sum.size();
            Set<Integer> prod = prods.get(i);
            int prodNum = prod == null ? 0 : prod.size();
            if (sumNum > 0 && prodNum > 0)
                Messages.fatal("PLN: sums and products are not disjoint");

            List<Integer> vars = new ArrayList<>();
            List<Integer> cards = new ArrayList<>();
            List<String> entries = new ArrayList<>();

            vars.add(offset + i);
            cards.add(2);

            int probCnt = probs.length;
            if (probCnt > 1) {
                vars.add(distId);
                cards.add(probCnt);
            }
            // generate indexes, note: use long in representation to avoid int overflow
            if (sumNum > 0) {
//                fw.print("# Sum Node " + i + ": " + node.toString());
//                fw.println(" with prior " + distNodes.indexOf(priorDist));
                List<Integer> sumList = new ArrayList<>(sum.size());
                for (Integer subId : sum) {
                    sumList.add(offset + subId);
                }
                while (sumNum > clauseLimit) {
                    //assert phonyId < numFacts;
                    int phonyHead = phonyId++;
                    Messages.log("PLN: Create sum phony node " + phonyHead);
                    fw.println();
                    fw.println(clauseLimit + 1);
                    fw.print(phonyHead);
                    for (int phonySub : sumList.subList(sumNum - clauseLimit, sumNum)) {
                        fw.print(" ");
                        fw.print(phonySub);
                    }
                    fw.println();
                    fw.print(2);
                    for (int j = 0; j < clauseLimit; j++) {
                        fw.print(" ");
                        fw.print(2);
                    }
                    fw.println();
                    long allRep = (1L << clauseLimit) - 1;
                    fw.println(allRep + 1);
                    long noneEntry = 0;
                    fw.println(noneEntry + " " + 1);
                    for (long subRep = 1; subRep <= allRep; subRep++) {
                        long entry = subRep * 2 + 1;
                        fw.println(entry + " " + 1);
                    }
                    fw.flush();
                    sumList = new ArrayList<>(sumList.subList(0, sumNum - clauseLimit));
                    sumList.add(phonyHead);
                    sumNum -= clauseLimit - 1;
                }
                for (Integer subId : sumList) {
                    vars.add(subId);
                    cards.add(2);
                }
                for (int probRep = 0; probRep < probCnt; probRep++) {
                    int noneRep = probRep * 2;
                    String entry = noneRep + " " + 1;
                    entries.add(entry);
                }
                long allSubRep = (1L << sumNum) - 1; // 2^(subNum)-1
                for (long subRep = 1; subRep <= allSubRep; subRep++) {
                    for (int probRep = 0; probRep < probCnt; probRep++) {
                        double trueProb = probs[probRep];
                        double falseProb = 1.0D - trueProb;
                        long falseRep = (subRep * probCnt + probRep) * 2;
                        long trueRep = falseRep + 1;
                        if (falseProb > 0) {
                            String falseEntry = falseRep + " " + falseProb;
                            entries.add(falseEntry);
                        }
                        if (trueProb > 0) {
                            String trueEntry = trueRep + " " + trueProb;
                            entries.add(trueEntry);
                        }
                    }
                }
            } else if (prodNum > 0) {
//                fw.print("# Product Node " + i + ": " + node.toString());
//                fw.println(" with prior " + distNodes.indexOf(priorDist));
                List<Integer> prodList = new ArrayList<>(prod.size());
                for (Integer subId : prod) {
                    prodList.add(offset + subId);
                }
                while (prodNum > clauseLimit) {
                    int phonyHead = phonyId++;
                    Messages.log("PLN: Create product phony node " + phonyHead);
                    fw.println();
                    fw.println(clauseLimit + 1);
                    fw.print(phonyHead);
                    for (int phonySub : prodList.subList(prodNum - clauseLimit, prodNum)) {
                        fw.print(" ");
                        fw.print(phonySub);
                    }
                    fw.println();
                    fw.print(2);
                    for (int j = 0; j < clauseLimit; j++) {
                        fw.print(" ");
                        fw.print(2);
                    }
                    fw.println();
                    long allRep = (1L << clauseLimit) - 1;
                    fw.println(allRep + 1);
                    for (long subRep = 0; subRep < allRep; subRep++) {
                        long entry = subRep * 2;
                        fw.println(entry + " " + 1);
                    }
                    long allEntry = allRep * 2 + 1;
                    fw.println(allEntry + " " + 1);
                    fw.flush();
                    prodList = new ArrayList<>(prodList.subList(0, prodNum - clauseLimit));
                    prodList.add(phonyHead);
                    prodNum -= clauseLimit - 1;
                }
                for (Integer subId : prodList) {
                    vars.add(subId);
                    cards.add(2);
                }
                long allSubRep = (1L << prodNum) - 1; // 2^(subNum)-1
                for (long subRep = 0; subRep < allSubRep; subRep++) {
                    for (long probRep = 0; probRep < probCnt; probRep++) {
                        long falseRep = (subRep * probCnt + probRep) * 2;
                        String falseEntry = falseRep + " " + 1;
                        entries.add(falseEntry);
                    }
                }
                for (int probRep = 0; probRep < probCnt; probRep++) {
                    double trueProb = probs[probRep];
                    double falseProb = 1.0D - trueProb;
                    long falseRep = (allSubRep * probCnt + probRep) * 2;
                    long trueRep = falseRep + 1;
                    if (falseProb > 0) {
                        String falseEntry = falseRep + " " + falseProb;
                        entries.add(falseEntry);
                    }
                    if (trueProb > 0) {
                        String trueEntry = trueRep + " " + trueProb;
                        entries.add(trueEntry);
                    }
                }
            } else {
//                fw.print("# Input Node " + i + ": " + node.toString());
//                fw.println(" with prior " + distNodes.indexOf(priorDist));
                for (int probRep = 0; probRep < probCnt; probRep++) {
                    double trueProb = probs[probRep];
                    double falseProb = 1.0D - trueProb;
                    long falseRep = ((long) probRep) * 2;
                    long trueRep = falseRep + 1;
                    if (falseProb > 0) {
                        String falseEntry = falseRep + " " + falseProb;
                        entries.add(falseEntry);
                    }
                    if (trueProb > 0) {
                        String trueEntry = trueRep + " " + trueProb;
                        entries.add(trueEntry);
                    }
                }
            }
            assert (vars.size() == cards.size());
            fw.println();
            fw.println(vars.size());
            for (Integer varId : vars) {
                fw.print(varId);
                fw.print(" ");
            }
            fw.println();
            for (Integer card : cards) {
                fw.print(card);
                fw.print(" ");
            }
            fw.println();
            fw.println(entries.size());
            for (String entry : entries) {
                fw.println(entry);
            }
            fw.flush();
        }
        return phonyId;
    }

    public void appendObs(PrintWriter ow, Map<NodeT, Boolean> obs, int idx) {
        for (NodeT obsNode : obs.keySet()) {
            int offset = distNodes.size() + (idx + 1) * (nodes.size() + numPhony);
            int id = offset + nodes.indexOf(obsNode);
            if (id < 0) continue;
            ow.print(id);
            ow.print(" ");
            ow.print(obs.get(obsNode) ? 1 : 0);
            ow.println();
        }
        ow.flush();
    }

    public void dumpObses(List<Map<NodeT, Boolean>> obses) {
        String obsFileName = workdir + File.separator + "obs.list";
        PrintWriter ow = Utils.openOut(obsFileName);
        for (int idx = 0; idx < obses.size(); idx++)
            appendObs(ow, obses.get(idx), idx);
        ow.flush();
        ow.close();
    }

    public void dumpObs(Map<NodeT, Boolean> obs) {
        String obsFileName = workdir + File.separator + "obs.list";
        PrintWriter ow = Utils.openOut(obsFileName);
        appendObs(ow, obs, -1);
        ow.flush();
        ow.close();
    }

    public void updateFactorGraphWithObservation(Map<NodeT, Boolean> obs) {
        dumpFactorGraph();
        dumpObs(obs);
        invokeUpdater();
        Map<Integer, double[]> updatedPriors = fetchParams();
        for (Integer paramId : updatedPriors.keySet()) {
            distNodes.get(paramId).updateProbs(updatedPriors.get(paramId));
        }
    }

    public Map<NodeT, Double> queryFactorGraph(Collection<NodeT> queries) {
        dumpFactorGraph();

        String queryFileName = workdir + File.separator + "query.list";
        PrintWriter qw = Utils.openOut(queryFileName);
        int offset = distNodes.size();
        for (NodeT q : queries) {
            int queryId = nodes.indexOf(q);
            if (queryId < 0) continue;
            qw.println(offset + queryId);
        }
        qw.flush();
        qw.close();

        invokePredictor();

        Map<Integer,Double> preds = fetchPrediction();
        Map<NodeT, Double> ret = new HashMap<>();
        for (Integer queryId : preds.keySet()) {
            NodeT qNode = nodes.get(queryId - offset);
            ret.put(qNode, preds.get(queryId));
        }
        return ret;
    }

    private void invokeUpdater() {
        String daiPath = System.getProperty("chord.inference.dai.path");
        String fgFileName = workdir + File.separator + "pln.fg";
        String paramsFileName = workdir + File.separator + "params.list";
        String obsFileName = workdir + File.separator + "obs.list";
        String weightsFileName = workdir + File.separator + "weights.list";
        String[] cmdArray = new String[] {
                daiPath + File.separator + "updater",
                fgFileName,
                obsFileName,
                paramsFileName,
                weightsFileName
        };
        String cmd = "";
        for (String s : cmdArray)
            cmd += s + " ";
        Messages.log("Starting command: '%s'", cmd);
        int elapsedTime = (int) (System.currentTimeMillis() - Main.startTime);
        int timeout = Config.v().timeoutMillis;
        timeout -= elapsedTime;
        try {
            int result = ProcessExecutor.execute(cmdArray, null, null, timeout);
            if (result != 0)
                throw new RuntimeException("Return value=" + result);
        } catch (Throwable ex) {
            Messages.fatal("Command '%s' terminated abnormally: %s", cmd, ex.getMessage());
        }
        Messages.log("Finished command: '%s'", cmd);
    }

    private void invokePredictor() {
        String daiPath = System.getProperty("chord.inference.dai.path");
        String fgFileName = workdir + File.separator + "pln.fg";
        String queryFileName = workdir + File.separator + "query.list";
        String predFileName = workdir + File.separator + "prediction.list";
        String[] cmdArray = new String[] {
                daiPath + File.separator + "predictor",
                fgFileName,
                queryFileName,
                predFileName
        };
        String cmd = "";
        for (String s : cmdArray)
            cmd += s + " ";
        Messages.log("Starting command: '%s'", cmd);
        int elapsedTime = (int) (System.currentTimeMillis() - Main.startTime);
        int timeout = Config.v().timeoutMillis;
        timeout -= elapsedTime;
        try {
            int result = ProcessExecutor.execute(cmdArray, null, null, timeout);
            if (result != 0)
                throw new RuntimeException("Return value=" + result);
        } catch (Throwable ex) {
            Messages.fatal("Command '%s' terminated abnormally: %s", cmd, ex.getMessage());
        }
        Messages.log("Finished command: '%s'", cmd);
    }

    private Map<Integer, double[]> fetchParams() {
        String newParamsFileName = workdir + File.separator + "weights.list";
        Map<Integer, double[]> parsed = new HashMap<>();
        try {
            BufferedReader in = new BufferedReader(new FileReader(newParamsFileName));
            String s;
            while ((s = in.readLine()) != null) {
                String[] arr = s.split("\\s+");
                int paramId = Integer.parseInt(arr[0]);
                double[] weights = new double[arr.length-1];
                for (int i = 1; i < arr.length; i++) {
                    weights[i-1] = Double.parseDouble(arr[i]);
                }
                parsed.put(paramId, weights);
            }
            in.close();
        } catch (Exception ex) {
            Messages.fatal(ex);
        }
        return parsed;
    }

    private Map<Integer, Double> fetchPrediction() {
        String predFileName = workdir + File.separator + "prediction.list";
        Map<Integer, Double> parsed = new HashMap<>();
        try {
            BufferedReader in = new BufferedReader(new FileReader(predFileName));
            String s;
            while ((s = in.readLine()) != null) {
                String[] arr = s.split("\\s+");
                assert arr.length == 2;
                int paramId = Integer.parseInt(arr[0]);
                double prob = Double.parseDouble(arr[1]);
                parsed.put(paramId, prob);
            }
            in.close();
        } catch (Exception ex) {
            Messages.fatal(ex);
        }
        return parsed;
    }

    @Deprecated
    public Map<NodeT, Double> queryFactorGraphWithObservations(List<Map<NodeT, Boolean>> obses, Collection<NodeT> queries) {
        dumpRepeatedFactorGraph(obses.size());
        dumpObses(obses);
        String queryFileName = workdir + File.separator + "query.list";
        PrintWriter qw = Utils.openOut(queryFileName);
        int offset = distNodes.size();
        for (NodeT q : queries) {
            int queryId = nodes.indexOf(q);
            if (queryId < 0) continue;
            qw.println(offset + queryId);
        }
        qw.flush();
        qw.close();
        invokeMultiPredictor();
        Map<Integer, Double> preds = fetchPrediction();
        Map<NodeT, Double> ret = new HashMap<>();
        for (Integer queryId : preds.keySet()) {
            NodeT qNode = nodes.get(queryId - offset);
            ret.put(qNode, preds.get(queryId));
        }
        return ret;
    }

    @Deprecated
    private void invokeMultiPredictor() {
        String daiPath = System.getProperty("chord.inference.dai.path");
        String fgFileName = workdir + File.separator + "pln.fg";
        String obsFileName = workdir + File.separator + "obs.list";
        String queryFileName = workdir + File.separator + "query.list";
        String predFileName = workdir + File.separator + "prediction.list";
        String paramFileName = workdir + File.separator + "params.list";
        String weightsFileName = workdir + File.separator + "weights.list";
        String[] cmdArray = new String[] {
                daiPath + File.separator + "multi-predictor",
                fgFileName,
                obsFileName,
                queryFileName,
                predFileName,
                paramFileName,
                weightsFileName
        };
        String cmd = "";
        for (String s : cmdArray)
            cmd += s + " ";
        Messages.log("Starting command: '%s'", cmd);
        int elapsedTime = (int) (System.currentTimeMillis() - Main.startTime);
        int timeout = Config.v().timeoutMillis;
        timeout -= elapsedTime;
        try {
            int result = ProcessExecutor.execute(cmdArray, null, null, timeout);
            if (result != 0)
                throw new RuntimeException("Return value=" + result);
        } catch (Throwable ex) {
            Messages.fatal("Command '%s' terminated abnormally: %s", cmd, ex.getMessage());
        }
        Messages.log("Finished command: '%s'", cmd);
    }
}

