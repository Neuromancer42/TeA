package provenance;

import chord.util.IndexMap;
import chord.util.Utils;

import java.io.File;
import java.io.PrintWriter;
import java.util.*;

public class Provenance {
    // Constraint structures
    private final IndexMap<LookUpRule> rules;
    private final IndexMap<ConstraintItem> clauses;
    private final IndexMap<Tuple> inputTuples;
    private final IndexMap<Tuple> outputTuples;
    private final IndexMap<Tuple> hiddenTuples;

    public Provenance(
            Collection<LookUpRule> rules, Collection<Tuple> tuples,
            Collection<Tuple> inputTuples, Collection<Tuple> outputTuples,
            Collection<ConstraintItem> clauses
    ) {
        this.inputTuples = new IndexMap<>(inputTuples.size());
        this.inputTuples.addAll(inputTuples);
        this.outputTuples = new IndexMap<>(outputTuples.size());
        this.outputTuples.addAll(outputTuples);
        List<Tuple> hiddenTuples = new LinkedList<>(tuples);
        hiddenTuples.removeAll(inputTuples);
        hiddenTuples.removeAll(outputTuples);
        this.hiddenTuples = new IndexMap<>(hiddenTuples.size());
        this.hiddenTuples.addAll(hiddenTuples);

        this.rules = new IndexMap<>(rules.size());
        this.rules.addAll(rules);
        this.clauses = new IndexMap<>(clauses.size());
        this.clauses.addAll(clauses);
    }

    public void dump(String dir) {
        // dump tuple dictionary
        String tupleDictFile = dir + File.separator + "tuple_dict.txt";
        PrintWriter dw = Utils.openOut(tupleDictFile);
        for (Tuple t : inputTuples) {
            String tupleId = "I" + inputTuples.indexOf(t);
            dw.println(tupleId + ":\t" + t.toVerboseString());
        }
        for (Tuple t : hiddenTuples) {
            String tupleId = "H" + hiddenTuples.indexOf(t);
            dw.println(tupleId + ":\t" + t.toVerboseString());
        }
        for (Tuple t : outputTuples) {
            String tupleId = "O"  + outputTuples.indexOf(t);
            dw.println(tupleId + ":\t" + t.toVerboseString());
        }
        dw.flush();
        dw.close();
        // dump rule dictionary
        String ruleDictFile = dir + File.separator + "rule_dict.txt";
        PrintWriter rdw = Utils.openOut(ruleDictFile);
        for (LookUpRule rule : rules) {
            String ruleId = "R" + rules.indexOf(rule);
            rdw.println(ruleId + ":\t" + rule.toString());
        }
        rdw.flush();
        rdw.close();
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
            qw.println("O" + outputTuples.indexOf(q));
        qw.flush();
        qw.close();
    }

    private String encodeClause(ConstraintItem cons) {
        StringBuilder sb = new StringBuilder();
        String clauseId = "D" + clauses.indexOf(cons);
        sb.append(clauseId +  " : ");
        String ruleId = "R" + rules.indexOf(cons.getRule());
        sb.append(ruleId + " - ");
        for (int j = 0; j < cons.getSubTuples().size(); j++) {
            Tuple sub = cons.getSubTuples().get(j);
            Boolean sign = cons.getSubTuplesSign().get(j);
            if (sign) {
                sb.append("NOT ");
            }
            sb.append(encodeTuple(sub));
            sb.append(" , ");
        }
        Tuple head = cons.getHeadTuple();
        Boolean headSign = cons.getHeadTupleSign();
        if (!headSign) {
            sb.append("NOT ");
        }
        sb.append(encodeTuple(head));
        return sb.toString();
    }

    private String encodeTuple(Tuple t)  {
        int id = inputTuples.indexOf(t);
        if (id >= 0)
            return "I" + inputTuples.indexOf(t);
        else
            id = outputTuples.indexOf(t);
        if (id >= 0)
            return "O" + outputTuples.indexOf(t);
        else
            id = hiddenTuples.indexOf(t);
        if (id >= 0)
            return "H" + hiddenTuples.indexOf(t);
        else
            return null;
    }
}
