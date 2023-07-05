package com.neuromancer42.tea.commons.provenance;

import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.inference.Categorical01;
import com.neuromancer42.tea.commons.inference.CausalGraph;
import com.neuromancer42.tea.commons.util.IndexMap;
import com.neuromancer42.tea.core.analysis.Trgt;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

public class ProvenanceUtil {
    private ProvenanceUtil() {}

    public static boolean dumpProvenance(Trgt.Provenance prov, Path path) {
        IndexMap<String> rules = new IndexMap<>();
        // dump pruned provenance
        Path consPath = path.resolve("cons_pruned.txt");
        try (BufferedWriter consWriter = Files.newBufferedWriter(consPath, StandardCharsets.UTF_8)) {
            for (int clsId = 0; clsId < prov.getConstraintCount(); ++clsId) {
                Trgt.Constraint cons = prov.getConstraint(clsId);
                rules.add(cons.getRuleInfo());
                int ruleId = rules.indexOf(cons.getRuleInfo());
                String consLine = prettifyConstraint(cons, "R" + ruleId + "-" + clsId);
                consWriter.append(consLine);
                consWriter.newLine();
            }
        } catch (IOException e) {
            Messages.error("ProvenanceUtil: failed to dump pruned constraint file, skip: %s", e.getMessage());
            return false;
        }
        // dump rule dictionary
        Path ruleDictPath = path.resolve("rule_dict.txt");
        try (BufferedWriter dictWriter = Files.newBufferedWriter(ruleDictPath, StandardCharsets.UTF_8)) {
            for (int i = 0; i < rules.size(); ++i) {
                String ruleId = "R" + i;
                dictWriter.append(ruleId).append(":\t").append(rules.get(i));
                dictWriter.newLine();
            }
        } catch (IOException e) {
            Messages.error("ProvenanceUtil: failed to dump rule dict file, skip: %s", e.getMessage());
            return false;
        }
        // dump output facts
        Path baseQueryPath = path.resolve("base_queries.txt");
        try (BufferedWriter baseQueryWriter = Files.newBufferedWriter(baseQueryPath, StandardCharsets.UTF_8)) {
            for (Trgt.Tuple q : prov.getOutputList()) {
                baseQueryWriter.append(prettifyTuple(q));
                baseQueryWriter.newLine();
            }
        } catch (IOException e) {
            Messages.error("ProvenanceUtil: failed to dump queries file, skip: %s", e.getMessage());
            return false;
        }
        return true;
    }

