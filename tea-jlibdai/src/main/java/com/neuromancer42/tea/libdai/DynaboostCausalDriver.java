package com.neuromancer42.tea.libdai;

import com.neuromancer42.tea.commons.inference.AbstractCausalDriver;
import com.neuromancer42.tea.commons.inference.Categorical01;
import com.neuromancer42.tea.commons.inference.CausalGraph;

import java.nio.file.Path;
import java.util.*;

public class DynaboostCausalDriver extends AbstractCausalDriver {
    public static final String type = "dynaboost";
    private final Map<Object, Integer> obsCount = new LinkedHashMap<>();
    private final double ruleProb = Double.parseDouble(System.getProperty("dynaboost.ruleprob", "0.999"));
    private int obsRuns;
    private boolean updated;
    private DAIMetaNetwork metaNetwork;

    public DynaboostCausalDriver(String name, Path path, CausalGraph causalGraph) {
        super(name, path, causalGraph);
        // TODO: reset all dist nodes to fixed probability
        super.causalGraph.resetStochNodes();
        var prodIter = super.causalGraph.getProdIter();
        while (prodIter.hasNext()) {
            var prod = prodIter.next();
            Object prodNode = causalGraph.getNode(prod.getKey());
            super.causalGraph.setStochNode(prodNode, new Categorical01(ruleProb));
        }
        updated = false;
        obsRuns = 0;
    }


    @Override
    protected void appendObservation(Map<Object, Boolean> obs) {
        obsRuns++;
        for (var entry : obs.entrySet()) {
            Object obsNode = entry.getKey();
            obsCount.putIfAbsent(obsNode, 0);
            Boolean obsVal = entry.getValue();
            if (obsVal) {
                obsCount.compute(obsNode, (k, v) -> (v == null) ? 1 : (v + 1));
            }
        }
        // drop previous results
        updated = false;
        metaNetwork = null;
    }

    @Override
    protected Double queryPossibilityById(int nodeId) {
        if (!updated) {
            invokeUpdater();
            updated = true;
        }
//        Messages.debug("DynaboostInferer: querying possibility of node #%d", nodeId);
        return metaNetwork.predictNode(nodeId);
    }

    @Override
    protected double[] queryFactorById(int distId) {
        if (!updated) {
            invokeUpdater();
            updated = true;
        }
        return metaNetwork.queryParamPosterior(distId);
    }


    private void invokeUpdater() {
        CausalGraph differentiated = new CausalGraph(this.causalGraph);
        differentiated.setName(differentiated.getName() + "_Dyna_" + obsRuns);
        // differentiate unfiltered dataflow, and apply observed frequencies
        for (var entry: obsCount.entrySet()) {
            Object node = entry.getKey();
            int nodeId = differentiated.getNodeId(node);
            double freq = entry.getValue() * 1.0 / obsRuns;
            // 1. create differentiated Node
            String tNode = "_DynaT_" + node;
            int tNodeId = differentiated.addNode(tNode);
//            Messages.debug("DynaboostInferer: differentiating %d[%s] to %d[%s]", nodeId, node, tNodeId, tNode);
            // 2. create derivations of tNode
            List<Integer> derivations = differentiated.getSum(nodeId);
            if (derivations == null) continue;
            Map<Integer, List<Integer>> tDrivations = new LinkedHashMap<>();
            for (Integer derivId : derivations) {
                List<Integer> deriveBody = differentiated.getProd(derivId);
                if (deriveBody == null) continue;
                List<Integer> tDeriveBody = new ArrayList<>();
                tDeriveBody.add(nodeId);
                tDeriveBody.addAll(deriveBody);
                String tDeriveNode = "_DynaT_" + differentiated.getNode(derivId);
                int tDeriveId = differentiated.addNode(tDeriveNode);
                tDrivations.put(tDeriveId, tDeriveBody);
            }
            // 3. replace nodeId to tNodeId following derivations
            var prodIter = differentiated.getProdIter();
            while (prodIter.hasNext()) {
                var prod = prodIter.next();
                List<Integer> prodBody = prod.getValue();
                prodBody.replaceAll(id -> id == nodeId ? tNodeId : id);
                differentiated.addProd(prod.getKey(), prodBody);
            }
            // 4. insert derivations of tNodeId
            for (var tDeriveEntry : tDrivations.entrySet()) {
                Integer tDeriveHeadId = tDeriveEntry.getKey();
                differentiated.addProd(tDeriveHeadId, tDeriveEntry.getValue());
                differentiated.setStochNode(differentiated.getNode(tDeriveHeadId), new Categorical01(ruleProb));
            }
            differentiated.addSum(tNodeId, new ArrayList<>(tDrivations.keySet()));
            // 5. add dummy observation node
            String oCls = "_DynaOCls_" + node;
            int oClsId = differentiated.addNode(oCls);
            differentiated.addProd(oClsId, List.of(nodeId));
            differentiated.setStochNode(oCls, new Categorical01(freq));
            String oNode = "_DynaO_" + node;
            int oNodeId = differentiated.addNode(oNode);
            differentiated.addSum(oNodeId, List.of(oClsId));
//            Messages.debug("DynaboostInferer: append observation cls %d[%s] and node %d[%s] of %d[%s]", oClsId, oCls, oNodeId, oNode, nodeId, node);

        }
        {
            // for debug only
            differentiated.dump(workDir);
        }
        // create meta network
        if (metaNetwork != null) {
            metaNetwork.dumpQueries(workDir.resolve(String.format("%03d.query", obsRuns-1)));
            metaNetwork.release();
        }
        metaNetwork = DAIMetaNetwork.createDAIMetaNetwork(workDir, name + "_" + obsRuns, differentiated, 0, false, false);
        // apply observations
        for (Object node : obsCount.keySet()) {
            String oNode = "_DynaO_" + node;
            int oNodeId = differentiated.getNodeId(oNode);
//            Messages.debug("DynaboostInferer: set observation node %d[%s] to true", oNodeId, oNode);
            metaNetwork.observeNode(oNodeId, 0, true);
        }
    }
}
