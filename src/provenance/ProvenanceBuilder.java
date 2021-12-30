package provenance;

import chord.analyses.DlogAnalysis;
import chord.analyses.ProgramRel;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.Messages;
import chord.util.Utils;
import chord.util.Timer;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

public class ProvenanceBuilder {
    // BDDBDDB configs
    protected String dlogName;
    protected File dlogFile;
    protected File confFile;
    protected DlogAnalysis dlogAnalysis;
    protected DlogAnalysis rawDlogAnalysis;
    private boolean activated = false;

    // Provenance Structure
    private Provenance provenance;

    public ProvenanceBuilder(String dlogName) {
        this.dlogName = DlogInstrumentor.instrumentName(dlogName);
    }

    private void setPath() {
        try {
            dlogAnalysis = (DlogAnalysis) ClassicProject.g().getTask(dlogName);
        } catch (ClassCastException e) {
            Messages.fatal("Error: Task " + dlogName + " is not a Datalog Analysis!");
        }
        try  {
            String rawDlogName = DlogInstrumentor.uninstrumentName(dlogName);
            rawDlogAnalysis = (DlogAnalysis) ClassicProject.g().getTask(rawDlogName);
        } catch (ClassCastException e) {
            Messages.fatal("Error: Original task of " + dlogName + " is not a Datalog Analysis!");
        }
        dlogFile = new File(dlogAnalysis.getFileName());
        confFile = new File(dlogFile.getParent(), dlogName+".config");
    }

    public Provenance getProvenance() {
        if (provenance == null) computeProvenance();
        return provenance;
    }

    protected void computeProvenance() {
        computeProvenance(getOutputRelationNames());
    }

    public void computeProvenance(Collection<String> observeRelationNames) {
        Timer timer = new Timer("provenance-builder");
        if (Config.v().verbose >= 1)
            System.out.println("ENTER: provenance-builder at " + (new Date()));
        timer.init();

        // generate mappings
        timer.pause();
        if (!activated) {
            activateDlog();
            activated = true;
        }
        timer.resume();

        // fetch results and generate dicts
        List<LookUpRule> rules = getRules();
        Set<Tuple> tuples = new HashSet<>();
        for (LookUpRule r : rules) {
            Iterator<ConstraintItem> iter = r.getAllConstrIterator();
            while (iter.hasNext()) {
                ConstraintItem cons = iter.next();
                tuples.add(cons.getHeadTuple());
                tuples.addAll(cons.getSubTuples());
            }
        }
        List<Tuple> inputTuples = getTuples(getInputRelationNames(), tuples);
        List<Tuple> outputTuples = getTuples(getOutputRelationNames()); // output must have corresponding rules
        Messages.log("ProvenanceBuilder recorded " + rules.size() + " rules and " + tuples.size() + " tuples.");

        // generate provenance structures
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

        // de-cycle and prune unused clauses
        DOBSolver dobSolver = new DOBSolver(tuples, inputTuples, tuple2ConsequentClauses, tuple2AntecedentClauses);
        List<Tuple> observeTuples = getTuples(observeRelationNames);
        Set<ConstraintItem> activeClauses = dobSolver.getActiveClauses(observeTuples);

        // filter out unused tuples again
        Set<Tuple> activeTuples = new HashSet<>();
        for (ConstraintItem clause : activeClauses) {
            activeTuples.add(clause.getHeadTuple());
            activeTuples.addAll(clause.getSubTuples());
        }
        List<Tuple> activeInputTuples = new ArrayList<>();
        for (Tuple t : inputTuples) {
            if (activeTuples.contains(t))
                activeInputTuples.add(t);
        }
        List<Tuple> activeOutputTuples = new ArrayList<>();
        for (Tuple t : outputTuples) {
            if (activeTuples.contains(t))
                activeOutputTuples.add(t);
        }
        // generate provenance structure
        provenance = new Provenance(rules, activeTuples, activeInputTuples, activeOutputTuples, activeClauses);

        timer.done();
        if (Config.v().verbose >= 1) {
            System.out.println("LEAVE: provenance-builder");
            System.out.println("Exclusive time: " + timer.getExclusiveTimeStr());
            System.out.println("Inclusive time: " + timer.getInclusiveTimeStr());
        }
    }

    private void activateDlog() {
        // set names and paths for dlog analysis
        setPath();
        // run original datalog
        ClassicProject.g().runTask(rawDlogAnalysis);
        // run instrumented datalog and fetch results
        ClassicProject.g().resetTaskDone(rawDlogAnalysis);
        ClassicProject.g().runTask(dlogAnalysis);
    }

    public void dump() {
        // dump all constraints
        String consFile = Config.v().outDirName + File.separator + "cons_all.txt";
        PrintWriter cw = Utils.openOut(consFile);
        for (LookUpRule rule : getRules()) {
            cw.println("# " + rule.toString());
            Iterator<ConstraintItem> iter = rule.getAllConstrIterator();
            while (iter.hasNext()) {
                ConstraintItem cons = iter.next();
                Tuple head = cons.getHeadTuple();
                cw.print(head.toSummaryString(","));
                cw.print("=\t");
                List<Tuple> subs = cons.getSubTuples();
                for (int i = 0; i < subs.size(); i++) {
                    if (i != 0) cw.print(",\t");
                    cw.print(subs.get(i).toSummaryString(","));
                }
                cw.println(".");
            }
        }
        cw.flush();
        cw.close();
    }

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

