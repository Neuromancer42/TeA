package com.neuromancer42.tea.commons.provenance;

import com.google.protobuf.*;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.inference.Categorical01;
import com.neuromancer42.tea.commons.inference.CausalGraph;
import com.neuromancer42.tea.commons.util.IndexMap;
import com.neuromancer42.tea.core.analysis.Trgt;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;

public class ProvenanceUtil {
    private ProvenanceUtil() {};

    public static boolean dumpProvenance(Trgt.Provenance prov, Path path) {
        IndexMap<String> rules = new IndexMap<>();
        // dump pruned provenance
        try {
            Path consPath = path.resolve("cons_pruned.txt");
            List<String> consLines = new ArrayList<>();
            for (int clsId = 0; clsId < prov.getConstraintCount(); ++clsId) {
                Trgt.Constraint cons = prov.getConstraint(clsId);
                rules.add(cons.getRuleInfo());
                int ruleId = rules.indexOf(cons.getRuleInfo());
                StringBuilder sb = new StringBuilder();
                sb.append("R").append(ruleId).append("-").append(clsId).append(": ");
                Trgt.Tuple head = cons.getHeadTuple();
////            Boolean headSign = cons.getHeadSign();
//            if (!headSign)
//                sb.append("NOT ");
                sb.append(prettifyTuple(head));
                sb.append("=");
                for (int j = 0; j < cons.getBodyTupleCount(); j++) {
                    if (j > 0) sb.append(",");
                    Trgt.Tuple body = cons.getBodyTuple(j);
//                Boolean sign = cons.getBodySign(j);
//                if (!sign) {
//                    sb.append("NOT ");
//                }
                    sb.append(prettifyTuple(body));
                }
                consLines.add(sb.toString());
            }
            Files.write(consPath, consLines, StandardCharsets.UTF_8);
            // dump rule dictionary
            Path ruleDictPath = path.resolve("rule_dict.txt");
            List<String> dictLines = new ArrayList<>();
            for (int i = 0; i < rules.size(); ++i) {
                String ruleId = "R" + i;
                dictLines.add(ruleId + ":\t" + rules.get(i));
            }
            Files.write(ruleDictPath, dictLines, StandardCharsets.UTF_8);
            // dump output facts
            Path baseQueryPath = path.resolve("base_queries.txt");
            List<String> baseQueryLines = new ArrayList<>();
            for (Trgt.Tuple q : prov.getOutputList())
                baseQueryLines.add(prettifyTuple(q));
            Files.write(baseQueryPath, baseQueryLines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Messages.error("ProvenanceUtil: failed to dump provenance file, skip: %s", e.getMessage());
            return false;
        }
        return true;
    }

    public static String prettifyTuple(Trgt.Tuple tuple) {
        StringBuilder sb = new StringBuilder();
        sb.append(tuple.getRelName()).append("(");
        for (int i = 0; i < tuple.getAttributeCount(); ++i) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(tuple.getAttribute(i));
        }
        sb.append(")");
        return sb.toString();
    }

    public static String prettifyConstraint(Trgt.Constraint constraint) {
        StringBuilder sb = new StringBuilder();
        sb.append(prettifyTuple(constraint.getHeadTuple()));
        sb.append(":-");
        for (int i = 0; i < constraint.getBodyTupleCount(); ++i) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(constraint.getBodyTuple(i));
        }
        sb.append(")");
        return sb.toString();
    }

    public static String prettifyProbability(Double prob) {
        return String.format("%.6g%%", prob * 100);
    }

    public static String encodeTuple(Trgt.Tuple tuple) {
        return Base64.getEncoder().encodeToString(tuple.toByteArray());
    }

    public static String encodeConstraint(Trgt.Constraint constr) {
        return Base64.getEncoder().encodeToString(constr.toByteArray());
    }

    public static CausalGraph buildCausalGraph(
            Trgt.Provenance provenance,
            Function<Trgt.Constraint, Categorical01> deriveDist,
            Function<Trgt.Tuple, Categorical01> inputDist
    ) {
        Map<String, List<String>> headToConstrs = new LinkedHashMap<>();
        Map<String, List<String>> constrToBodies = new LinkedHashMap<>();
        Set<String> hybrid = new LinkedHashSet<>();
        for (Trgt.Constraint constr : provenance.getConstraintList()) {
            String constrRepr = encodeConstraint(constr);
            hybrid.add(constrRepr);
            String headRepr = encodeTuple(constr.getHeadTuple());
            hybrid.add(headRepr);
            headToConstrs.computeIfAbsent(headRepr, t -> new ArrayList<>()).add(constrRepr);
            List<String> bodyReprList = new ArrayList<>();
            for (Trgt.Tuple body : constr.getBodyTupleList()) {
                bodyReprList.add(encodeTuple(body));
            }
            hybrid.addAll(bodyReprList);
            constrToBodies.put(constrRepr, bodyReprList);
        }

        Map<String, Categorical01> stochMapping = new HashMap<>();

        for (Trgt.Constraint constr : provenance.getConstraintList()) {
            Categorical01 dist = deriveDist.apply(constr);
            if (dist != null)
                stochMapping.put(encodeConstraint(constr), dist);
        }
        for (Trgt.Tuple inputTuple : provenance.getInputList()) {
            Categorical01 dist = inputDist.apply(inputTuple);
            if (dist != null)
                stochMapping.put(encodeTuple(inputTuple), dist);
        }

        List<String> singletons = new ArrayList<>();
        for (Trgt.Tuple tuple : provenance.getInputList()) {
            singletons.add(encodeTuple(tuple));
        }
        return CausalGraph.createCausalGraph(provenance.getId(),
                hybrid,
                singletons,
                headToConstrs,
                constrToBodies,
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
