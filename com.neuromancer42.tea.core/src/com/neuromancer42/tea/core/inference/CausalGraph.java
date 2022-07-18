package com.neuromancer42.tea.core.inference;

import com.neuromancer42.tea.core.project.Config;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.ProcessExecutor;
import com.neuromancer42.tea.core.util.IndexMap;
import com.neuromancer42.tea.core.util.IndexSet;
import com.neuromancer42.tea.core.util.Utils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

// Probabilistic Logic Network structure
// (i.e. probabilistic term logic, in a causal bayesian network form)
// 1. each node is either an input / conjunction / disjunction
// 2. each conjunction and each input node has a related probability
// 3. in causal graph format, every conjunction/disjunction has a dummy node for stochastic part
public class CausalGraph<NodeT> {

    // Nodes should better be Set<>, but for stability, we use ordered structure
    private final IndexMap<NodeT> nodes = new IndexMap<>();
    // each node either correspond to a sum or a product, or is a singleton
    private final Map<Integer, List<Integer>> sums = new HashMap<>();
    private final Map<Integer, List<Integer>> prods = new HashMap<>();
    private final Set<Integer> singletons = new HashSet<>();
    // nodes either corresponds to a distribution, or are determinant
    private final Map<Integer, Integer> stochMap = new HashMap<>();
    private final IndexMap<Categorical01> distNodes = new IndexMap<>();

    public CausalGraph(
            Collection<NodeT> nodes,
            Collection<NodeT> singletons,
            Map<NodeT, List<NodeT>> sums,
            Map<NodeT, List<NodeT>> prods
    ) {
        // build determinant part and verify well-formedness
        this.nodes.addAll(nodes);

        for (NodeT singleton: singletons) {
            int nodeId = this.nodes.indexOf(singleton);
            if (!this.nodes.contains(singleton)) {
                Messages.fatal("CausalGraph: unmet singleton " + singleton);
            }
            this.singletons.add(nodeId);
        }

        for (NodeT head : sums.keySet()) {
            int headId = this.nodes.indexOf(head);
            if (!this.nodes.contains(head)) {
                Messages.fatal("CausalGraph: unmet head " + head);
            }
            List<Integer> bodyIds = new ArrayList<>();
            for (NodeT sub : sums.get(head)) {
                int subId = this.nodes.indexOf(sub);
                if (!this.nodes.contains(sub)) {
                    Messages.fatal("CausalGraph: unmet sub " + sub + "in head " + head);
                }
                bodyIds.add(subId);
            }
            this.sums.put(headId, bodyIds);
        }

        for (NodeT head : prods.keySet()) {
            int headId = this.nodes.indexOf(head);
            if (!this.nodes.contains(head)) {
                Messages.fatal("CausalGraph: unmet head " + head);
            }
            List<Integer> bodyIds = new ArrayList<>();
            for (NodeT sub : prods.get(head)) {
                int subId = this.nodes.indexOf(sub);
                if (!this.nodes.contains(sub)) {
                    Messages.fatal("CausalGraph: skip unmet sub " + sub + " in head " + head);
                }
                bodyIds.add(subId);
            }
            this.prods.put(headId, bodyIds);
        }

        for (NodeT node : nodes) {
            int occurrance = 0;
            if (singletons.contains(node)) occurrance += 1;
            if (sums.containsKey(node)) occurrance += 1;
            if (prods.containsKey(node)) occurrance += 1;
            if (occurrance > 1) {
                Messages.fatal("CausalGraph: overlapped node " + node);
            }
            if (occurrance == 0) {
                Messages.fatal("CausalGraph: redundant node " + node);
            }
        }

    }

    public CausalGraph(
            Collection<NodeT> nodes,
            Collection<NodeT> singletons,
            Map<NodeT, List<NodeT>> sums,
            Map<NodeT, List<NodeT>> prods,
            Map<NodeT, Categorical01> distMap
    ) {
        this(nodes, singletons, sums, prods);
        setStochNodes(distMap);
    }

    public void setStochNode(NodeT node, Categorical01 dist) {
        if (!nodes.contains(node)) {
            Messages.error("CausalGraph: skip unmet stochastic node " + node);
            return;
        }
        this.distNodes.add(dist);
        int nodeId = this.nodes.indexOf(node);
        int distId = this.distNodes.indexOf(dist);
        this.stochMap.put(nodeId, distId);
    }

    public void setStochNodes(Map<NodeT, Categorical01> distMap) {
        for (Map.Entry<NodeT, Categorical01> stochNodeEntry : distMap.entrySet()) {
            NodeT node = stochNodeEntry.getKey();
            Categorical01 dist = stochNodeEntry.getValue();
            setStochNode(node, dist);
        }
    }

