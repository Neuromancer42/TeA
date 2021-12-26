package provenance;

import chord.analyses.ProgramRel;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.Messages;
import chord.util.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

public class Provenance {
    // Constraint structures
    private final List<Tuple> tuples;
    private final List<LookUpRule> rules;
    private final Set<ConstraintItem> activeClauses;
    private final Map<LookUpRule, String> ruleIdMap;
    private final Map<Tuple, String> tupleIdMap;
    private final List<Tuple> inputTuples;
    private final List<Tuple> outputTuples;

    public Provenance(
            List<LookUpRule> rules, List<Tuple> tuples,
            List<Tuple> inputTuples, List<Tuple> outputTuples,
            Set<ConstraintItem> activeClauses
    ) {
        this.tuples = tuples;
        this.inputTuples = inputTuples;
        this.outputTuples = outputTuples;
        tupleIdMap = new HashMap<>();
        for (int i = 0; i < tuples.size(); i++) {
            Tuple t = tuples.get(i);
            String tupleId = "T" + Integer.toString(i);
            tupleIdMap.put(t, tupleId);
        }
        this.rules = rules;
        ruleIdMap = new HashMap<>();
        for (int i = 0; i < rules.size(); i++) {
            LookUpRule rule = rules.get(i);
            String ruleId = "R" + Integer.toString(i);
            ruleIdMap.put(rule, ruleId);
        }
        this.activeClauses = activeClauses;
    }

    public void dump(String dir) {
        // dump tuple dictionary
        String tupleDictFile = dir + File.separator + "tuple_dict.txt";
        PrintWriter dw = Utils.openOut(tupleDictFile);
        for (Tuple t : tuples) {
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
        for (ConstraintItem cons : activeClauses)
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
        String ruleId = ruleIdMap.get(cons.getRule());
        sb.append(ruleId + ": ");
        for (int j = 0; j < cons.getSubTuples().size(); j++) {
            Tuple sub = cons.getSubTuples().get(j);
            Boolean sign = cons.getSubTuplesSign().get(j);
            if (sign) {
                sb.append("NOT ");
            }
            sb.append(tupleIdMap.get(sub));
            sb.append(", ");
        }
        Tuple head = cons.getHeadTuple();
        Boolean headSign = cons.getHeadTupleSign();
        if (!headSign) {
            sb.append("NOT ");
        }
        sb.append(tupleIdMap.get(head));
        return sb.toString();
    }
}
