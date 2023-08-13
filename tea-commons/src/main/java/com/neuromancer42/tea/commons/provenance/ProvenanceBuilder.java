package com.neuromancer42.tea.commons.provenance;

import com.google.protobuf.TextFormat;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.util.IndexMap;
import com.neuromancer42.tea.core.analysis.Trgt;

import java.nio.file.Path;
import java.util.*;

public class ProvenanceBuilder {

    private final boolean needPrune;
    protected final String name;
    private Trgt.Provenance provenance;
    private final boolean doAugment;

    public ProvenanceBuilder(String name, Map<String, String> options) {
        this.name = name;
        this.needPrune = options.getOrDefault("tea.provenance.prune", "false").equals("true");
        this.doAugment = options.getOrDefault("tea.provenance.augment", "true").equals("true");
    }

    private final IndexMap<String> ruleInfos = new IndexMap<>();
    private final Set<Trgt.Constraint> constraints = new LinkedHashSet<>();
    private final Set<Trgt.Tuple> inputTuples = new LinkedHashSet<>();
    private final Set<Trgt.Tuple> outputTuples = new LinkedHashSet<>();

    public void addRuleInfos(Collection<String> newRuleInfos) {
        for (String newRuleInfo : newRuleInfos) {
            ruleInfos.add(newRuleInfo);
        }
    }

    public void addConstraints(Collection<Trgt.Constraint> newConstraints) {
        Messages.debug("ProvenanceBuilder %s: provenance add %d constraints", name, newConstraints.size());
        constraints.addAll(newConstraints);
        for (Trgt.Constraint cons : newConstraints) {
            Trgt.Tuple head = cons.getHeadTuple();
            if (inputTuples.contains(head)) {
                Messages.warn("ProvenanceBuilder %s: expand proof of tuple %s, removing it from inputs", name, TextFormat.shortDebugString(head));
                inputTuples.remove(head);
            }
        }
    }

    public void addInputTuples(Collection<Trgt.Tuple> newTuples) {
        Messages.debug("ProvenanceBuilder %s: provenance add %d input tuples", name, newTuples.size());
        inputTuples.addAll(newTuples);
    }

    public void addOutputTuples(Collection<Trgt.Tuple> newTuples) {
        Messages.debug("ProvenanceBuilder %s: provenance add %d output tuples", name, newTuples.size());
        outputTuples.addAll(newTuples);
    }

    public Trgt.Provenance getProvenance() {
        if (provenance == null) computeProvenance();
        return provenance;
    }

    public void computeProvenance() {
        computeProvenance(outputTuples);
    }

    public void computeProvenance(Collection<Trgt.Tuple> observeTuples) {
//        Timer timer = new Timer("provenance-builder");
//        Messages.log("ENTER: provenance-builder at " + (new Date()));
//        timer.init();

        // fetch results and generate dicts
        Set<Trgt.Tuple> tuples = new HashSet<>();
        for (Trgt.Constraint cons : constraints) {
            tuples.add(cons.getHeadTuple());
            tuples.addAll(cons.getBodyTupleList());
        }
        Messages.debug("ProvenanceBuilder recorded " + tuples.size() + " tuples.");

        // generate provenance structures
        Map<Trgt.Tuple, Set<Trgt.Constraint>> tuple2AntecedentClauses = new HashMap<>();
        Map<Trgt.Tuple, Set<Trgt.Constraint>> tuple2ConsequentClauses = new HashMap<>();

        for (Trgt.Tuple tuple : tuples) {
            tuple2AntecedentClauses.put(tuple, new HashSet<>());
            tuple2ConsequentClauses.put(tuple, new HashSet<>());
        }

        for (Trgt.Constraint cons : constraints) {
            for (Trgt.Tuple sub : cons.getBodyTupleList()) {
                tuple2ConsequentClauses.get(sub).add(cons);
            }
            Trgt.Tuple head = cons.getHeadTuple();
            tuple2AntecedentClauses.get(head).add(cons);
        }

        // de-cycle and prune unused clauses
        Set<Trgt.Constraint> activeClauses;
        if (needPrune) {
            DOBSolver dobSolver = new DOBSolver(tuples, inputTuples, tuple2ConsequentClauses, tuple2AntecedentClauses);
            activeClauses = dobSolver.getActiveClauses(observeTuples);
        } else {
            activeClauses = new HashSet<>();
            Queue<Trgt.Tuple> workSet = new LinkedList<>(observeTuples);
            while (!workSet.isEmpty()) {
                Trgt.Tuple t = workSet.poll();
                for (Trgt.Constraint cons : tuple2AntecedentClauses.getOrDefault(t, new HashSet<>())) {
                    if (activeClauses.add(cons)) {
                        workSet.addAll(cons.getBodyTupleList());
                    }
                }
            }
        }

        // filter out unused tuples again
        Set<Trgt.Tuple> activeTuples = new HashSet<>();
        for (Trgt.Constraint clause : activeClauses) {
            activeTuples.add(clause.getHeadTuple());
            activeTuples.addAll(clause.getBodyTupleList());
        }
        List<Trgt.Tuple> activeInputTuples = new ArrayList<>();
        for (Trgt.Tuple t : inputTuples) {
            if (activeTuples.contains(t))
                activeInputTuples.add(t);
        }
        List<Trgt.Tuple> activeOutputTuples = new ArrayList<>();
        for (Trgt.Tuple t : outputTuples) {
            if (activeTuples.contains(t))
                activeOutputTuples.add(t);
        }
        // generate provenance structure
        provenance = Trgt.Provenance.newBuilder()
                .setId(name)
                .addAllConstraint(activeClauses)
                .addAllInput(activeInputTuples)
                .addAllOutput(activeOutputTuples)
                .build();

//        timer.done();
//        Messages.log("LEAVE: provenance-builder");
//        Timer.printTimer(timer);
    }

