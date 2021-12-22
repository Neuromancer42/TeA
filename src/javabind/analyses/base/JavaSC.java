package javabind.analyses.base;

import chord.project.Chord;
import chord.project.ClassicProject;
import chord.analyses.JavaAnalysis;

/**
 * Collect special strings in Java programs
 * TODO: currently it only generates an empty domain
 * @author Yifan Chen
 */

@Chord(name="empty-sc",
    produces={"SC"},
    namesOfTypes = {"SC"},
    types= {DomSC.class}
    )
public class JavaSC extends JavaAnalysis {
    private DomSC domSC;
    private int newLocalCount;

    public void run() {
        domSC = (DomSC) ClassicProject.g().getTrgt("SC");

        domSC.save();
    }
}
