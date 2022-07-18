package com.neuromancer42.tea.core.inference;

import com.neuromancer42.tea.core.provenance.ConstraintItem;
import com.neuromancer42.tea.core.provenance.Provenance;
import com.neuromancer42.tea.core.provenance.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class CausalBuilder {

    private final Function<ConstraintItem, Categorical01> deriveDist;
    private final Function<Tuple, Categorical01> inputDist;

// *** TODO: this is hardcoded prior, do something or move it to other place
//        Categorical01 inputDist = new Categorical01(new double[]{1D, 0.75D, 0.5D, 0.25D});
//        Categorical01 deriveDist = new Categorical01(new double[]{1D, 0.75D, 0.5D, 0.25D});
//        CausalBuilder causalBuilder = new CausalBuilder(
//                (clause) -> new Categorical01(deriveDist),
//                (inputTuple) -> new Categorical01(inputDist)
//        );
//        causal = causalBuilder.buildCausalGraph(provenance);
    public CausalBuilder(Function<ConstraintItem, Categorical01> deriveDist, Function<Tuple, Categorical01> inputDist) {
        this.deriveDist = deriveDist;
        this.inputDist = inputDist;
    }

    public CausalGraph<String> buildCausalGraph(Provenance provenance) {
        List<String> clauseIds = provenance.getClauseIds();
        List<String> inputTupleIds = provenance.getInputTupleIds();
        List<String> outputTupleIds = provenance.getOutputTupleIds();
        List<String> hiddenTupleIds = provenance.getHiddenTupleIds();
        List<String> hybrid = new ArrayList<>();
        hybrid.addAll(clauseIds);
        hybrid.addAll(inputTupleIds);
        hybrid.addAll(outputTupleIds);
        hybrid.addAll(hiddenTupleIds);

        Map<String, Categorical01> stochMapping = new HashMap<>();

        for (String clauseId : clauseIds)
            stochMapping.put(clauseId, deriveDist.apply(provenance.decodeClause(clauseId)));
        for (String inputId : inputTupleIds)
            stochMapping.put(inputId, inputDist.apply(provenance.decodeTuple(inputId)));

        CausalGraph<String> causal = new CausalGraph<String>(hybrid,
                inputTupleIds,
                provenance.getHeadtuple2ClausesMap(),
                provenance.getClause2SubtuplesMap(),
                stochMapping);
        return causal;
    }
}