    public boolean dumpProvenance(Path path) {
        if (provenance == null) {
            Messages.error("ProvenanceBuilder %s: no provenance computed yet, do nothing", name);
            return false;
        }
        return ProvenanceUtil.dumpProvenance(provenance, path);
    }

    private class DOBSolver {
        private final Map<Trgt.Tuple, Integer> tupleDOB;
        private final Map<Trgt.Tuple, Set<Trgt.Constraint>> tuple2ConsequentClauses;
        private final Map<Trgt.Tuple, Set<Trgt.Constraint>> tuple2AntecedentClauses;
        private final Set<Trgt.Constraint> allClauses;
        private Set<Trgt.Constraint> fwdClauses;
        private Set<Trgt.Constraint> augClauses;

        public DOBSolver(
                Collection<Trgt.Tuple> allTuples,
                Collection<Trgt.Tuple> inputTuples,
                Map<Trgt.Tuple, Set<Trgt.Constraint>> tuple2ConsequentClauses,
                Map<Trgt.Tuple, Set<Trgt.Constraint>> tuple2AntecedentClauses
        ) {
            this.tuple2ConsequentClauses = tuple2ConsequentClauses;
            this.tuple2AntecedentClauses = tuple2AntecedentClauses;
            int maxDOB = allTuples.size();
            tupleDOB = new HashMap<>();
            allClauses = new HashSet<>();
            for (Trgt.Tuple tuple : allTuples) {
                if (inputTuples.contains(tuple)) {
                    tupleDOB.put(tuple, 0);
                } else {
                    tupleDOB.put(tuple, maxDOB);
                }
                allClauses.addAll(tuple2AntecedentClauses.get(tuple));
            }
        }

        private Integer maxAntecedentDob(Trgt.Constraint clause) {
            Integer dob = 0;
            for (Trgt.Tuple sub : clause.getBodyTupleList()) {
                Integer subDOB = tupleDOB.get(sub);
                if (subDOB > dob) dob = subDOB;
            }
            return dob;
        }

        private void computeFwdClauses() {
            fwdClauses = new HashSet<>();
            Queue<Trgt.Tuple> queue = new LinkedList<>();
            for(Map.Entry<Trgt.Tuple, Integer> entry : tupleDOB.entrySet()){
                if(entry.getValue() == 0){
                    queue.add(entry.getKey());
                }
            }
            Map<Trgt.Constraint, Integer> cnt = new HashMap<>();
            Map<Trgt.Constraint, Integer> siz = new HashMap<>();
            for (Trgt.Constraint clause : allClauses) {
                siz.put(clause, new HashSet<>(clause.getBodyTupleList()).size());
            }
            while(!queue.isEmpty()){
                Trgt.Tuple tuple = queue.poll();
                for(Trgt.Constraint clause : tuple2ConsequentClauses.get(tuple)) {
                    int clsAntecedants = cnt.compute(clause, (cls, c) -> (c == null) ? 1 : (c + 1));
                    if(clsAntecedants == siz.get(clause)) {
                        Trgt.Tuple head = clause.getHeadTuple();
                        if(tupleDOB.get(head) > tupleDOB.get(tuple) + 1){
                            tupleDOB.put(head, tupleDOB.get(tuple) + 1);
                            queue.add(head);
                        }
                    }
                }
            }
            for (Trgt.Constraint clause : allClauses) {
                if (tupleDOB.get(clause.getHeadTuple()) > maxAntecedentDob(clause)) {
                    fwdClauses.add(clause);
                }
            }
        }

        private Set<Trgt.Constraint> getForwardClauses() {
            if (fwdClauses == null) computeFwdClauses();
            Messages.debug("Forward clauses found " + fwdClauses.size());
            return fwdClauses;
        }

