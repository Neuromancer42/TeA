package inference;

import chord.analyses.JavaAnalysis;
import provenance.ConstraintItem;
import provenance.Provenance;
import provenance.ProvenanceBuilder;
import provenance.Tuple;

import java.util.*;
import java.util.function.Function;

public abstract class PLNDriver extends JavaAnalysis {
    private Provenance provenance;
    private PLN<String> pln;

    protected abstract String getDlogName();
    protected abstract List<String> getObserveRelationNames();

    @Override
    public void run() {
        String dlogName = getDlogName();
        ProvenanceBuilder pDriver = new ProvenanceBuilder(dlogName);
        pDriver.computeProvenance(getObserveRelationNames());
        provenance = pDriver.getProvenance();

        Categorical01 inputDist = new Categorical01(new double[]{0.5D, 0.75D, 0.875D, 1D});
        Categorical01 deriveDist = new Categorical01(new double[]{0.5D, 0.75D, 0.875D, 1D});
        buildPLN((clause) -> new Categorical01(deriveDist),
                (inputTuple) -> new Categorical01(inputDist));
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
        pln.dumpDot("pln_prior.dot", (idx) -> (provenance.unfoldId(idx) + "\n" + priorQueryResults.get(idx)), Categorical01::toString);
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
            pln.dumpDot("pln_post-" + i + ".dot", (idx) -> (provenance.unfoldId(idx) + "\n" + curQueryResults.get(idx)), Categorical01::toString);
        }
    }

    void buildPLN(Function<ConstraintItem, Categorical01> getDeriveDist, Function<Tuple, Categorical01> getInputDist) {
        List<String> clauseIds = provenance.getClauseIds();
        List<String> inputTupleIds = provenance.getInputTupleIds();
        List<String> outputTupleIds = provenance.getOutputTupleIds();
        List<String> hiddenTupleIds = provenance.getHiddenTupleIds();
        List<String> hybrid = new ArrayList<>();
        hybrid.addAll(clauseIds);
        hybrid.addAll(inputTupleIds);
        hybrid.addAll(outputTupleIds);
        hybrid.addAll(hiddenTupleIds);

        Map<String, Categorical01> priorMapping = new HashMap<>();

        for (String clauseId : clauseIds)
            priorMapping.put(clauseId, getDeriveDist.apply(provenance.decodeClause(clauseId)));
        for (String inputId : inputTupleIds)
            priorMapping.put(inputId, getInputDist.apply(provenance.decodeTuple(inputId)));

        pln = new PLN<>(hybrid,
                provenance.getHeadtuple2ClausesMap(),
                provenance.getClause2SubtuplesMap(),
                priorMapping);
    }
}
