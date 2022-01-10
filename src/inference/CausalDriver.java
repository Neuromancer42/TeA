package inference;

import chord.analyses.JavaAnalysis;
import provenance.ConstraintItem;
import provenance.Provenance;
import provenance.ProvenanceBuilder;
import provenance.Tuple;

import java.util.*;
import java.util.function.Function;

public abstract class CausalDriver extends JavaAnalysis {
    private Provenance provenance;
    private CausalGraph<String> pln;

    protected abstract String getDlogName();
    protected abstract List<String> getObserveRelationNames();

    @Override
    public void run() {
        String dlogName = getDlogName();
        ProvenanceBuilder pDriver = new ProvenanceBuilder(dlogName);
        pDriver.computeProvenance(getObserveRelationNames());
        provenance = pDriver.getProvenance();

        Bernoulli inputDist = new Bernoulli(0.9D);
        Bernoulli deriveDist = new Bernoulli(0.99D);
        buildPLN((clause) -> new Bernoulli(deriveDist),
                (inputTuple) -> new Bernoulli(inputDist));
        //provenance.dump(dumpDirName);
        List<String> outputTupleIds = provenance.getOutputTupleIds();
        List<String> hiddenTupleIds = provenance.getHiddenTupleIds();
        List<String> inputTupleIds = provenance.getInputTupleIds();
        List<String> allTupleIds = new ArrayList<>(outputTupleIds.size() + hiddenTupleIds.size() + inputTupleIds.size());
        allTupleIds.addAll(outputTupleIds);
        allTupleIds.addAll(hiddenTupleIds);
        allTupleIds.addAll(inputTupleIds);
        Map<String, Double> queryResults = pln.queryFactorGraph(allTupleIds);
        Map<String, Double> priorQueryResults = queryResults;
        pln.dumpDot("pln_prior.dot", (idx) -> (provenance.unfoldId(idx) + "\n" + priorQueryResults.get(idx)), Bernoulli::toString);
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
            pln.updateFactorGraphWithObservation(obsTrace);
            queryResults = pln.queryFactorGraph(allTupleIds);
            Map<String, Double> curQueryResults = queryResults;
            pln.dumpDot("pln_post-" + i + ".dot", (idx) -> (provenance.unfoldId(idx) + "\n" + curQueryResults.get(idx)), Bernoulli::toString);
        }
    }

    void buildPLN(Function<ConstraintItem, Bernoulli> getDeriveDist, Function<Tuple, Bernoulli> getInputDist) {
        List<String> clauseIds = provenance.getClauseIds();
        List<String> inputTupleIds = provenance.getInputTupleIds();
        List<String> outputTupleIds = provenance.getOutputTupleIds();
        List<String> hiddenTupleIds = provenance.getHiddenTupleIds();
        List<String> hybrid = new ArrayList<>();
        hybrid.addAll(clauseIds);
        hybrid.addAll(inputTupleIds);
        hybrid.addAll(outputTupleIds);
        hybrid.addAll(hiddenTupleIds);

        Map<String, Bernoulli> priorMapping = new HashMap<>();

        for (String clauseId : clauseIds)
            priorMapping.put(clauseId, getDeriveDist.apply(provenance.decodeClause(clauseId)));
        for (String inputId : inputTupleIds)
            priorMapping.put(inputId, getInputDist.apply(provenance.decodeTuple(inputId)));

        pln = new CausalGraph<>(hybrid,
                provenance.getHeadtuple2ClausesMap(),
                provenance.getClause2SubtuplesMap(),
                priorMapping);
    }
}
