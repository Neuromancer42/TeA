package provenance;

import chord.analyses.DlogAnalysis;
import chord.analyses.JavaAnalysis;
import chord.analyses.ProgramRel;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.Messages;
import chord.util.Utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

public abstract class ProvenanceDriver extends JavaAnalysis {
    // BDDBDDB configs
    protected List<ITask> tasks;
    private String dlogName;
    private File dlogFile;
    private File confFile;
    private DlogAnalysis dlogAnalysis;

    // Constraint structures
    private List<Tuple> tuples;
    private List<LookUpRule> rules;
    private Set<ConstraintItem> forwardClauses;
    private Set<ConstraintItem> activeClauses;

    // output files
    private Map<LookUpRule, String> ruleIdMap;
    private Map<Tuple, String> tupleIdMap;
    private static String ConsFileName = "cons_all.txt";
    private static String PrunedFileName = "cons_pruned.txt";
    private static String BaseFileName = "base_queries.txt";
    private static String RuleDictFileName = "rule_dict.txt";
    private static String TupleDictFileName = "tuple_dict.txt";

    private void setPath(String name) {
        dlogName = DlogInstrumentor.instrumentName(name);
        try {
            dlogAnalysis = (DlogAnalysis) ClassicProject.g().getTask(dlogName);
        } catch (ClassCastException e) {
            Messages.fatal("Error: Task " + dlogName + " is not a Datalog Analysis!");
        }
        dlogFile = new File(dlogAnalysis.getFileName());
        confFile = new File(dlogFile.getParent(), dlogName+".config");
    }

    public void run() {
        // 0. set names and paths for dlog analysis
        setPath(getDlogName());

        // 1. run instrumented datalog and fetch results
        genTasks();
        for (ITask t : tasks) {
            ClassicProject.g().resetTaskDone(t);
            ClassicProject.g().runTask(t);
        }

        // 2. fetch results and generate dicts
        tuples = getTuples(getRelationNames());
        tupleIdMap = new HashMap<>();
        for (int i = 0; i < tuples.size(); i++) {
            Tuple t = tuples.get(i);
            String tupleId = "T" + Integer.toString(i);
            tupleIdMap.put(t, tupleId);
        }
        rules = getRules();
        ruleIdMap = new HashMap<>();
        for (int i = 0; i < rules.size(); i++) {
            LookUpRule rule = rules.get(i);
            String ruleId = "R" + Integer.toString(i);
            ruleIdMap.put(rule, ruleId);
        }

        // 3. generate provenance structures
        Map<Tuple, Set<ConstraintItem>> tuple2AntecedentClauses = new HashMap<>();
        Map<Tuple, Set<ConstraintItem>> tuple2ConsequentClauses = new HashMap<>();

        for (Tuple tuple : tuples) {
            tuple2AntecedentClauses.put(tuple, new HashSet<>());
            tuple2ConsequentClauses.put(tuple, new HashSet<>());
        }

        for (LookUpRule rule : rules) {
            Iterator<ConstraintItem> iter = rule.getAllConstrIterator();
            while (iter.hasNext()) {
                ConstraintItem cons = iter.next();
                for (Tuple sub : cons.getSubTuples()) {
                    tuple2AntecedentClauses.get(sub).add(cons);
                }
                Tuple head = cons.getHeadTuple();
                tuple2ConsequentClauses.get(head).add(cons);
            }
        }

        // 4. de-cycle and prune unused tuples
        DOBSolver dobSolver = new DOBSolver(tuples, tuple2ConsequentClauses, tuple2AntecedentClauses);
        List<Tuple> outputTuples = getTuples(getOutputRelationNames());
        forwardClauses = dobSolver.getForwardClauses();
        activeClauses = dobSolver.getActiveClauses(outputTuples);

        // 5. dump
        dump();
    }

