package com.neuromancer42.tea.core.inference;

import com.neuromancer42.tea.core.provenance.Tuple;
import com.neuromancer42.tea.core.provenance.ConstraintItem;
import com.neuromancer42.tea.core.provenance.Provenance;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public abstract class AbstractCausalDriver {
    private Provenance provenance;
    private CausalGraph<String> causal;

    protected abstract String getDlogName();
    protected abstract List<String> getObserveRelationNames();

    protected AbstractCausalDriver(Provenance provenance) {
        this.provenance = provenance;
    }

    public void run() {
        Categorical01 inputDist = new Categorical01(new double[]{1D, 0.75D, 0.5D, 0.25D});
        Categorical01 deriveDist = new Categorical01(new double[]{1D, 0.75D, 0.5D, 0.25D});
        buildCausalGraph((clause) -> new Categorical01(deriveDist),
                (inputTuple) -> new Categorical01(inputDist));
        //provenance.dump(dumpDirName);
        List<String> outputTupleIds = provenance.getOutputTupleIds();
        List<String> hiddenTupleIds = provenance.getHiddenTupleIds();
        List<String> inputTupleIds = provenance.getInputTupleIds();
        List<String> allTupleIds = new ArrayList<>(outputTupleIds.size() + hiddenTupleIds.size() + inputTupleIds.size());
        allTupleIds.addAll(outputTupleIds);
        allTupleIds.addAll(hiddenTupleIds);
        allTupleIds.addAll(inputTupleIds);
        Map<String, Double> queryResults = causal.queryFactorGraph(allTupleIds);
        Map<String, Double> priorQueryResults = queryResults;
        causal.dumpDot("causal_prior.dot", (idx) -> (provenance.unfoldId(idx) + "\n" + priorQueryResults.get(idx)), Categorical01::toString);
        // TODO iterative update with traces
        Map<String, Boolean> obsTrace = new HashMap<>();
        // For debug only
        for (String tupleId : allTupleIds) {
            if (provenance.unfoldId(tupleId).contains("labelPrimFld")) {
                obsTrace.put(tupleId, true);
            }
        }
        // TODO: combine the following two actions
        for (int i = 0; i < 20; i++) {
            causal.updateFactorGraphWithObservation(obsTrace);
            queryResults = causal.queryFactorGraph(allTupleIds);
            Map<String, Double> curQueryResults = queryResults;
            causal.dumpDot("causal_post-" + i + ".dot", (idx) -> (provenance.unfoldId(idx) + "\n" + curQueryResults.get(idx)), Categorical01::toString);
        }
    }

    void buildCausalGraph(Function<ConstraintItem, Categorical01> getDeriveDist, Function<Tuple, Categorical01> getInputDist) {
        List<String> clauseIds = provenance.getClauseIds();
        List<String> inputTupleIds = provenance.getInputTupleIds();
        List<String> outputTupleIds = provenance.getOutputTupleIds();
        List<String> hiddenTupleIds = provenance.getHiddenTupleIds();
        List<String> hybrid = new ArrayList<>();
        hybrid.addAll(clauseIds);
        hybrid.addAll(inputTupleIds);
        hybrid.addAll(outputTupleIds);
        hybrid.addAll(hiddenTupleIds);

        Map<String, Categorical01> deriveMapping = new HashMap<>();
        Map<String, Categorical01> inputMapping = new HashMap<>();

        for (String clauseId : clauseIds)
            deriveMapping.put(clauseId, getDeriveDist.apply(provenance.decodeClause(clauseId)));
        for (String inputId : inputTupleIds)
            inputMapping.put(inputId, getInputDist.apply(provenance.decodeTuple(inputId)));

        causal = new CausalGraph<>(hybrid,
                inputTupleIds,
                provenance.getHeadtuple2ClausesMap(),
                provenance.getClause2SubtuplesMap(),
                deriveMapping,
                inputMapping);
    }
}
