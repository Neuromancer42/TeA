package javabind.analyses.taint;

import chord.project.Chord;
import provenance.ProvenanceDriver;

import java.util.ArrayList;
import java.util.List;

@Chord(name="ci-taint-provenance")
public class CITaintProvenance extends ProvenanceDriver {
    @Override
    protected String getDlogName() {
        return "java-ci-taint-dlog";
    }

    @Override
    protected List<String> getOutputRelationNames() {
        List<String> relNames = new ArrayList<>();
        relNames.add("ci_flow");
        return relNames;
    }
}