    private static String prettifyTuple(Trgt.Tuple tuple) {
        StringBuilder sb = new StringBuilder();
        sb.append(tuple.getRelName()).append("(");
        for (int i = 0; i < tuple.getAttrIdCount(); ++i) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(tuple.getAttrId(i));
        }
        sb.append(")");
        return sb.toString();
    }

    private static String prettifyConstraint(Trgt.Constraint cons, String marker) {
        StringBuilder sb = new StringBuilder();
        sb.append(marker).append("\t");
        Trgt.Tuple head = cons.getHeadTuple();
////            Boolean headSign = cons.getHeadSign();
//            if (!headSign)
//                sb.append("NOT ");
        sb.append(prettifyTuple(head));
        for (int j = 0; j < cons.getBodyTupleCount(); j++) {
            sb.append("\t");
            Trgt.Tuple body = cons.getBodyTuple(j);
//                Boolean sign = cons.getBodySign(j);
//                if (!sign) {
//                    sb.append("NOT ");
//                }
            sb.append(prettifyTuple(body));
        }
        return sb.toString();
    }

    public static CausalGraph buildCausalGraph(
            Trgt.Provenance provenance,
            Function<Trgt.Constraint, Categorical01> deriveDist,
            Function<Trgt.Tuple, Categorical01> inputDist
    ) {
        Map<Object, List<Object>> headToConstrs = new LinkedHashMap<>();
        Map<Object, List<Object>> constrToBodies = new LinkedHashMap<>();
        Set<Object> hybrid = new LinkedHashSet<>();
        for (Trgt.Constraint constr : provenance.getConstraintList()) {
            hybrid.add(constr);
            Trgt.Tuple head = constr.getHeadTuple();
            hybrid.add(head);
            headToConstrs.computeIfAbsent(head, t -> new ArrayList<>()).add(constr);
            List<Object> bodyList = new ArrayList<>(constr.getBodyTupleList());
            hybrid.addAll(bodyList);
            constrToBodies.put(constr, bodyList);
        }

        Map<Object, Categorical01> stochMapping = new HashMap<>();

        for (Trgt.Constraint constr : provenance.getConstraintList()) {
            Categorical01 dist = deriveDist.apply(constr);
            if (dist != null)
                stochMapping.put(constr, dist);
        }
        for (Trgt.Tuple inputTuple : provenance.getInputList()) {
            Categorical01 dist = inputDist.apply(inputTuple);
            if (dist != null)
                stochMapping.put(inputTuple, dist);
        }

        List<Object> singletons = new ArrayList<>(provenance.getInputList());
        return CausalGraph.createCausalGraph(provenance.getId(),
                hybrid,
                singletons,
                headToConstrs,
                constrToBodies,
                stochMapping);
    }

    public static CausalGraph buildSqueezedCausalGraph(
            Trgt.Provenance provenance,
            Function<Trgt.Constraint, Categorical01> deriveDist,
            Function<Trgt.Tuple, Categorical01> inputDist,
            Set<Trgt.Tuple> reservedTuples
    ) {
        Set<Trgt.Tuple> reserved = new HashSet<>(reservedTuples);
        Messages.debug("ProvenanceUtil: %d reserved tuples", reserved.size());

        Map<Object, Categorical01> stochMapping = new HashMap<>();
        List<Trgt.Tuple> singletons = new ArrayList<>(provenance.getInputList());
        Map<Trgt.Tuple, Set<Trgt.Constraint>> headToConstrs = new LinkedHashMap<>();
        Map<Trgt.Constraint, Set<Trgt.Tuple>> constrToBodies = new HashMap<>();

        Map<Trgt.Tuple, Integer> bodyToConstrCnt = new HashMap<>();
        Map<Trgt.Tuple, Integer> headToNonEmptyConstrCnt = new HashMap<>();
        Set<Object> origHybrid = new HashSet<>();
        int origParamNum = 0;

        for (Trgt.Constraint constr : provenance.getConstraintList()) {
            Categorical01 dist = deriveDist.apply(constr);
            if (dist != null) {
                stochMapping.put(constr, dist);
                origParamNum += dist.getSupports().length;
            }
        }
        for (Trgt.Tuple inputTuple : provenance.getInputList()) {
            Categorical01 dist = inputDist.apply(inputTuple);
            if (dist != null) {
                stochMapping.put(inputTuple, dist);
                origParamNum += dist.getSupports().length;
            }
        }

        for (Trgt.Constraint constr : provenance.getConstraintList()) {
            origHybrid.add(constr);
            Trgt.Tuple head = constr.getHeadTuple();
            origHybrid.add(head);
            headToConstrs.computeIfAbsent(head, t -> new LinkedHashSet<>()).add(constr);
            Set<Trgt.Tuple> bodyList = new LinkedHashSet<>();
            for (Trgt.Tuple body : constr.getBodyTupleList()) {
                origHybrid.add(body);
                bodyList.add(body);
                bodyToConstrCnt.compute(body, (k, cnt) -> (cnt == null ? 1 : (cnt + 1)));
            }
            constrToBodies.put(constr, bodyList);
        }
        for (var entry : headToConstrs.entrySet()) {
            headToNonEmptyConstrCnt.put(entry.getKey(), entry.getValue().size());
        }
        Messages.log("ProvenanceUtil: original causal graph size [%d causal (head %d + constr %d + input %d) + %d random (%d params)]",
                origHybrid.size(), headToConstrs.size(), constrToBodies.size(), singletons.size(), stochMapping.size(), origParamNum);

        Set<Trgt.Tuple> eliminatables = new LinkedHashSet<>();
        Set<Trgt.Tuple> inputEliminated = new HashSet<>();
        // Step 1 : eliminate from inputs
        for (Trgt.Tuple singleton : singletons) {
            if (!reserved.contains(singleton)) {
                eliminatables.add(singleton);
            }
        }
        Messages.debug("ProvenanceUtil: find %d eliminatable input nodes", eliminatables.size());
        while (!eliminatables.isEmpty()) {
            for (Trgt.Tuple elimTuple : eliminatables) {
                if (elimTuple == null) break;
                boolean newElim = inputEliminated.add(elimTuple);
                if (!newElim) {
                    Messages.error("ProvenanceUtil: input tuple %s has been eliminated before", elimTuple);
                    continue;
                }
                if (inputEliminated.size() <= 10 || inputEliminated.size() % 100 == 0) {
                    Messages.debug("ProvenanceUtil: %d input tuples has been compressed", inputEliminated.size());
                }
                boolean isSingleton = singletons.remove(elimTuple);
                if (!isSingleton)
                    Messages.error("ProvenanceUtil: eliminating non-input tuple %s ?", elimTuple);
                bodyToConstrCnt.remove(elimTuple);
            }
            for (Trgt.Constraint constr : constrToBodies.keySet()) {
                Set<Trgt.Tuple> bodies = constrToBodies.get(constr);
                Set<Trgt.Tuple> elimTuples = new LinkedHashSet<>(bodies);
                elimTuples.retainAll(eliminatables);
                for (Trgt.Tuple elimTuple : elimTuples) {
                    bodies.remove(elimTuple);
                    {
                        Categorical01 pConstr = stochMapping.get(constr);
                        Categorical01 pTuple = stochMapping.get(elimTuple);
                        Categorical01 pNew = Categorical01.multiplyDist(pTuple, pConstr);
                        if (pNew != null) {
                            stochMapping.put(constr, pNew);
                        }
                    }
                }
                if (bodies.isEmpty()) {
                    Trgt.Tuple head = constr.getHeadTuple();
                    headToNonEmptyConstrCnt.computeIfPresent(head, (t, cnt) -> (cnt - 1));
                }
            }
            for (Trgt.Tuple elimTuple : eliminatables) {
                stochMapping.remove(elimTuple);
            }

            Set<Trgt.Tuple> newEliminatables = new LinkedHashSet<>();
            for (Trgt.Tuple head : headToNonEmptyConstrCnt.keySet()) {
                if (headToNonEmptyConstrCnt.getOrDefault(head, 0) == 0) {
                    newEliminatables.add(head);
                }
            }
            for (Trgt.Tuple head : newEliminatables) {
                headToNonEmptyConstrCnt.remove(head);
                Categorical01 pNew = stochMapping.get(head);
                Set<Trgt.Constraint> mergingConstrs = headToConstrs.remove(head);
                for (Trgt.Constraint peerConstr : mergingConstrs) {
                    constrToBodies.remove(peerConstr);
                    Categorical01 pConstr = stochMapping.remove(peerConstr);
                    pNew = Categorical01.revMultiply(pNew, pConstr);
                }
                if (pNew != null)
                    stochMapping.put(head, pNew);
                singletons.add(head);
            }
            eliminatables = newEliminatables;
            eliminatables.removeAll(reserved);
            Messages.debug("ProvenanceUtil: find %d new input tuples to be compressed", eliminatables.size());
        }
        Messages.debug("ProvenanceUtil: compressed %d input tuples", inputEliminated.size());

        // Step 2: eliminate middle nodes
        eliminatables = new LinkedHashSet<>();
        for (Trgt.Tuple head : headToConstrs.keySet()) {
            if (!reserved.contains(head) && headToConstrs.get(head).size() == 1 && bodyToConstrCnt.getOrDefault(head, 0) == 1) {
                eliminatables.add(head);
            }
        }
        Messages.debug("ProvenanceUtil: find %d eliminatable middle nodes", eliminatables.size());
        Set<Trgt.Tuple> middleEliminated = new HashSet<>();
        Set<Trgt.Constraint> origConstrs = new LinkedHashSet<>(constrToBodies.keySet());
        Set<Trgt.Constraint> deletedConstrs = new HashSet<>();
        for (Trgt.Constraint sinkConstr : origConstrs) {
            if (deletedConstrs.contains(sinkConstr)) {
                continue;
            }
            Set<Trgt.Tuple> sinkBodies = constrToBodies.getOrDefault(sinkConstr, Set.of());
            Set<Trgt.Tuple> elimTuples = new LinkedHashSet<>(sinkBodies);
            elimTuples.retainAll(eliminatables);
            while (!elimTuples.isEmpty()) {
                for (Trgt.Tuple elimTuple : elimTuples) {
                    if (elimTuple == null) break;

                    boolean newElim = middleEliminated.add(elimTuple);
                    if (!newElim) {
                        Messages.error("ProvenanceUtil: middle tuple %s has been eliminated before", elimTuple);
                        continue;
                    }
                    if (middleEliminated.size() <= 10 || middleEliminated.size() % 100 == 0) {
                        Messages.debug("ProvenanceUtil: %d middle tuples has been compressed", middleEliminated.size());
                    }
                    Trgt.Constraint srcConstr = headToConstrs.remove(elimTuple).toArray(new Trgt.Constraint[0])[0];
                    Set<Trgt.Tuple> srcBodies = constrToBodies.remove(srcConstr);
                    Categorical01 pSrc = stochMapping.remove(srcConstr);
                    deletedConstrs.add(srcConstr);

                    Categorical01 pSink = stochMapping.get(sinkConstr);
                    sinkBodies.remove(elimTuple);
                    sinkBodies.addAll(srcBodies);
                    Categorical01 pNew = Categorical01.multiplyDist(pSrc, pSink);
                    if (pNew != null)
                        stochMapping.put(sinkConstr, pNew);
                    if (eliminatables.size() <= 10 || eliminatables.size() % 100 == 0)
                        Messages.debug("ProvenanceUtil: %d middle tuples remaining to be compressed", eliminatables.size());
                }
                elimTuples = new LinkedHashSet<>(sinkBodies);
                elimTuples.retainAll(eliminatables);
            }
        }
        Messages.debug("ProvenanceUtil: compressed %d middle tuples in total", middleEliminated.size());

        Set<Object> hybrid = new LinkedHashSet<>();
        Set<Object> newSingletons = new LinkedHashSet<>(singletons);
        Map<Object, List<Object>> newConstrToBodies = new LinkedHashMap<>();
        Map<Object, List<Object>> newHeadToConstrs = new LinkedHashMap<>();
        for (var entry : headToConstrs.entrySet()) {
            newHeadToConstrs.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            hybrid.add(entry.getKey());
            hybrid.addAll(entry.getValue());
            for (var constr : entry.getValue()) {
                Set<Object> bodies = new LinkedHashSet<>(constrToBodies.get(constr));
                newConstrToBodies.put(constr, new ArrayList<>(bodies));
                hybrid.addAll(bodies);
            }
        }
        hybrid.addAll(newSingletons); // in case of lonely nodes
        int sqzParamNum = 0;
        for (Categorical01 dist : stochMapping.values()) {
            sqzParamNum += dist.getSupports().length;
        }
        Messages.log("ProvenanceUtil: squeeze causal graph size to [%d causal + %d random (%d params)]",
                hybrid.size(), stochMapping.size(), sqzParamNum);
        return CausalGraph.createCausalGraph(provenance.getId(),
                hybrid,
                newSingletons,
                newHeadToConstrs,
                newConstrToBodies,
                stochMapping);
    }

    public static Set<Trgt.Tuple> filterTuple(Trgt.Provenance provenance, Collection<String> rels) {
        Set<Trgt.Tuple> tuples = new LinkedHashSet<>();
        for (Trgt.Constraint constr : provenance.getConstraintList()) {
            Trgt.Tuple head = constr.getHeadTuple();
            if (rels.contains(head.getRelName())) {
                tuples.add(head);
            }
            for (Trgt.Tuple body : constr.getBodyTupleList()) {
                if (rels.contains(body.getRelName())) {
                    tuples.add(body);
                }
            }
        }
        return tuples;
    }

    public static Set<Trgt.Tuple> getAllTuple(Trgt.Provenance provenance) {
        return filterTuple(provenance, new HashSet<>());
    }
}
