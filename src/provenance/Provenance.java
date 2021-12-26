package provenance;

import chord.analyses.ProgramRel;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.Messages;

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
        File tupleDictFile = new File(dir, "tuple_dict.txt");
        try {
            PrintWriter dw = new PrintWriter(tupleDictFile);
            for (Tuple t : tuples) {
                String tupleId = tupleIdMap.get(t);
                dw.println(tupleId + ": " + t.toSummaryString(","));
                tupleIdMap.put(t, tupleId);
            }
            dw.flush();
            dw.close();
        } catch (FileNotFoundException e) {
            Messages.fatal(e);
        }
        // dump rule dictionary
        File ruleDictFile = new File(dir, "rule_dict.txt");
        try {
            PrintWriter rdw = new PrintWriter(ruleDictFile);
            for (LookUpRule rule : rules) {
                String ruleId = ruleIdMap.get(rule);
                rdw.println(ruleId + ": " + rule.toString());
                ruleIdMap.put(rule, ruleId);
            }
            rdw.flush();
            rdw.close();
        } catch (FileNotFoundException e) {
            Messages.fatal(e);
        }
        // dump all constraints
        File consFile = new File(dir, "cons_all.txt");
        try {
            PrintWriter cw = new PrintWriter(consFile);
            for (LookUpRule rule : rules) {
                Iterator<ConstraintItem> iter = rule.getAllConstrIterator();
                while (iter.hasNext()) {
                    ConstraintItem cons = iter.next();
                    cw.println(encodeClause(cons));
                }
            }
            cw.flush();
            cw.close();
        } catch (FileNotFoundException e) {
            Messages.fatal(e);
        }
        // dump pruned provenance
        File prunedFile = new File(dir, "cons_pruned.txt");
        try {
            PrintWriter pw = new PrintWriter(prunedFile);
            for (ConstraintItem cons : activeClauses)
                pw.println(encodeClause(cons));
            pw.flush();
            pw.close();
        } catch (FileNotFoundException e) {
            Messages.fatal(e);
        }
        // dump output facts
        File baseFile = new File(dir, "base_queries.txt");
        try {
            PrintWriter qw = new PrintWriter(baseFile);
            for (Tuple q : outputTuples)
                qw.println(tupleIdMap.get(q));
            qw.flush();
            qw.close();
        } catch (FileNotFoundException e) {
            Messages.fatal(e);
        }
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
