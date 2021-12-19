package shord.project;

import java.program.Program;

import chord.util.Timer;
import chord.util.Utils;

/**
 * Entry point of Chord after JVM settings are resolved.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 * @author Saswat Anand
 */
public class Main {
	
	public static long startTime;

    public static void main(String[] args) throws Exception 
	{
		Config.init();
		ClassicProject.init();

		startTime = System.currentTimeMillis();

        Timer timer = new Timer("chord");
        timer.init();
        String initTime = timer.getInitTimeStr();
        if (Config.v().verbose >= 0)
            System.out.println("Chord run initiated at: " + initTime);
        Program program = Program.g();

//        //DEBUG
//        String harness = new String("oata.PrimTaint");
//        Set<String> harnesses = new HashSet<>();
//        harnesses.add(harness);
//        program.build(harnesses);
//        program.setMainClass(harness);
//        System.out.println(program.getMainMethod().toString());

        Project project = ClassicProject.g();
        String[] analysisNames = Utils.toArray(Config.v().runAnalyses);
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
