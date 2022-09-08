package com.neuromancer42.tea.core.provenance;

import com.neuromancer42.tea.core.project.Config;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.util.Timer;
import com.neuromancer42.tea.core.util.Utils;

import java.util.*;

public abstract class AbstractProvenanceBuilder {

    private final boolean doPrune;
    // Provenance Structure
    private Provenance provenance;
    protected final String name;

    protected AbstractProvenanceBuilder(String name, boolean doPrune) {
        this.name = name;
        this.doPrune = doPrune;
    }

    public Provenance getProvenance() {
        if (provenance == null) computeProvenance();
        return provenance;
    }

    protected void computeProvenance() {
        computeProvenance(getOutputTuples());
    }

    public void computeProvenance(Collection<Tuple> observeTuples) {
        Timer timer = new Timer("provenance-builder");
        Messages.log("ENTER: provenance-builder at " + (new Date()));
        timer.init();

        // fetch results and generate dicts
        Collection<ConstraintItem> constraintItems = getAllConstraintItems();
        Set<Tuple> tuples = new HashSet<>();
        for (ConstraintItem cons : constraintItems) {
            tuples.add(cons.getHeadTuple());
            tuples.addAll(cons.getSubTuples());
        }
        Collection<Tuple> inputTuples = getInputTuples();
        Collection<Tuple> outputTuples = getOutputTuples(); // output must have corresponding rules
        Messages.debug("ProvenanceBuilder recorded " + tuples.size() + " tuples.");

        // generate provenance structures
        Map<Tuple, Set<ConstraintItem>> tuple2AntecedentClauses = new HashMap<>();
        Map<Tuple, Set<ConstraintItem>> tuple2ConsequentClauses = new HashMap<>();

        for (Tuple tuple : tuples) {
            tuple2AntecedentClauses.put(tuple, new HashSet<>());
            tuple2ConsequentClauses.put(tuple, new HashSet<>());
        }

        for (ConstraintItem cons : constraintItems) {
            for (Tuple sub : cons.getSubTuples()) {
                tuple2AntecedentClauses.get(sub).add(cons);
            }
            Tuple head = cons.getHeadTuple();
            tuple2ConsequentClauses.get(head).add(cons);
        }

        // de-cycle and prune unused clauses
        Set<ConstraintItem> activeClauses;
        if (doPrune) {
            DOBSolver dobSolver = new DOBSolver(tuples, inputTuples, tuple2ConsequentClauses, tuple2AntecedentClauses);
            activeClauses = dobSolver.getActiveClauses(observeTuples);
        } else {
            activeClauses = new HashSet<>(constraintItems);
        }

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
        provenance = new Provenance(name, activeTuples, activeInputTuples, activeOutputTuples, activeClauses, getRuleInfos());

        timer.done();
        Messages.log("LEAVE: provenance-builder");
        Timer.printTimer(timer);
    }

    public abstract List<String> getRuleInfos();

    public abstract Collection<ConstraintItem> getAllConstraintItems();

    public abstract Collection<Tuple> getInputTuples();

    public abstract Collection<Tuple> getOutputTuples();

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
            Messages.debug("Forward clauses found " + fwdClauses.size());
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
                Messages.debug("Forward clauses augment candidates " + cands.size());
                augmentFromCandidates(cands);
                Messages.debug("Forward clauses augmented by " + (augClauses.size() - fwdClauses.size()));
            }
            return augClauses;
        }

        public Set<ConstraintItem> getActiveClauses(Collection<Tuple> observeTuples) {
            Messages.debug("Computing active clauses for " + observeTuples.size() + " tuples.");
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
            Messages.debug("Found " + activeClauses.size() + " active clauses.");
            return activeClauses;
        }
    }
}