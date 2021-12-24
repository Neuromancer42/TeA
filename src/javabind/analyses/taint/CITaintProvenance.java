package javabind.analyses.taint;

import chord.project.Chord;
import provenance.ProvenanceDriver;

import java.util.HashSet;
import java.util.Set;

@Chord(name="ci-taint-provenance")
public class CITaintProvenance extends ProvenanceDriver {
    protected String getDlogName() {
        return "java-ci-taint-dlog";
    }

    @Override
    protected Set<String> getOutputRelationNames() {
        Set<String> relNames = new HashSet<>();
        relNames.add("ci_flow");
        return relNames;
    }
}
