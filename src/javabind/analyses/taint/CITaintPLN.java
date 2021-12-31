package javabind.analyses.taint;

import chord.project.Chord;
import inference.PLNDriver;

import java.util.ArrayList;
import java.util.List;

@Chord( name="ci-taint-pln" )
public class CITaintPLN extends PLNDriver {
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
