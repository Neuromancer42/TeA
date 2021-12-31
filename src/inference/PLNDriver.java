package inference;

import chord.analyses.JavaAnalysis;
import chord.project.Config;
import chord.util.Utils;
import provenance.ConstraintItem;
import provenance.Provenance;
import provenance.ProvenanceBuilder;
import provenance.Tuple;

import java.io.File;
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

        Categorical01 inputDist = new Categorical01(new double[]{0.01D, 0.5D, 0.99D});
        Categorical01 deriveDist = new Categorical01(new double[]{0.001D, 0.999D});
        buildPLN((clause) -> new Categorical01(deriveDist),
                (inputTuple) -> new Categorical01(inputDist));
        String dumpDirName = Config.v().outDirName + File.separator + "bnet";
        Utils.mkdirs(dumpDirName);
        provenance.dump(dumpDirName);
        pln.dumpDot(dumpDirName, (idx) -> provenance.unfoldId(idx), Categorical01::toString);
        pln.dumpFactorGraph(dumpDirName);
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