    public void dump(Path workdir) {
        try {
            Path netFilePath = workdir.resolve("causal.txt");
            List<String> netLines = new ArrayList<String>();
            for (int nodeId = 0; nodeId < nodes.size(); nodeId++) {
                // 1. head id
                StringBuilder lb = new StringBuilder();
                lb.append(nodeId);
                lb.append("\t");

                // 2. dist id
                Integer distId = stochMap.get(nodeId);
                if (distId != null) {
                    lb.append(distNodes.indexOf(distId));
                } else {
                    lb.append("D"); // D for determinant
                }
                lb.append("\t");

                // 3. body symbol + body length + body items*
                if (sums.containsKey(nodeId)) {
                    List<Integer> sumSubIds = sums.get(nodeId);
                    int sumNum = sumSubIds.size();
                    lb.append(sumNum);
                    lb.append("\t");
                    lb.append("+");
                    for (Integer sumSubId : sumSubIds) {
                        lb.append("\t").append(sumSubId);
                    }
                } else if (prods.containsKey(nodeId)) {
                    List<Integer> prodSubIds = prods.get(nodeId);
                    int prodNum = prodSubIds.size();
                    lb.append(prodNum);
                    lb.append("\t");
                    lb.append("*");
                    for (Integer prodSubId : prodSubIds) {
                        lb.append("\t").append(prodSubId);
                    }
                } else {
                    lb.append(0);
                }
                netLines.add(lb.toString());
            }
            Files.write(netFilePath, netLines, StandardCharsets.UTF_8);

            Path distFilePath = workdir.resolve("priors.list");
            List<String> distLines = new ArrayList<>(distNodes.size());
            for (int i = 0; i < distNodes.size(); i++) {
                StringBuilder lb = new StringBuilder();
                lb.append(i);
                Categorical01 dist = distNodes.get(i);
                int valNum = dist.getSupports().length;
                lb.append("\t");
                lb.append(valNum);
                for (int j = 0; j < valNum; j++) {
                    lb.append("\t");
                    double val = dist.getSupports()[j];
                    lb.append(val);
                    lb.append("\t");
                    lb.append(dist.probability(val));
                }
                distLines.add(lb.toString());
            }
            Files.write(distFilePath, distLines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Messages.error("CausalGraph: failed to dump causal graph, skip");
            e.printStackTrace();
        }
    }

    private void dumpDotSubgraph(List<String> lines, Function<NodeT, String> nodeRepr, Integer trace) {
        String suffix = trace==null ? "_x" : ("_"+trace);
        lines.add("\tsubgraph cluster"+suffix+" {");
        lines.add("\t\tlabel=\"trace"+suffix+"\";");
        for (int nodeId = 0; nodeId < nodes.size(); nodeId++) {
            StringBuilder lb = new StringBuilder();
            lb.append("\t\t");
            lb.append("n").append(nodeId).append(suffix);
            lb.append(" [");
            NodeT node = nodes.get(nodeId);

            String label = nodeRepr.apply(node);
            if (trace == null) label = (nodeId + distNodes.size()) + "\n" + label;
            lb.append("label=\"").append(label).append("\"");

            String shape = "";
            String style = "";
            if (sums.containsKey(nodeId)) {
                shape = "ellipse";
            } else if (prods.containsKey(nodeId)) {
                shape = "box";
            } else {
                shape = "ellipse";
                style = "filled";
            }
            lb.append(",shape=").append(shape);
            if (!style.equals(""))
                lb.append(",style=").append(style);
            lb.append("];");
            lines.add(lb.toString());
        }
        for (Map.Entry<Integer, List<Integer>> sum : sums.entrySet()) {
            Integer headId = sum.getKey();
            for (Integer subId : sum.getValue()) {
                String line = "\t" +
                        "n" + subId + suffix +
                        " -> " +
                        "n" + headId + suffix +
                        " [style=dotted];";
                lines.add(line);
            }
        }
        for (Map.Entry<Integer, List<Integer>> prod : prods.entrySet()) {
            Integer headId = prod.getKey();
            for (Integer subId : prod.getValue()) {
                String line = "\t" +
                        "n" + subId + suffix +
                        " -> " +
                        "n" + headId + suffix +
                        ";";
                lines.add(line);
            }
        }
        for (Map.Entry<Integer, Integer> distEntry : stochMap.entrySet()) {
            Integer nodeId = distEntry.getKey();
            Integer distId = distEntry.getValue();
            String line = "\t" +
                    "p" + distId +
                    " -> " +
                    "n" + nodeId + suffix +
                    " [style=bold];";
            lines.add(line);
        }
        lines.add("\t}");
    }

    public void dumpDot(Path dotFilePath, Function<NodeT, String> nodeRepr, Function<Categorical01, String> distRepr) {
        dumpDot(dotFilePath, nodeRepr, distRepr, 0);
    }

    public void dumpDot(Path dotFilePath, Function<NodeT, String> nodeRepr, Function<Categorical01, String> distRepr, int numRepeats) {
        List<String> lines = new ArrayList<>();
        lines.add("digraph G{");

        lines.add("subgraph cluster_prior {");
        lines.add("label=params;");
        for (int distId = 0; distId < distNodes.size(); distId++) {
            Categorical01 distNode = distNodes.get(distId);
            String label = distRepr.apply(distNode);
            String line = "\t" +
                    "p" + distId +
                    " [" +
                    "label=\""+distId+"\n"+label+"\"" +
                    ",shape=box,style=filled" +
                    "];";
            lines.add(line);
        }
        lines.add("}");

        dumpDotSubgraph(lines, nodeRepr, null);

        for (int i = 0; i < numRepeats; i++) {
            dumpDotSubgraph(lines, nodeRepr, i);
        }

        lines.add("}");
        try {
            Files.write(dotFilePath, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Messages.error("CausalGraph: failed to dump .dot file of causal graph, skip");
            e.printStackTrace();
        }
    }

    public Integer getNodeId(NodeT node) {
        int nodeId = nodes.indexOf(node);
        if (nodeId < 0)
            return null;
        else
            return nodeId;
    }

    public Set<NodeT> getAllNodes() {
        Set<NodeT> allNodes = new HashSet<>();
        for (NodeT node: nodes) {
            allNodes.add(node);
        }
        return allNodes;
    }

    public IndexMap<Categorical01> getAllDistNodes() {
        return distNodes;
    }
}

