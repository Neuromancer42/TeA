package provenance;

import chord.analyses.DlogAnalysis;
import chord.analyses.JavaAnalysis;
import chord.analyses.ProgramRel;
import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.ITask;
import chord.project.Messages;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.*;

public abstract class ProvenanceDriver extends JavaAnalysis {

    protected List<ITask> tasks;

    private String dlogName;
    private File dlogFile;
    private File confFile;
    private DlogAnalysis dlogAnalysis;

    private static String ConsFileName = "cons_all.txt";
    private static String RuleDictFileName = "rule_dict.txt";
    private static String TupleDictFileName = "tuple_dict.txt";
    private static String BaseFileName = "base_queries.txt";
    private File consFile = null;
    private File ruleDictFile = null;
    private File tupleDictFile = null;
    private File baseFile = null;

    private void setPath(String name) {
        dlogName = DlogInstrumentor.instrumentName(name);
        try {
            dlogAnalysis = (DlogAnalysis) ClassicProject.g().getTask(dlogName);
        } catch (ClassCastException e) {
            Messages.fatal("Error: Task " + dlogName + " is not a Datalog Analysis!");
        }
        dlogFile = new File(dlogAnalysis.getFileName());
        confFile = new File(dlogFile.getParent(), dlogName+".config");
    }

    public void run() {
        // 0. set names and paths for dlog analysis
        setPath(getDlogName());
        // 1. run instrumented datalog
        genTasks();
        for (ITask t : tasks) {
            ClassicProject.g().resetTaskDone(t);
            ClassicProject.g().runTask(t);
        }

        // 2. print constraints and node-name dictionary
        tupleDictFile = new File(Config.v().outDirName, TupleDictFileName);
        Map<Tuple, String> tupleIdMap = new HashMap<>(); // TODO make it member
        try {
            PrintWriter tdw = new PrintWriter(tupleDictFile);
            List<Tuple> tuples = getTuples();
            for (int i = 0; i < tuples.size(); i++) {
                Tuple t = tuples.get(i);
                String tupleId = "T" + Integer.toString(i);
                tdw.println(tupleId + ": " + t.toSummaryString(","));
                tupleIdMap.put(t, tupleId);
            }
            tdw.flush();
            tdw.close();
        } catch (FileNotFoundException e) {
            Messages.fatal(e);
        }
        consFile = new File(Config.v().outDirName, ConsFileName);
        ruleDictFile = new File(Config.v().outDirName, RuleDictFileName);
        try {
            PrintWriter pw = new PrintWriter(consFile);
            PrintWriter rdw = new PrintWriter(ruleDictFile);
            List<LookUpRule> rules = getRules();
            for (int i = 0; i < rules.size(); i++) {
                LookUpRule rule = rules.get(i);
                String name = "R" + Integer.toString(i);
                rdw.println(name + ": " + rule.toString());
                Iterator<ConstraintItem> iter = rule.getAllConstrIterator();
                while (iter.hasNext()) {
                    ConstraintItem cons = iter.next();
                    StringBuilder sb = new StringBuilder();
                    sb.append(name + ": ");
                    for (int j = 0; j < cons.getSubTuples().size(); j++) {
                        Tuple sub = cons.getSubTuples().get(j);
                        Boolean sign = cons.getSubTuplesSign().get(j);
                        if (sign) {
                            sb.append("NOT ");
                        }
                        sb.append(tupleIdMap.get(sub));
                        sb.append(", ");
                    }
                    Tuple head = cons.getHeadTuple();
                    Boolean headSign = cons.getHeadTupleSign();
                    if (!headSign) {
                        sb.append("NOT ");
                    }
                    sb.append(tupleIdMap.get(head));
                    pw.println(sb);
                }
            }
            pw.flush();
            pw.close();
            rdw.flush();
            rdw.close();
        } catch (FileNotFoundException e) {
            Messages.fatal(e);
        }

        // 3. print output facts
        baseFile = new File(Config.v().outDirName, BaseFileName);
        try {
            PrintWriter pw = new PrintWriter(baseFile);
            Set<String> qRelNames = getOutputRelationNames();
            for (String qRelName : qRelNames) {
                ProgramRel qRel = (ProgramRel) ClassicProject.g().getTrgt(qRelName);
                qRel.load();
                for (int[] indices : qRel.getAryNIntTuples()) {
                    Tuple t = new Tuple(qRel, indices);
                    pw.println(tupleIdMap.get(t));
                }
            }
            pw.flush();
            pw.close();
        } catch (FileNotFoundException e) {
            Messages.fatal(e);
        }
    }

    protected void genTasks() {
        tasks = new ArrayList<>();
        tasks.add(dlogAnalysis);
    };

    protected List<LookUpRule> getRules() {
        List<LookUpRule> rules = new ArrayList<>();
        try {
            Scanner sc = new Scanner(confFile);
            while (sc.hasNext()) {
                String line = sc.nextLine().trim();
                if (!line.equals("")) {
                    LookUpRule r = new LookUpRule(line);
                    rules.add(r);
                }
            }
            sc.close();
        }  catch (FileNotFoundException e) {
            Messages.fatal(e);
        }
        return rules;
    }

    protected List<Tuple> getTuples() {
        List<Tuple> tuples = new ArrayList<>();
        for (String relName : getRelationNames()) {
            ProgramRel rel = (ProgramRel)  ClassicProject.g().getTrgt(relName);
            rel.load();
            for (int[] vals : rel.getAryNIntTuples()) {
                Tuple t = new Tuple(rel, vals);
                tuples.add(t);
            }
        }
        return tuples;
    }

    // by default, get all (non-instrumenting) relation tuples
    protected Set<String> getRelationNames() {
        // all input relations
        Set<String> relNames = new HashSet<>();
        relNames.addAll(dlogAnalysis.getConsumedRels().keySet());

        // all derived and output relations
        for (String relName : dlogAnalysis.getProducedRels().keySet()) {
            if  (!DlogInstrumentor.isInstrumented(relName)) {
                relNames.add(relName);
            }
        }
        return relNames;
    }

    // by default, all output relations are added
    protected Set<String> getOutputRelationNames() {
        String rawDlogName = DlogInstrumentor.uninstrumentName(dlogName);
        DlogAnalysis rawDlogAnalysis = null;
        try {
            rawDlogAnalysis = (DlogAnalysis) ClassicProject.g().getTask(rawDlogName);
        } catch (ClassCastException e) {
            Messages.fatal("Error: Task " + rawDlogName + " is not a Datalog Analysis!");
        }
        return rawDlogAnalysis.getProducedRels().keySet();
    }

    protected abstract String getDlogName();
}