        // binary search to enlarge the non-circular provenances
        private void augmentFromCandidates(List<Trgt.Constraint> candidateClauses) {
            int tot = candidateClauses.size();
            if (tot == 0) {
                Messages.warn("No candidates to augment forward clauses.");
            } else if (isAncestorDescendantDisjoint(candidateClauses)) {
                augClauses.addAll(candidateClauses);
                Messages.debug("Forward clauses augmented by " + candidateClauses.size());
            } else if (tot == 1) {
                Messages.debug("Backward clause found: " + candidateClauses.get(0).toString());
            } else {
                int mid = tot / 2;
                augmentFromCandidates(candidateClauses.subList(0, mid));
                augmentFromCandidates(candidateClauses.subList(mid, tot));
            }
        }

        // check non-circularity
        private boolean isAncestorDescendantDisjoint(Collection<Trgt.Constraint> roots) {
            Set<Trgt.Tuple> ancestors = new HashSet<>();
            Set<Trgt.Tuple> descendants = new HashSet<>();
            for (Trgt.Constraint clause : roots) {
                ancestors.addAll(clause.getBodyTupleList());
                descendants.add(clause.getHeadTuple());
            }
            Queue<Trgt.Tuple> newAncestors = new LinkedList<>(ancestors);
            Queue<Trgt.Tuple> newDescendants = new LinkedList<>(descendants);
            while (!newAncestors.isEmpty() || !newDescendants.isEmpty()) {
                if (newDescendants.isEmpty()
                        || (!newAncestors.isEmpty() && ancestors.size() < descendants.size())
                ) {
                    Trgt.Tuple newAncestor = newAncestors.poll();
                    for (Trgt.Constraint cons : tuple2AntecedentClauses.get(newAncestor)) {
                        if (augClauses.contains(cons)) {
                            for (Trgt.Tuple sub : cons.getBodyTupleList()) {
                                if (descendants.contains(sub))
                                    return false;
                                if (ancestors.add(sub))
                                    newAncestors.offer(sub);
                            }
                        }
                    }
                } else {
                    Trgt.Tuple newDescendant = newDescendants.poll();
                    for (Trgt.Constraint ante : tuple2ConsequentClauses.get(newDescendant)) {
                        if (augClauses.contains(ante)) {
                            Trgt.Tuple head = ante.getHeadTuple();
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

        private Set<Trgt.Constraint> getAugmentedClauses() {
            if (augClauses == null) {
                augClauses = new HashSet<>(getForwardClauses());
                List<Trgt.Constraint> cands = new ArrayList<>();
                for (Trgt.Constraint cons : allClauses) {
                    if (!augClauses.contains(cons))
                        cands.add(cons);
                }
                Messages.debug("Forward clauses augment candidates " + cands.size());
                augmentFromCandidates(cands);
                Messages.debug("Forward clauses augmented by " + (augClauses.size() - fwdClauses.size()));
            }
            return augClauses;
        }

        public Set<Trgt.Tuple> getCoreachableTuples(Collection<Trgt.Tuple> outputTuples, Collection<Trgt.Constraint> augFwdClauses) {
            Set<Trgt.Tuple> coreachableTuples = new HashSet<>(outputTuples);
            Queue<Trgt.Tuple> worklist = new LinkedList<>(outputTuples);
            while (!worklist.isEmpty()) {
                Trgt.Tuple head = worklist.poll();
                if (tuple2AntecedentClauses.containsKey(head)) {
                    for (Trgt.Constraint clause : tuple2AntecedentClauses.get(head)) {
                        if (augFwdClauses.contains(clause)) {
                            for (Trgt.Tuple sub : clause.getBodyTupleList()) {
                                if (coreachableTuples.add(sub))
                                    worklist.offer(sub);
                            }
                        }
                    }
                }
            }
            return coreachableTuples;
        }

        public Set<Trgt.Constraint> getActiveClauses(Collection<Trgt.Tuple> observeTuples) {
            Messages.debug("Computing active clauses for " + observeTuples.size() + " tuples.");

            Set<Trgt.Constraint> activeClauses = new HashSet<>();
            Set<Trgt.Constraint> augFwdClauses;
            if (doAugment)
                augFwdClauses = getAugmentedClauses();
            else
                augFwdClauses = getForwardClauses();
            Set<Trgt.Tuple> coreachableTuples = getCoreachableTuples(observeTuples, augFwdClauses);
            for (Trgt.Constraint clause : augFwdClauses) {
                if (coreachableTuples.contains(clause.getHeadTuple())) {
                    activeClauses.add(clause);
                }
            }
            Messages.debug("Found " + activeClauses.size() + " active clauses.");
            return activeClauses;
        }
    }
}