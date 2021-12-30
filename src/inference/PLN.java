package inference;

import chord.project.Messages;
import chord.util.IndexMap;
import chord.util.Utils;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

// Probabilistic Logic Network structure
// (i.e. probabilistic term logic)
// TODO: accept DNF format only
// TODO: split large clauses (to avoid integer overflow)
public class PLN<NodeT> {
    // each node is an object
    private final IndexMap<NodeT> nodes;
    // each node either correspond to a sum or a product, or is a singleton
    private final Map<NodeT, Set<NodeT>> sums;
    private final Map<NodeT, Set<NodeT>> prods;
    // some nodes corresponds to a distribution, or is determinant
    private final Map<NodeT, Categorical01> priors;
    private final IndexMap<Categorical01> distNodes;

    public PLN(
            Collection<NodeT> nodes,
            Map<NodeT, Set<NodeT>> sums,
            Map<NodeT, Set<NodeT>> prods,
            Map<NodeT, Categorical01> priors
    ) {
        this.nodes = new IndexMap<>(nodes.size());
        this.nodes.addAll(nodes);
        this.sums = new HashMap<>(sums);
        this.prods = new HashMap<>(prods);
        this.priors = new HashMap<>(priors);

        this.distNodes = new IndexMap<>();
        this.distNodes.addAll(priors.values());
    }

