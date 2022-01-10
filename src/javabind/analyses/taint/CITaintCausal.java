package javabind.analyses.taint;

import chord.project.Chord;
import inference.CausalDriver;

import java.util.ArrayList;
import java.util.List;

@Chord( name="ci-taint-causal" )
public class CITaintCausal extends CausalDriver {
    @Override
    protected String getDlogName() {
        return "java-ci-taint-dlog";
    }

    @Override
    protected List<String> getObserveRelationNames() {
        List<String> relNames = new ArrayList<>();
        relNames.add("ci_flow");
        return relNames;
    }
}
