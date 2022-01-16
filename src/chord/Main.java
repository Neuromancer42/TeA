package chord;

import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.Project;
import javabind.program.Program;

import chord.util.Timer;
import chord.util.Utils;

import java.util.HashSet;
import java.util.Set;

/**
 * Entry point of Chord after JVM settings are resolved.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 * @author Saswat Anand
 */
public class Main {

    public static void main(String[] args) throws Exception
	{
		Config.init();
		ClassicProject.init();

        Timer timer = new Timer("chord");
        timer.init();
        String initTime = timer.getInitTimeStr();
        if (Config.v().verbose >= 0)
            System.out.println("Chord run initiated at: " + initTime);

        // set main class of Java
        Program program = Program.g();
        String mainClass = Config.v().mainClassName;
        Set<String> harnesses = new HashSet<>();
        harnesses.add(mainClass);
        program.build(harnesses);
        program.setMainClass(mainClass);

        // start analysis
        String[] analysisNames = Utils.toArray(Config.v().runAnalyses);
        Project project = ClassicProject.g();
        if (analysisNames.length > 0) {
            project.run(analysisNames);
        }
        String[] relNames = Utils.toArray(Config.v().printRels);
        if (relNames.length > 0) {
            project.printRels(relNames);
        }
        timer.done();
        String doneTime = timer.getDoneTimeStr();
        if (Config.v().verbose >= 0) {
            System.out.println("Chord run completed at: " + doneTime);
            System.out.println("Total time: " + timer.getInclusiveTimeStr());
        }
    }
}
