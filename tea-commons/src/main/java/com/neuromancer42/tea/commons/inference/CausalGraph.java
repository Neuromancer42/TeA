package com.neuromancer42.tea.commons.inference;

import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.util.IndexMap;

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
public class CausalGraph {
    public static CausalGraph createCausalGraph(
            String name,
            Collection<String> nodes,
            Collection<String> singletons,
            Map<String, List<String>> sums,
            Map<String, List<String>> prods
    ) {
        IndexMap<String> nodeList = new IndexMap<>();
        nodeList.addAll(nodes);

        Set<Integer> singletonSet = new HashSet<>();
        for (String singleton: singletons) {
            int nodeId = nodeList.indexOf(singleton);
            if (!nodeList.contains(singleton)) {
                Messages.fatal("CausalGraph: unmet singleton " + singleton);
            }
            singletonSet.add(nodeId);
        }

        Map<Integer, List<Integer>> sumMap = new LinkedHashMap<>();
        for (String head : sums.keySet()) {
            int headId = nodeList.indexOf(head);
            if (!nodeList.contains(head)) {
                Messages.fatal("CausalGraph: unmet head " + head);
            }
            // use Set to eliminate duplicate items
            Set<Integer> bodyIds = new LinkedHashSet<>();
            for (String sub : sums.get(head)) {
                int subId = nodeList.indexOf(sub);
                if (!nodeList.contains(sub)) {
                    Messages.fatal("CausalGraph: unmet sub " + sub + "in head " + head);
                }
                if (subId == headId) {
                    Messages.fatal("CausalGraph: ill-formed, head " + head + " exists in bodies");
                }
                bodyIds.add(subId);
            }
            sumMap.put(headId, new ArrayList<>(bodyIds));
        }

        Map<Integer, List<Integer>> prodMap = new LinkedHashMap<>();
        for (String head : prods.keySet()) {
            int headId = nodeList.indexOf(head);
            if (!nodeList.contains(head)) {
                Messages.fatal("CausalGraph: unmet head " + head);
            }
            // use Set to eliminate duplicate items
            Set<Integer> bodyIds = new LinkedHashSet<>();
            for (String sub : prods.get(head)) {
                int subId = nodeList.indexOf(sub);
                if (!nodeList.contains(sub)) {
                    Messages.fatal("CausalGraph: unmet sub " + sub + " in head " + head);
                }
                if (subId == headId) {
                    Messages.fatal("CausalGraph: ill-formed, head " + head + " exists in bodies");
                }
                bodyIds.add(subId);
            }
            prodMap.put(headId, new ArrayList<>(bodyIds));
        }

        for (String node : nodes) {
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
        return new CausalGraph(name, nodeList, singletonSet, sumMap, prodMap);
    }

    public static CausalGraph createCausalGraph(
            String name,
            Collection<String> nodes,
            Collection<String> singletons,
            Map<String, List<String>> sums,
            Map<String, List<String>> prods,
            Map<String, Categorical01> distMap
    ) {
        CausalGraph causalGraph = createCausalGraph(name, nodes, singletons, sums, prods);
        causalGraph.setStochNodes(distMap);
        return causalGraph;
    }

    // Nodes should better be Set<>, but for stability, we use ordered structure
    private final IndexMap<String> nodes;
    // each node either correspond to a sum or a product, or is a singleton
    private final Set<Integer> singletons;
    private final Map<Integer, List<Integer>> sums;
    private final Map<Integer, List<Integer>> prods;
    // nodes either corresponds to a distribution, or are determinant
    private final Map<Integer, Integer> stochMap;
    private final IndexMap<Categorical01> distNodes;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private String name;

    private CausalGraph(
            String name,
            IndexMap<String> nodes,
            Set<Integer> singletons,
            Map<Integer, List<Integer>> sums,
            Map<Integer, List<Integer>> prods
    ) {
        this.name = name;
        this.nodes = new IndexMap<>();
        for (var n : nodes)
            this.nodes.add(n);
        this.singletons = new LinkedHashSet<>(singletons);
        this.sums = new HashMap<>();
        for (var entry: sums.entrySet()) {
            int sumHead = entry.getKey();
            List<Integer> sumBody = new ArrayList<>(entry.getValue());
            this.sums.put(sumHead, sumBody);
        }
        this.prods = new HashMap<>();
        for (var entry: prods.entrySet()) {
            int prodHead = entry.getKey();
            List<Integer> prodBody = new ArrayList<>(entry.getValue());
            this.prods.put(prodHead, prodBody);
        }
        this.stochMap = new HashMap<>();
        this.distNodes = new IndexMap<>();
    }

    public CausalGraph(CausalGraph cg) {
        this(cg.name, cg.nodes, cg.singletons, cg.sums, cg.prods);
        for (Categorical01 distNode : cg.distNodes) {
            this.distNodes.add(new Categorical01(distNode));
        }
        this.stochMap.putAll(cg.stochMap);
    }

    public void setStochNode(String node, Categorical01 dist) {
        if (!nodes.contains(node)) {
            Messages.error("CausalGraph: skip unmet stochastic node " + node);
            return;
        }
        this.distNodes.add(dist);
        int nodeId = this.nodes.indexOf(node);
        int distId = this.distNodes.indexOf(dist);
        this.stochMap.put(nodeId, distId);
    }

    public void setStochNodes(Map<String, Categorical01> distMap) {
        for (Map.Entry<String, Categorical01> stochNodeEntry : distMap.entrySet()) {
            String node = stochNodeEntry.getKey();
            Categorical01 dist = stochNodeEntry.getValue();
            setStochNode(node, dist);
        }
    }

    public void resetStochNodes() {
        this.stochMap.clear();
        this.distNodes.clear();
    }

    public void dump(Path workdir) {
        try {
            Path netFilePath = workdir.resolve(name + ".causal.graph");
            List<String> netLines = new ArrayList<>();
            for (int nodeId = 0; nodeId < nodes.size(); nodeId++) {
                // 1. head id
                StringBuilder lb = new StringBuilder();
                lb.append(nodeId);
                lb.append("\t");

                // 2. dist id
                Integer distId = stochMap.get(nodeId);
                if (distId != null) {
                    lb.append(distId);
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

            Path distFilePath = workdir.resolve(name + ".priors.list");
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
            Messages.debug("CausalGraph: dumping causal graph to path " + netFilePath);
        } catch (IOException e) {
            Messages.error("CausalGraph: failed to dump causal graph, skip");
            e.printStackTrace();
        }
    }

    private void dumpDotSubgraph(List<String> lines, Function<String, String> nodeRepr, Integer trace) {
        String suffix = trace==null ? "_x" : ("_"+trace);
        lines.add("\tsubgraph cluster"+suffix+" {");
        lines.add("\t\tlabel=\"trace"+suffix+"\";");
        for (int nodeId = 0; nodeId < nodes.size(); nodeId++) {
            StringBuilder lb = new StringBuilder();
            lb.append("\t\t");
            lb.append("n").append(nodeId).append(suffix);
            lb.append(" [");
            String node = nodes.get(nodeId);

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

    public void dumpDot(Path dotFilePath, Function<String, String> nodeRepr, Function<Categorical01, String> distRepr) {
        dumpDot(dotFilePath, nodeRepr, distRepr, 0);
    }

    public void dumpDot(Path dotFilePath, Function<String, String> nodeRepr, Function<Categorical01, String> distRepr, int numRepeats) {
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

    public Integer getNodeId(String node) {
        int nodeId = nodes.indexOf(node);
        if (nodeId < 0)
            return null;
        else
            return nodeId;
    }

    public String getNode(Integer nodeId) {
        return nodes.get(nodeId);
    }

    public Set<String> getAllNodes() {
        Set<String> allNodes = new HashSet<>();
        for (String node: nodes) {
            allNodes.add(node);
        }
        return allNodes;
    }

    public int nodeSize() {
        return nodes.size();
    }

    public int distSize() {
        return distNodes.size();
    }

    public IndexMap<Categorical01> getAllDistNodes() {
        return distNodes;
    }

    public Iterator<Map.Entry<Integer, List<Integer>>> getSumIter() {
        return sums.entrySet().iterator();
    }

    public Iterator<Map.Entry<Integer, List<Integer>>> getProdIter() {
        return prods.entrySet().iterator();
    }

    public boolean isSingleton(Integer nodeId) {
        return singletons.contains(nodeId);
    }

    public boolean isStochNode(Integer nodeId) {
        return stochMap.containsKey(nodeId);
    }

    public Integer getNodesDistId(Integer nodeId) {
        return stochMap.get(nodeId);
    }

    public Iterator<Integer> getSingletonIter() {
        return singletons.iterator();
    }

    public Integer addNode(String node) {
        this.nodes.add(node);
        return this.nodes.indexOf(node);
    }

    public void addProd(Integer prodHead, List<Integer> prodBody) {
        this.prods.put(prodHead, new ArrayList<>(prodBody));
    }

    public void addSum(Integer sumHead, List<Integer> sumBody) {
        this.sums.put(sumHead, new ArrayList<>(sumBody));
    }

    public List<Integer> getProd(Integer prodHead) {
        return this.prods.get(prodHead);
    }

    public List<Integer> getSum(Integer sumHead) {
        return this.sums.get(sumHead);
    }
}

