package com.neuromancer42.tea.libdai;

import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.inference.AbstractCausalDriver;
import com.neuromancer42.tea.commons.inference.CausalGraph;

import java.nio.file.Path;
import java.util.*;

public class EMCausalDriver extends AbstractCausalDriver {

    public static final String type = "em";
    private final List<Map<Object, Boolean>> obsHistory = new ArrayList<>();

    private boolean updated;
    private DAIMetaNetwork metaNetwork;

    protected EMCausalDriver(String name, Path path, CausalGraph causalGraph) {
        super(name, path, causalGraph);
        updated = false;
    }

    @Override
    protected void appendObservation(Map<Object, Boolean> obs) {
        Map<Object, Boolean> filtered = doFilter(obs);
        obsHistory.add(filtered);
        // drop previous results
        updated = false;
    }

    private Map<Integer, Set<Integer>> cgRevProd = null;
    private Map<Integer, Set<Integer>> cgRevSum = null;
    private Map<Object, Boolean> doFilter(Map<Object, Boolean> obs) {
        // build reverse index from body to head;
        if (cgRevProd == null || cgRevSum == null) {
            cgRevProd = new HashMap<>();
            for (var prodIter = causalGraph.getProdIter(); prodIter.hasNext(); ) {
                var prod = prodIter.next();
                var headId = prod.getKey();
                var bodyIdList = prod.getValue();
                for (var bodyId : bodyIdList) {
                    cgRevProd.computeIfAbsent(bodyId, k -> new HashSet<>()).add(headId);
                }
            }
            cgRevSum = new HashMap<>();
            for (var sumIter = causalGraph.getSumIter(); sumIter.hasNext(); ) {
                var sum = sumIter.next();
                var headId = sum.getKey();
                var bodyIdList = sum.getValue();
                for (var bodyId : bodyIdList) {
                    cgRevSum.computeIfAbsent(bodyId, k -> new HashSet<>()).add(headId);
                }
            }
        }
        // Note:
        //      eliminate which one when 0 -> 1? 0 or 1?
        //      I think it should be the one to be eliminated as it tells less to the model;
        Map<Object, Boolean> filtered = new LinkedHashMap<>(obs);
        Set<Integer> workset = new LinkedHashSet<>();
        Set<Integer> mustBeZeroProdIds = new HashSet<>();
        for (var entry : obs.entrySet()) {
            if (!entry.getValue()) {
                var zeroBody = entry.getKey();
                var zeroBodyId = causalGraph.getNodeId(zeroBody);
                workset.add(zeroBodyId);
            }
        }
        while (!workset.isEmpty()) {
            Set<Integer> candSet = new LinkedHashSet<>();
            for (var zeroBodyId : workset) {
                var zeroBody = causalGraph.getNode(zeroBodyId);
                if (filtered.getOrDefault(zeroBody, false)) {
                    Messages.debug("CausalDriver %s: removing contradictory node +%d\n%s", getName(), zeroBodyId, zeroBody);
                    filtered.remove(zeroBody);
                }
                for (var prodHeadId : cgRevProd.getOrDefault(zeroBodyId, Set.of())) {
                    boolean newZero = mustBeZeroProdIds.add(prodHeadId);
                    if (newZero) {
                        candSet.addAll(cgRevSum.getOrDefault(prodHeadId, Set.of()));
                    }
                }
            }
            Set<Integer> newWorkset = new LinkedHashSet<>();
            for (var cand : candSet) {
                var sumBody = causalGraph.getSum(cand);
                if (mustBeZeroProdIds.containsAll(sumBody)) {
                    newWorkset.add(cand);
                }
            }
            workset = newWorkset;
        }
        Messages.log("CausalDriver %s: applying %d / %d observation", getName(), filtered.size(), obs.size());
        return filtered;
    }

    @Override
    protected Double queryPossibilityById(int nodeId) {
        if (!updated) {
            invokeLearner();
            updated = true;
        }
        return metaNetwork.predictNode(nodeId);
    }

    @Override
    protected double[] queryFactorById(int distId) {
        if (!updated) {
            invokeLearner();
            updated = true;
        }
        return metaNetwork.queryParamPosterior(distId);
    }

    private void invokeLearner() {
        // release old causal graph for memory performance
        if (metaNetwork != null) {
            metaNetwork.dumpQueries(workDir.resolve("em.query"));
            metaNetwork.release();
            metaNetwork = null;
        }
        String fileName = String.format("em_%03d", obsHistory.size());
        metaNetwork = DAIMetaNetwork.createDAIMetaNetwork(workDir, fileName, causalGraph, 0, false, true);
        if (obsHistory.size() > 0) {
            metaNetwork.runEM(workDir, fileName, obsHistory);
        }
    }
}
