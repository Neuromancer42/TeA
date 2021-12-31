package inference;

import chord.project.Messages;
import chord.util.IndexMap;
import chord.util.Utils;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Function;

// Probabilistic Logic Network structure
// (i.e. probabilistic term logic)
// TODO: accept DNF format only
public class PLN<NodeT> {
    // each node is an object
    private final IndexMap<NodeT> nodes;
    // each node either correspond to a sum or a product, or is a singleton
    private final Map<Integer, Set<Integer>> sums;
    private final Map<Integer, Set<Integer>> prods;
    // some nodes corresponds to a distribution, or is determinant
    private final Map<Integer, Integer> priors;
    private final IndexMap<Categorical01> distNodes;

    public PLN(
            Collection<NodeT> nodes,
            Map<NodeT, Set<NodeT>> sums,
            Map<NodeT, Set<NodeT>> prods,
            Map<NodeT, Categorical01> priors
    ) {
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
    }

    public void dump(String dir) {
        String netFileName = dir + File.separator + "pln.txt";
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

        String distFileName = dir + File.separator + "priors.list";
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

    public void dumpDot(String dir, Function<NodeT, String> nodeRepr, Function<Categorical01, String> distRepr) {
        String dotFileName = dir + File.separator + "pln.dot";
        PrintWriter dw = Utils.openOut(dotFileName);
        dw.println("digraph G{");
        dw.println("\tsubgraph cluster0 {");
        dw.println("\t\tlabel=\"trace\";");
        for (int i = 0; i < nodes.size(); i++) {
            dw.print("\t\tn" + i);
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
        dw.println("\t}");

        for (int i = 0; i < distNodes.size(); i++) {
            dw.print("\tp"+i);
            dw.print(" [");
            Categorical01 distNode = distNodes.get(i);
            String label = distRepr.apply(distNode);
            dw.print("label=\""+label+"\"");
            dw.print(",shape=box");
            dw.println("];");
        }

        for (Integer headId : sums.keySet())
            for (Integer subId : sums.get(headId)) {
                dw.print("\t");
                dw.print("n"+subId);
                dw.print(" -> ");
                dw.print("n"+headId);
                dw.println(" [style=dotted];");
            }
        for (Integer headId  : prods.keySet())
            for (Integer subId : prods.get(headId)) {
                dw.print("\t");
                dw.print("n"+subId);
                dw.print(" -> ");
                dw.print("n"+headId);
                dw.println(";");
            }
        for (Integer nodeId : priors.keySet()) {
            int distId = priors.get(nodeId);
            dw.print("\t");
            dw.print("p"+distId);
            dw.print(" -> ");
            dw.print("n"+nodeId);
            dw.println(" [style=bold];");
        }
        dw.println("}");
        dw.flush();
        dw.close();
    }

    public void dumpFactorGraph(String dir) {
        // unsigned int has 32 bits
        // 16 bits for probability representation
        // 1 bits for head
        int defaultClauseLimit = 15;
        dumpFactorGraph(dir, defaultClauseLimit);
    }

    // TODO: separate conditionally on probabilistic / determinant nodes
    public void dumpFactorGraph(String dir, int clauseLimit) {
        assert (clauseLimit > 1);
        String fgFileName = dir + File.separator + "pln.fg";
        PrintWriter fw = Utils.openOut(fgFileName);
        int numFacts = nodes.size() + distNodes.size();
        int phonyId = numFacts;
        int numPhony = 0;
        // count number of phony nodes
        // each phony reduces subnums by (clauseLimit-1)
        for (Integer headId : sums.keySet()) {
            int subNum = sums.get(headId).size();
            while (subNum > clauseLimit) {
                numPhony++;
                subNum -= clauseLimit - 1;
            }
        }
        for (Integer headId : prods.keySet()) {
            int subNum = prods.get(headId).size();
            while (subNum > clauseLimit) {
                numPhony++;
                subNum -= clauseLimit - 1;
            }
        }
        numFacts += numPhony;
        fw.println(numFacts);
        fw.flush();
        // each nodes and each distnode has a factor block
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
                    assert phonyId < numFacts;
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
                    assert phonyId < numFacts;
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
        fw.flush();
        fw.close();
        Messages.log("FactorGraph consisting of "+ nodes.size() + " nodes, " + distNodes.size() + " dist nodes and " + numPhony + " phony nodes." );
    }
}