    public void dump(String dir) {
        String netFileName = dir + File.separator + "pln.txt";
        PrintWriter bw = Utils.openOut(netFileName);
        for (int i = 0; i < nodes.size(); i++) {
            bw.print(i + "\t");
            NodeT node = nodes.get(i);
            int distId = distNodes.indexOf(priors.get(node));
            bw.print(distId + "\t");
            Set<NodeT> sumNodes = sums.get(node);
            int sumNum = sumNodes == null ? 0 : sumNodes.size();
            Set<NodeT> prodNodes = prods.get(node);
            int prodNum = prodNodes == null ? 0 : prodNodes.size();
            if (sumNum > 0 && prodNum > 0)
                Messages.fatal("PLN: sums and products are not disjoint");
            if (sumNum > 0) {
                bw.print(sumNum);
                bw.print("\t+");
                for (NodeT sumNode : sumNodes) {
                    int sumId = nodes.indexOf(sumNode);
                    if (sumId < 0)
                        Messages.error("PLN: unmet node " + sumNode);
                    bw.print("\t" + sumId);
                }
                bw.println();
            } else if (prodNum > 0) {
                bw.print(prodNum);
                bw.print("\t*");
                for (NodeT prodNode : prodNodes) {
                    int prodId = nodes.indexOf(prodNode);
                    if (prodId < 0)
                        Messages.error("PLN: unmet node " + prodNode);
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

    public void dumpDot(String dir, Map<NodeT, String> nodeRepr, Map<Categorical01, String> distRepr) {
        String dotFileName = dir + File.separator + "pln.dot";
        PrintWriter dw = Utils.openOut(dotFileName);
        dw.println("digraph G{");
        dw.println("\tsubgraph cluster0 {");
        dw.println("\t\tlabel=\"trace\";");
        for (int i = 0; i < nodes.size(); i++) {
            dw.print("\t\tn" + i);
            dw.print(" [");
            NodeT node = nodes.get(i);

            String label = nodeRepr.getOrDefault(node, node.toString());
            dw.print("label=\""+label+"\"");

            String shape = "";
            String style = "";
            Set<NodeT> sumNodes = sums.get(node);
            int sumNum = sumNodes == null ? 0 : sumNodes.size();
            Set<NodeT> prodNodes = prods.get(node);
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
            String label = distRepr.getOrDefault(distNode, distNode.toString());
            dw.print("label=\""+label+"\"");
            dw.print(",shape=box");
            dw.println("];");
        }

        for (NodeT to : sums.keySet())
            for (NodeT from : sums.get(to)) {
                dw.print("\t");
                dw.print("n"+nodes.indexOf(from));
                dw.print(" -> ");
                dw.print("n"+nodes.indexOf(to));
                dw.println(" [style=dotted];");
            }
        for (NodeT to : prods.keySet())
            for (NodeT from : prods.get(to)) {
                dw.print("\t");
                dw.print("n"+nodes.indexOf(from));
                dw.print(" -> ");
                dw.print("n"+nodes.indexOf(to));
                dw.println(";");
            }
        for (NodeT node : priors.keySet()) {
            Categorical01 dist = priors.get(node);
            dw.print("\t");
            dw.print("p"+distNodes.indexOf(dist));
            dw.print(" -> ");
            dw.print("n"+nodes.indexOf(node));
            dw.println(" [style=bold];");
        }
        dw.println("}");
        dw.flush();
        dw.close();
    }

    public void dumpFactorGraph(String dir) {
        String fgFileName = dir + File.separator + "pln.fg";
        PrintWriter fw = Utils.openOut(fgFileName);
        int numFacts = nodes.size() + distNodes.size();
        fw.println(numFacts);
        // each nodes and each distnode has a factor block
        for (int i = 0; i < distNodes.size(); i++) {
            fw.println();
            Categorical01 distNode = distNodes.get(i);
//            fw.println("# DistNode " + i + " " + distNode.toString());
            fw.println(1); // variable numbers
            fw.println(i); // variable IDs
            List<Double> supports = distNode.getSupports();
            int cardinality = supports.size();
            fw.println(cardinality); // cardinalities
            fw.println(cardinality); // number of non-zero entries
            for (int j = 0; j < cardinality; j++) {
                fw.print(j);
                fw.print(" ");
                fw.println(distNode.probability(supports.get(j)));
            }
        }

        int offset = distNodes.size();

        for (int i = 0; i < nodes.size(); i++) {
            fw.println();
            NodeT node = nodes.get(i);
            List<Double> deterministic = new ArrayList<Double>(1);
            deterministic.add(1.0);
            Categorical01 priorDist = priors.get(node);
            List<Double> probs = priorDist == null ? deterministic : priorDist.getSupports();

            Set<NodeT> sum = sums.get(node);
            int sumNum = sum == null ? 0 : sum.size();
            Set<NodeT> prod = prods.get(node);
            int prodNum = prod == null ? 0 : prod.size();
            if (sumNum > 0 && prodNum > 0)
                Messages.fatal("PLN: sums and products are not disjoint");

            List<Integer> vars = new ArrayList<>();
            List<Integer> cards = new ArrayList<>();
            List<String> entries = new ArrayList<>();

            vars.add(offset + nodes.indexOf(node));
            cards.add(2);

            int probCnt = probs.size();
            if (probCnt > 1) {
                vars.add(distNodes.indexOf(priorDist));
                cards.add(probCnt);
            }
            if (sumNum > 0) {
                if (priorDist != null) {
                    Messages.error("Recommend using DNF format");
                }
//                fw.print("# Sum Node " + i + ": " + node.toString());
//                fw.println(" with prior " + distNodes.indexOf(priorDist));
                for (NodeT sub : sum) {
                    vars.add(offset + nodes.indexOf(sub));
                    cards.add(2);
                }
                for (int probRep = 0; probRep < probCnt; probRep++) {
                    int noneRep = probRep * 2;
                    String entry = noneRep + " " + 1;
                    entries.add(entry);
                }
                int allSubRep = (1 << sumNum) - 1; // 2^(subNum)-1
                for (int subRep = 1; subRep <= allSubRep; subRep++) {
                    for (int probRep = 0; probRep < probCnt; probRep++) {
                        double trueProb = probs.get(probRep);
                        double falseProb = 1.0D - trueProb;
                        int falseRep = (subRep * probCnt + probRep) * 2;
                        int trueRep = falseRep + 1;
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
                for (NodeT sub : prod) {
                    vars.add(offset + nodes.indexOf(sub));
                    cards.add(2);
                }
                int allSubRep = (1 << prodNum) - 1; // 2^(subNum)-1
                for (int subRep = 0; subRep < allSubRep; subRep++) {
                    for (int probRep = 0; probRep < probCnt; probRep++) {
                        int falseRep = (subRep * probCnt + probRep) * 2;
                        String falseEntry = falseRep + " " + 1;
                        entries.add(falseEntry);
                    }
                }
                for (int probRep = 0; probRep < probCnt; probRep++) {
                    double trueProb = probs.get(probRep);
                    double falseProb = 1.0D - trueProb;
                    int falseRep = (allSubRep * probCnt + probRep) * 2;
                    int trueRep = falseRep + 1;
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
                    double trueProb = probs.get(probRep);
                    double falseProb = 1.0D - trueProb;
                    int falseRep = probRep * 2;
                    int trueRep = falseRep + 1;
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
        }
        fw.flush();
        fw.close();
        Messages.log("FactorGraph consisting of "+ nodes.size() + " nodes and " + distNodes.size() + " dist nodes.");
    }
}