    protected List<Tuple> getTuples(Collection<String> relNames, Collection<Tuple> filtered) {
        List<Tuple> tuples = new ArrayList<>();
        for (String relName : relNames) {
            ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt(relName);
            rel.load();
            for (int[] vals : rel.getAryNIntTuples()) {
                Tuple t = new Tuple(rel, vals);
                if (filtered.contains(t))
                    tuples.add(t);
            }
        }
        return tuples;
    }

    protected List<Tuple> getTuples(Collection<String> relNames) {
        List<Tuple> tuples = new ArrayList<>();
        for (String relName : relNames) {
            ProgramRel rel = (ProgramRel) ClassicProject.g().getTrgt(relName);
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
        relNames.addAll(getInputRelationNames());

        // all derived and output relations
        for (String relName : dlogAnalysis.getProducedRels().keySet()) {
            if  (!DlogInstrumentor.isInstrumented(relName)) {
                relNames.add(relName);
            }
        }
        return relNames;
    }

    protected List<String> getInputRelationNames() {
        return new ArrayList<>(dlogAnalysis.getConsumedRels().keySet());
    }
    // by default, all output relations are added
    protected List<String> getOutputRelationNames() {
        List<String> outputRelNames = new ArrayList<>();
        outputRelNames.addAll(rawDlogAnalysis.getProducedRels().keySet());
        return outputRelNames;
    }

    private class DOBSolver {
        private final Map<Tuple, Integer> tupleDOB;
        private final int maxDOB;
        private int numChanged;
        private final Map<Tuple, Set<ConstraintItem>> tuple2ConsequentClauses;
        private final Map<Tuple, Set<ConstraintItem>> tuple2AntecedentClauses;
        private Set<ConstraintItem> allClauses;
        private Set<ConstraintItem> fwdClauses;
        private Set<ConstraintItem> augClauses;

        public DOBSolver(
                Collection<Tuple> allTuples,
                Collection<Tuple> inputTuples,
                Map<Tuple, Set<ConstraintItem>> tuple2ConsequentClauses,
                Map<Tuple, Set<ConstraintItem>> tuple2AntecedentClauses
        ) {
            this.tuple2ConsequentClauses = tuple2ConsequentClauses;
            this.tuple2AntecedentClauses = tuple2AntecedentClauses;
            maxDOB = allTuples.size();
            tupleDOB = new HashMap<>();
            allClauses = new HashSet<>();
            for (Tuple tuple : allTuples) {
                if (inputTuples.contains(tuple)) {
                    tupleDOB.put(tuple, 0);
                    numChanged++;
                } else {
                    allClauses.addAll(tuple2ConsequentClauses.get(tuple));
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

        private void solve() {
            while(numChanged > 0) {
                //Messages.log("DOBSolver updated " + numChanged + " tuples' date-of-birth");
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
        }

        // binary search to enlarge the non-circular provenances
        private void augmentFromCandidates(List<ConstraintItem> candidateClauses) {
            int tot = candidateClauses.size();
            if (tot == 0) {
                Messages.warn("No candidates to augment forward clauses.");
            } else if (isAncestorDescendantDisjoint(candidateClauses)) {
                augClauses.addAll(candidateClauses);
                //Messages.log("Forward clauses augmented by " + candidateClauses.size());
            } else if (tot == 1) {
                //Messages.log("Backward clause found: " + candidateClauses.get(0).toString());
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
            while (!newAncestors.isEmpty() || !newDescendants.isEmpty()) {
                if (newDescendants.isEmpty()
                        || (!newAncestors.isEmpty() && ancestors.size() < descendants.size())
                ) {
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

        private Set<ConstraintItem> getForwardClauses() {
            if (fwdClauses == null) computeFwdClauses();
            Messages.log("Forward clauses found " + fwdClauses.size());
            return fwdClauses;
        }

        private Set<ConstraintItem> getAugmentedClauses() {
            if (augClauses == null) {
                augClauses = new HashSet<>(getForwardClauses());
                List<ConstraintItem> cands = new ArrayList<>();
                for (ConstraintItem cons : allClauses) {
                    if (!augClauses.contains(cons))
                        cands.add(cons);
                }
                Messages.log("Forward clauses augment candidates " + cands.size());
                augmentFromCandidates(cands);
                Messages.log("Forward clauses augmented by " + (augClauses.size() - fwdClauses.size()));
            }
            return augClauses;
        }

        public Set<ConstraintItem> getActiveClauses(Collection<Tuple> observeTuples) {
            Messages.log("Computing active clauses for " + observeTuples.size() + " tuples.");
            Set<Tuple> coreachableTuples = getCoreachableTuples(observeTuples);

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
            Messages.log("Found " + activeClauses.size() + " active clauses.");
            return activeClauses;
        }
    }
}