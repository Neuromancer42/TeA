package provenance;

import chord.util.Utils;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public class Provenance {
    // Constraint structures
    private final List<LookUpRule> rules;
    private final List<ConstraintItem> clauses;
    private final Map<LookUpRule, String> ruleIdMap;
    private final Map<Tuple, String> tupleIdMap;
    private final Map<ConstraintItem, String> clauseIdMap;
    private final List<Tuple> inputTuples;
    private final List<Tuple> outputTuples;
    private final List<Tuple> hiddenTuples;

    public Provenance(
            Collection<LookUpRule> rules, Collection<Tuple> tuples,
            Collection<Tuple> inputTuples, Collection<Tuple> outputTuples,
            Collection<ConstraintItem> clauses
    ) {
        this.inputTuples = new ArrayList<>(inputTuples);
        this.outputTuples = new ArrayList<>(outputTuples);
        this.hiddenTuples = new ArrayList<>(tuples);
        this.hiddenTuples.removeAll(inputTuples);
        this.hiddenTuples.removeAll(outputTuples);
        tupleIdMap = new HashMap<>();
        for (int i = 0; i < this.inputTuples.size(); i++) {
            Tuple t = this.inputTuples.get(i);
            String tupleId = "I" + i;
            tupleIdMap.put(t, tupleId);
        }
        for (int i = 0; i < this.outputTuples.size(); i++) {
            Tuple t = this.outputTuples.get(i);
            String tupleId = "O" + i;
            tupleIdMap.put(t, tupleId);
        }
        for (int i = 0; i < this.hiddenTuples.size(); i++) {
            Tuple t = this.hiddenTuples.get(i);
            String tupleId = "H" + i;
            tupleIdMap.put(t, tupleId);
        }
        this.rules = new ArrayList<>(rules);
        ruleIdMap = new HashMap<>();
        for (int i = 0; i < this.rules.size(); i++) {
            LookUpRule rule = this.rules.get(i);
            String ruleId = "R" + i;
            ruleIdMap.put(rule, ruleId);
        }
        this.clauses = new ArrayList<>(clauses);
        clauseIdMap = new HashMap<>();
        for (int i = 0; i < this.clauses.size(); i++) {
            ConstraintItem clause = this.clauses.get(i);
            String clauseId = "D" + i;
            clauseIdMap.put(clause, clauseId);
        }
    }

    public void dump(String dir) {
        // dump tuple dictionary
        String tupleDictFile = dir + File.separator + "tuple_dict.txt";
        PrintWriter dw = Utils.openOut(tupleDictFile);
        for (Tuple t : inputTuples) {
            String tupleId = tupleIdMap.get(t);
            dw.println(tupleId + ": " + t.toSummaryString(","));
            tupleIdMap.put(t, tupleId);
        }
        for (Tuple t : hiddenTuples) {
            String tupleId = tupleIdMap.get(t);
            dw.println(tupleId + ": " + t.toSummaryString(","));
            tupleIdMap.put(t, tupleId);
        }
        for (Tuple t : outputTuples) {
            String tupleId = tupleIdMap.get(t);
            dw.println(tupleId + ": " + t.toSummaryString(","));
            tupleIdMap.put(t, tupleId);
        }
        dw.flush();
        dw.close();
        // dump rule dictionary
        String ruleDictFile = dir + File.separator + "rule_dict.txt";
        PrintWriter rdw = Utils.openOut(ruleDictFile);
        for (LookUpRule rule : rules) {
            String ruleId = ruleIdMap.get(rule);
            rdw.println(ruleId + ": " + rule.toString());
            ruleIdMap.put(rule, ruleId);
        }
        rdw.flush();
        rdw.close();
        // dump all constraints
        String consFile = dir + File.separator + "cons_all.txt";
        PrintWriter cw = Utils.openOut(consFile);
        for (LookUpRule rule : rules) {
            Iterator<ConstraintItem> iter = rule.getAllConstrIterator();
            while (iter.hasNext()) {
                ConstraintItem cons = iter.next();
                cw.println(encodeClause(cons));
            }
        }
        cw.flush();
        cw.close();
        // dump pruned provenance
        String prunedFile =  dir + File.separator + "cons_pruned.txt";
        PrintWriter pw = Utils.openOut(prunedFile);
        for (ConstraintItem cons : clauses)
            pw.println(encodeClause(cons));
        pw.flush();
        pw.close();
        // dump output facts
        String baseFile = dir + File.separator + "base_queries.txt";
        PrintWriter qw = Utils.openOut(baseFile);
        for (Tuple q : outputTuples)
            qw.println(tupleIdMap.get(q));
        qw.flush();
        qw.close();
    }

    private String encodeClause(ConstraintItem cons) {
        StringBuilder sb = new StringBuilder();
        String clauseId = clauseIdMap.get(cons);
        sb.append(clauseId +  " : ");
        String ruleId = ruleIdMap.get(cons.getRule());
        sb.append(ruleId + " - ");
        for (int j = 0; j < cons.getSubTuples().size(); j++) {
            Tuple sub = cons.getSubTuples().get(j);
            Boolean sign = cons.getSubTuplesSign().get(j);
            if (sign) {
                sb.append("NOT ");
            }
            sb.append(tupleIdMap.get(sub));
            sb.append(" , ");
        }
        Tuple head = cons.getHeadTuple();
        Boolean headSign = cons.getHeadTupleSign();
        if (!headSign) {
            sb.append("NOT ");
        }
        sb.append(tupleIdMap.get(head));
        return sb.toString();
    }

    public List<Tuple> getInputTuples() {
        return inputTuples;
    }

    public List<Tuple> getOutputTuples() {
        return outputTuples;
    }

    public List<Tuple> getHiddenTuples() {
        return hiddenTuples;
    }

    public List<ConstraintItem> getClauses() {
        return clauses;
    }
}
