package javabind.analyses.taint;

import chord.analyses.JavaAnalysis;
import chord.analyses.ProgramRel;
import chord.project.Chord;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import provenance.ConstraintItem;
import provenance.LookUpRule;
import provenance.Tuple;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

@Chord(name="ci-taint-provenance")
public class CITaintProvenance extends JavaAnalysis {

    protected List<ITask> tasks;

    public void run() {

        genTasks();
        runTasks();

        Set<ConstraintItem> provenance = new HashSet<>();
        for (LookUpRule r : getRules()) {
            Iterator<ConstraintItem> iter = r.getAllConstrIterator();
            while (iter.hasNext()) {
                ConstraintItem cons = iter.next();
                provenance.add(cons);
            }
        }

        Set<Tuple> tuples = new HashSet<>();
        Set<String> rels = getRelations();
        for (String s : rels) {
            ProgramRel r = (ProgramRel) ClassicProject.g().getTrgt(s);
            r.load();
            for (int[] vals : r.getAryNIntTuples()) {
                Tuple tmp = new Tuple(r, vals);
                tuples.add(tmp);
            }
        }

        try {
            PrintWriter pw = new PrintWriter(new File(Config.v().outDirName, "named_cons_all.txt"));
            PrintWriter dw = new PrintWriter(new File(Config.v().outDirName, "rule_dict.txt"));
            List<LookUpRule> rules = getRules();
            for (int i = 0; i < rules.size(); ++i) {
                LookUpRule rule = rules.get(i);
                String name = "R" + Integer.toString(i);
                dw.println(name + ": " + rule.toString());
                Iterator<ConstraintItem> iter  = rule.getAllConstrIterator();
                while (iter.hasNext()) {
                    // TODO: this.printNamedConstraint(pw, cons)
                    ConstraintItem cons = iter.next();
                    StringBuilder sb = new StringBuilder();
                    // default weight 1
                    sb.append(name + ": ");
                    for (int j = 0; j < cons.getSubTuples().size(); j++) {
                        Tuple sub = cons.getSubTuples().get(j);
                        Boolean sign = cons.getSubTuplesSign().get(j);
                        if (sign) {
                            sb.append("NOT ");
                        }
                        sb.append(sub.toString());
                        sb.append(", ");
                    }
                    Tuple head = cons.getHeadTuple();
                    Boolean headSign = cons.getHeadTupleSign();
                    if (!headSign) {
                        sb.append("NOT ");
                    }
                    sb.append(head.toString());
                    pw.println(sb);
                }
            }
            pw.flush();
            pw.close();
            dw.flush();
            dw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }

        try {
            Set<Tuple> queries = new HashSet<>();
            PrintWriter pw = new PrintWriter(new File(Config.v().outDirName, "base_queries.txt"));
            ProgramRel relFlow = (ProgramRel) ClassicProject.g().getTrgt("ci_flow");
            relFlow.load();
            for (int[] indices : relFlow.getAryNIntTuples()) {
                Tuple t = new Tuple(relFlow, indices);
                pw.println(t);
                queries.add(t);
            }
            pw.flush();
            pw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    protected void genTasks() {
        tasks = new ArrayList<>();
        tasks.add(ClassicProject.g().getTask("java-ci-taint-dlog_XZ89_"));
    }

    protected void runTasks() {
        for (ITask t : tasks) {
            ClassicProject.g().resetTaskDone(t);
            ClassicProject.g().runTask(t);
        }
    }

    protected List<LookUpRule> getRules() {
        List<LookUpRule> rules = new ArrayList<>();
        String conFile = "datalog/provenance/java-ci-taint-dlog_XZ89_.config";
        try {
            Scanner sc = new Scanner(new File(conFile));
            while (sc.hasNext()) {
                String line = sc.nextLine().trim();
                if (!line.equals("")) {
                    LookUpRule r = new LookUpRule(line);
                    rules.add(r);
                }
            }
            sc.close();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return rules;
    }

    protected  Set<String> getRelations() {
        Set<String> ret = new HashSet<>();
        //TODO
        return ret;
    }
}