    private void dump() {
        // 1. dump tuple dictionary
        File tupleDictFile = new File(Config.v().outDirName, TupleDictFileName);
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
        // 2. dump rule dictionary
        File ruleDictFile = new File(Config.v().outDirName, RuleDictFileName);
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
        // 3. dump all constraints
        File consFile = new File(Config.v().outDirName, ConsFileName);
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
        // 4. dump pruned provenance
        File prunedFile = new File(Config.v().outDirName, PrunedFileName);
        try {
            PrintWriter pw = new PrintWriter(prunedFile);
            for (ConstraintItem cons : activeClauses)
                pw.println(encodeClause(cons));
            pw.flush();
            pw.close();
        } catch (FileNotFoundException e) {
            Messages.fatal(e);
        }
        // 5. dump output facts
        File baseFile = new File(Config.v().outDirName, BaseFileName);
        try {
            PrintWriter pw = new PrintWriter(baseFile);
            List<String> qRelNames = getOutputRelationNames();
            for (String qRelName : qRelNames) {
                ProgramRel qRel = (ProgramRel) ClassicProject.g().getTrgt(qRelName);
                qRel.load();
                for (int[] indices : qRel.getAryNIntTuples()) {
                    Tuple t = new Tuple(qRel, indices);
                    pw.println(tupleIdMap.get(t));
                }
            }
            pw.flush();
            pw.close();
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

    protected void genTasks() {
        tasks = new ArrayList<>();
        tasks.add(dlogAnalysis);
    };

    protected List<LookUpRule> getRules() {
        List<LookUpRule> rules = new ArrayList<>();
        try {
            Scanner sc = new Scanner(confFile);
            while (sc.hasNext()) {
                String line = sc.nextLine().trim();
                if (!line.equals("")) {
                    LookUpRule r = new LookUpRule(line);
                    rules.add(r);
                }
            }
            sc.close();
        }  catch (FileNotFoundException e) {
            Messages.fatal(e);
        }
        return rules;
    }

    protected List<Tuple> getTuples(Collection<String> relNames) {
        List<Tuple> tuples = new ArrayList<>();
        for (String relName : relNames) {
            ProgramRel rel = (ProgramRel)  ClassicProject.g().getTrgt(relName);
            rel.load();
            for (int[] vals : rel.getAryNIntTuples()) {
                Tuple t = new Tuple(rel, vals);
                tuples.add(t);
            }
        }
        return tuples;
    }

    // by default, get all (non-instrumenting) relation tuples
    protected List<String> getRelationNames() {
        List<String> relNames = new ArrayList<>();

        // all input relations
        relNames.addAll(dlogAnalysis.getConsumedRels().keySet());

        // all derived and output relations
        for (String relName : dlogAnalysis.getProducedRels().keySet()) {
            if  (!DlogInstrumentor.isInstrumented(relName)) {
                relNames.add(relName);
            }
        }
        return relNames;
    }

    // by default, all output relations are added
    protected List<String> getOutputRelationNames() {
        List<String> outputRelNames = new ArrayList<>();
        String rawDlogName = DlogInstrumentor.uninstrumentName(dlogName);
        DlogAnalysis rawDlogAnalysis = null;
        try {
            rawDlogAnalysis = (DlogAnalysis) ClassicProject.g().getTask(rawDlogName);
            outputRelNames.addAll(rawDlogAnalysis.getProducedRels().keySet());
        } catch (ClassCastException e) {
            Messages.fatal("Error: Task " + rawDlogName + " is not a Datalog Analysis!");
        }
        return outputRelNames;
    }

    protected abstract String getDlogName();

    private class DOBSolver {
        private Map<Tuple, Integer> tupleDOB;
        private int maxDOB;
        private int numChanged;
        private final Map<Tuple, Set<ConstraintItem>> tuple2ConsequentClauses;
        private final Map<Tuple, Set<ConstraintItem>> tuple2AntecedentClauses;
        private Set<ConstraintItem> allClauses;
        private Set<ConstraintItem> fwdClauses;
        private Set<ConstraintItem> augClauses;

        public DOBSolver(
                Collection<Tuple> allTuples,
                Map<Tuple, Set<ConstraintItem>> tuple2ConsequentClauses,
                Map<Tuple, Set<ConstraintItem>> tuple2AntecedentClauses
        ) {
            this.tuple2ConsequentClauses = tuple2ConsequentClauses;
            this.tuple2AntecedentClauses = tuple2AntecedentClauses;
            maxDOB = allTuples.size();
            tupleDOB = new HashMap<>();
            allClauses = new HashSet<>();
            for (Tuple tuple : allTuples) {
                Set<ConstraintItem> cons = tuple2ConsequentClauses.get(tuple);
                if (cons.isEmpty()) {
                    tupleDOB.put(tuple, 0);
                    numChanged++;
                } else {
                    allClauses.addAll(cons);
                    tupleDOB.put(tuple, maxDOB);
                }
            }
        }

        private Integer maxAntecedentDob(ConstraintItem clause) {
            Integer dob = 0;
            for (Tuple sub : clause.getSubTuples()) {
                Integer subDOB = tupleDOB.get(sub);
                if (subDOB > dob) dob = subDOB;
            }
            return dob;
        }

        public void solve() {
            while(numChanged > 0) {
                numChanged = 0;
                for (Tuple head : tuple2ConsequentClauses.keySet()) {
                    for (ConstraintItem clause : tuple2ConsequentClauses.get(head)) {
                        assert head == clause.getHeadTuple();
                        Integer newDOB = maxAntecedentDob(clause);
                        if (newDOB < maxDOB) newDOB++;
                        Integer headDOB = tupleDOB.get(head);
                        if (newDOB < headDOB) {
                            numChanged++;
                            tupleDOB.put(head, newDOB);
                        }
                    }
                }
            }
        }

        public Map<Tuple, Integer> getDOB() {
            if (numChanged > 0) solve();
            return tupleDOB;
        }

        private void computeFwdClauses() {
            if (numChanged > 0) solve();
            fwdClauses = new HashSet<>();
            for (Tuple head : tuple2ConsequentClauses.keySet()) {
                for (ConstraintItem clause : tuple2ConsequentClauses.get(head)) {
                    assert head == clause.getHeadTuple();
                    // erroneous de-cycle, reserves only earliest clauses
                    if (tupleDOB.get(head) > maxAntecedentDob(clause)) {
                        fwdClauses.add(clause);
                    }
                }
            }
            Messages.log("Forward clauses found " + fwdClauses.size());
        }

        // binary search to enlarge the non-circular provenances
        private void augmentFromCandidates(List<ConstraintItem> candidateClauses) {
            int tot = candidateClauses.size();
            if (tot == 0) {
                Messages.log("No candidates to augment forward clauses.");
            } else if (isAncestorDescendantDisjoint(candidateClauses)) {
                augClauses.addAll(candidateClauses);
                Messages.log("Forward clauses augmented " + candidateClauses.size());
            } else if (tot == 1) {
                Messages.log("Backward clause found " + candidateClauses.get(0).toString());
            } else {
                int mid = tot / 2;
                augmentFromCandidates(candidateClauses.subList(0, mid));
                augmentFromCandidates(candidateClauses.subList(mid, tot));
            }
        }

        // check non-circularity
        private boolean isAncestorDescendantDisjoint(Collection<ConstraintItem> roots) {
            Set<Tuple> ancestors = new HashSet<>();
            Set<Tuple> descendants = new HashSet<>();
            for (ConstraintItem clause : roots) {
                ancestors.addAll(clause.getSubTuples());
                descendants.add(clause.getHeadTuple());
            }
            Queue<Tuple> newAncestors = new LinkedList<>(ancestors);
            Queue<Tuple> newDescendants = new LinkedList<>(descendants);
            while (!newAncestors.isEmpty() && !newDescendants.isEmpty()) {
                if (!newAncestors.isEmpty() && ancestors.size() < descendants.size()) {
                    Tuple newAncestor = newAncestors.poll();
                    for (ConstraintItem cons : tuple2ConsequentClauses.get(newAncestor)) {
                        if (augClauses.contains(cons)) {
                            for (Tuple sub : cons.getSubTuples()) {
                                if (descendants.contains(sub))
                                    return false;
                                if (ancestors.add(sub))
                                    newAncestors.offer(sub);
                            }
                        }
                    }
                } else {
                    Tuple newDescendant = newDescendants.poll();
                    for (ConstraintItem ante : tuple2AntecedentClauses.get(newDescendant)) {
                        if (augClauses.contains(ante)) {
                            Tuple head = ante.getHeadTuple();
                            if (ancestors.contains(head))
                                return false;
                            if (descendants.add(head))
                                newDescendants.offer(head);
                        }
                    }
                }
            }
            return true;
        }

        public Set<Tuple> getCoreachableTuples(Collection<Tuple> outputTuples) {
            if (fwdClauses == null) computeFwdClauses();

            Set<Tuple> coreachableTuples = new HashSet<>(outputTuples);
            Queue<Tuple> worklist = new LinkedList<>(outputTuples);
            while (!worklist.isEmpty()) {
                Tuple head = worklist.poll();
                if (tuple2ConsequentClauses.containsKey(head)) {
                    for (ConstraintItem clause : tuple2ConsequentClauses.get(head)) {
                        if (fwdClauses.contains(clause)) {
                            for (Tuple sub : clause.getSubTuples()) {
                                if (coreachableTuples.add(sub))
                                    worklist.offer(sub);
                            }
                        }
                    }
                }
            }
            return coreachableTuples;
        }

        public Set<ConstraintItem> getForwardClauses() {
            if (fwdClauses == null) computeFwdClauses();
            return fwdClauses;
        }

        public Set<ConstraintItem> getAugmentedClauses() {
            if (augClauses == null) {
                augClauses = new HashSet<>(getForwardClauses());
                List<ConstraintItem> cands = new ArrayList<>();
                for (ConstraintItem cons : allClauses) {
                    if (!augClauses.contains(cons))
                        cands.add(cons);
                }
                Messages.log("Forward clauses augment candidates " + cands.size());
                augmentFromCandidates(cands);
            }
            return augClauses;
        }

        public Set<ConstraintItem> getActiveClauses(Collection<Tuple> outputTuples) {
            Set<Tuple> coreachableTuples = getCoreachableTuples(outputTuples);

            Set<ConstraintItem> activeClauses = new HashSet<>();
            Set<ConstraintItem> fwdClauses;
            boolean doAugment = Utils.buildBoolProperty("chord.dlog.provenance.augment", true);
            if (doAugment)
                fwdClauses = getAugmentedClauses();
            else
                fwdClauses = getForwardClauses();
            for (ConstraintItem clause : fwdClauses) {
                if (coreachableTuples.contains(clause.getHeadTuple())) {
                    activeClauses.add(clause);
                }
            }
            return activeClauses;
        }

    }
}