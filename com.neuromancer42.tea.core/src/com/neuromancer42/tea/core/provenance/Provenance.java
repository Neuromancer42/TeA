package com.neuromancer42.tea.core.provenance;

import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.util.Utils;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public class Provenance {
    // Constraint structures
    // TODO: optimize de-duplicate operations

    private final List<ConstraintItem> clauses;
    private final List<Tuple> inputTuples;
    private final List<Tuple> outputTuples;
    private final List<Tuple> hiddenTuples;
    private Map<ConstraintItem, String> clauseIdMap;
    private Map<Tuple, String> tupleIdMap;

    public Provenance(
            Collection<Tuple> tuples,
            Collection<Tuple> inputTuples, Collection<Tuple> outputTuples,
            Collection<ConstraintItem> clauses
    ) {
        this.inputTuples = new ArrayList<>(inputTuples.size());
        this.inputTuples.addAll(inputTuples);
        this.outputTuples = new ArrayList<>(outputTuples.size());
        this.outputTuples.addAll(outputTuples);
        List<Tuple> hiddenTuples = new LinkedList<>(tuples);
        hiddenTuples.removeAll(inputTuples);
        hiddenTuples.removeAll(outputTuples);
        this.hiddenTuples = new ArrayList<>(hiddenTuples.size());
        this.hiddenTuples.addAll(hiddenTuples);

        this.clauses = new ArrayList<>(clauses.size());
        this.clauses.addAll(clauses);

        clauseIdMap = new HashMap<>();
        for (int idx = 0; idx < this.clauses.size(); idx++)
            clauseIdMap.put(this.clauses.get(idx), "D" + idx);
        tupleIdMap = new HashMap<>();
        for (int idx = 0; idx < this.inputTuples.size(); idx++)
            tupleIdMap.put(this.inputTuples.get(idx), "I" + idx);
        for (int idx = 0; idx < this.outputTuples.size(); idx++)
            tupleIdMap.put(this.outputTuples.get(idx), "O" + idx);
        for (int idx = 0; idx < this.hiddenTuples.size(); idx++)
            tupleIdMap.put(this.hiddenTuples.get(idx), "H" + idx);
    }

    public void dump(String dir) {
        // dump tuple dictionary
        String tupleDictFile = dir + File.separator + "tuple_dict.txt";
        PrintWriter dw = Utils.openOut(tupleDictFile);
        for (Tuple t : inputTuples) {
            String tupleId = encodeTuple(t);
            dw.println(tupleId + ":\t" + t.toString());
        }
        for (Tuple t : hiddenTuples) {
            String tupleId = encodeTuple(t);
            dw.println(tupleId + ":\t" + t.toString());
        }
        for (Tuple t : outputTuples) {
            String tupleId = encodeTuple(t);
            dw.println(tupleId + ":\t" + t.toString());
        }
        dw.flush();
        dw.close();
//        // dump rule dictionary
//        String ruleDictFile = dir + File.separator + "rule_dict.txt";
//        PrintWriter rdw = Utils.openOut(ruleDictFile);
//        for (String ruleInfo : ruleInfos) {
//            String ruleId = "R" + ruleInfos.indexOf(ruleInfo);
//            rdw.println(ruleId + ":\t" + ruleInfo);
//        }
//        rdw.flush();
//        rdw.close();
        // dump pruned provenance
        String prunedFile = dir + File.separator + "cons_pruned.txt";
        PrintWriter pw = Utils.openOut(prunedFile);
        for (ConstraintItem cons : clauses)
            pw.println(getClauseDetail(cons));
        pw.flush();
        pw.close();
        // dump output facts
        String baseFile = dir + File.separator + "base_queries.txt";
        PrintWriter qw = Utils.openOut(baseFile);
        for (Tuple q : outputTuples)
            qw.println(encodeTuple(q));
        qw.flush();
        qw.close();
    }

    private String getClauseDetail(ConstraintItem cons) {
        StringBuilder sb = new StringBuilder();
        sb.append("R" + cons.getRuleInfo() + "-");
        String clauseId = encodeClause(cons);
        sb.append(clauseId +  " : ");
        Tuple head = cons.getHeadTuple();
        Boolean headSign = cons.getHeadTupleSign();
        if (!headSign)
            sb.append("NOT ");
        sb.append(encodeTuple(head));
        sb.append("=");
        for (int j = 0; j < cons.getSubTuples().size(); j++) {
            if (j > 0) sb.append(",");
            Tuple sub = cons.getSubTuples().get(j);
            Boolean sign = cons.getSubTuplesSign().get(j);
            if (!sign) {
                sb.append("NOT ");
            }
            sb.append(encodeTuple(sub));
        }
        return sb.toString();
    }

    private String encodeClause(ConstraintItem clause) {
        return clauseIdMap.get(clause);
    }

    public ConstraintItem decodeClause(String name) {
        char tag = name.charAt(0);
        if (tag == 'D') {
            int idx = Integer.parseInt(name.substring(1));
            return clauses.get(idx);
        } else {
            Messages.error("Provenance: unmet clause id " + name);
            return null;
        }
    }

    private String encodeTuple(Tuple t)  {
        return tupleIdMap.get(t);
    }

    public Tuple decodeTuple(String name) {
        char tag = name.charAt(0);
        int idx = Integer.parseUnsignedInt(name.substring(1));
        if (tag == 'I') {
            return inputTuples.get(idx);
        } else if (tag == 'O') {
            return outputTuples.get(idx);
        } else if (tag == 'H') {
            return hiddenTuples.get(idx);
        } else {
            Messages.error("Provenance: unmet tuple id " + name);
            return null;
        }
    }

    public List<ConstraintItem> getClauses() { return clauses; }

    public List<Tuple> getInputTuples() { return inputTuples; }

    public List<Tuple> getOutputTuples() { return outputTuples; }

    public List<Tuple> getHiddenTuples() { return hiddenTuples; }

    public List<String> getClauseIds() {
        List<String> l = new ArrayList<>(clauses.size());
        for (ConstraintItem clause : clauses)
            l.add(encodeClause(clause));
        return l;
    }

    public List<String> getInputTupleIds() {
        List<String> l = new ArrayList<>(inputTuples.size());
        for (Tuple t : inputTuples)
            l.add(encodeTuple(t));
        return l;
    }

    public List<String> getOutputTupleIds() {
        List<String> l = new ArrayList<>(outputTuples.size());
        for (Tuple t : outputTuples)
            l.add(encodeTuple(t));
        return l;
    }

    public List<String> getHiddenTupleIds() {
        List<String> l = new ArrayList<>( hiddenTuples.size());
        for (Tuple t : hiddenTuples)
            l.add(encodeTuple(t));
        return l;
    }

    public Map<String, Set<String>> getHeadtuple2ClausesMap() {
        Map<String, Set<String>> tuple2Clauses = new HashMap<>();
        for (ConstraintItem clause : clauses) {
            String head = encodeTuple(clause.getHeadTuple());
            tuple2Clauses.putIfAbsent(head, new HashSet<>());
            tuple2Clauses.get(head).add(encodeClause(clause));
        }
        return tuple2Clauses;
    }

    public Map<String, Set<String>> getClause2SubtuplesMap() {
        Map<String, Set<String>> clause2Tuples = new HashMap<>();
        for (ConstraintItem clause : clauses) {
            String cid = encodeClause(clause);
            clause2Tuples.put(cid, new HashSet<>());
            for (Tuple sub : clause.getSubTuples()) {
                clause2Tuples.get(cid).add(encodeTuple(sub));
            }
        }
        return clause2Tuples;
    }

    public String unfoldId(String name) {
        char tag = name.charAt(0);
        if (tag == 'I' || tag == 'O' || tag == 'H')
            return name + ": " + decodeTuple(name).toString();
        if (tag == 'D')
            return getClauseDetail(decodeClause(name));
        if (tag == 'R') {
            return name;
        }
        Messages.error("Provenance: unmet id " + name);
        return null;
    }
}
