package chord;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

import chord.project.Messages;
import chord.util.Utils;
import jwutil.collections.IndexMap;
import jwutil.io.SystemProperties;
import net.sf.bddbddb.*;
import chord.bddbddb.Dom;

import chord.project.ClassicProject;
import chord.project.Config;
import chord.project.TaskParser;
import chord.analyses.DlogAnalysis;
import chord.analyses.ProgramRel;

/**
 * A simple datalog instrumentor using John Whaley's BDDSolver. Because he
 * didn't separate the parser nicely from the solver, a program needs to be
 * provided to fool the solver. Usage: ant -Dchord.work.dir=<benchmark>
 * -Dchord.run.analyses=provenance-instr 
 * -Dchord.provenance.dlog=<dlog-name>
 * -Dchord.provenance.paramR=<parametric relations: r1,r2,r3...>
 * -Dchord.provenance.ruleFilter=<true/false> run Note: 1. the current
 * implementation doesn't handle the case that new domain needed to be added to
 * the file. In that case, .bddvarorder needs adjustment. 2. The output
 * uninstrumented rules slightly differ from the rules in the orignal dlog file.
 * This is because options like split are handled
 * 
 * @author xin
 *
 * Patch 1: new domains are added in the end of .bddvarorder by default
 * Patch 2: replace random id with dlog's hashcode
 * Patch 3: optimize the generation of phantom empty domains/relations
 * Patch 3: Instrumentor becomes a separated program
 *
 * @author Yifan Chen
 *
 */

public class DlogInstrumentor {

	public final static String DLOG = "chord.provenance.dlog";
	public final static String PARAM = "chord.provenance.paramR";
	public final static String RULE_FILTER = "chord.provenance.ruleFilter";

	public static void main(String[] args)  {
		Config.init();
		ClassicProject.init();
		String dlogName = System.getProperty(DLOG);
		DlogInstrumentor dlogInstr = new DlogInstrumentor(dlogName);
		dlogInstr.transform();
	}

	private boolean ruleFilter;
	public String dlogName;
	public String instrumentedDlogName;
	private Collection inputRelations;
	private IndexMap allRelations;
	private List rules;
	private List<InferenceRule> rulesToInstr = new ArrayList<InferenceRule>();
	private List<InferenceRule> proRuleList = new ArrayList<InferenceRule>();
	private List<List<Relation>> proRelList = new ArrayList<List<Relation>>();
	private Set<Relation> outputRelations = new HashSet<Relation>();
	private Set<Relation> instrumentedRelations = new HashSet<Relation>();
	private final static String MAGIC = "_XZ89_";
	private String headerInclude = "";
	private String headerBddvarorder = "";
	private String[] paramTuples;
	private String dumpDirName;

	private Solver solver;
	private DlogAnalysis dlogAnalysis = null;

	private PrintWriter dlogOut;
	private PrintWriter configOut;
	
	private int id;


	public DlogInstrumentor(String name) {
		dlogName = name;
		try {
			dlogAnalysis = (DlogAnalysis) ClassicProject.g().getTask(dlogName);
		} catch (ClassCastException e) {
			Messages.fatal("Error: Task " + dlogName + " is not a Datalog Analysis!");
		} catch (Exception e) {
			Messages.fatal(e);
		}
		id = dlogName.hashCode() % 100;
	}

	public void transform() {
		instrumentedDlogName = instrumentName(dlogName);

		Messages.log("Instrumenting [" + dlogName + "] for provenance.");
		ruleFilter = Boolean.parseBoolean(System.getProperty(RULE_FILTER, "false"));
		if(ruleFilter)
			paramTuples = System.getProperty(PARAM).split(",");
		if (dlogName == null)
			throw new RuntimeException("Need to set property: " + DLOG);

		TaskParser taskParser = new TaskParser();
		if (!taskParser.run())
			throw new RuntimeException("Task parsing not successful.");
		//Try to generate empty relations and doms that needed. Note if multiple tasks can produce
		//the tasks, you need to specify the one in the command line
		List<String> relConsumed = taskParser.getNameToConsumeNamesMap().get(dlogName);
		if(relConsumed!=null)
		for(String s : relConsumed){
			Object o = ClassicProject.g().getTrgt(s);
			if(o instanceof Dom){
				Dom d = (Dom)o;
				try {
					d.save(Config.v().bddbddbWorkDirName, Config.v().saveDomMaps);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
			else{
				ProgramRel rel = (ProgramRel)o;
				rel.zero();
				rel.save();
			}
		}

		dlogAnalysis.run();

		String fileName = dlogAnalysis.getFileName();
		System.setProperty("verbose", "" + Config.v().verbose);
		System.setProperty("bdd", "j");
		System.setProperty("basedir", Config.v().bddbddbWorkDirName);
		String solverName = SystemProperties.getProperty("solver", "net.sf.bddbddb.BDDSolver");

		initOutput();

		fillHeader(fileName);
		try {
			solver = (Solver) Class.forName(solverName).newInstance();
			solver.load(fileName);
			inputRelations = solver.getRelationsToLoad();
			allRelations = solver.getRelations();
			outputRelations.addAll(solver.getRelationsToSave());
			rules = solver.getRules();
			filterRules();
			generateProvenance();
			generateInstrumentedDlog();
			closeOutput();

			Messages.log("Instrumented [" + instrumentedDlogName + "] in " + dumpDirName);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void initOutput() {
		try {
			dumpDirName = Config.v().outDirName + File.separator + "provenance";
			Utils.mkdirs(dumpDirName);
			dlogOut = new PrintWriter(new File(dumpDirName + File.separator + instrumentedDlogName + ".dlog"));
			configOut = new PrintWriter(new File(dumpDirName + File.separator + instrumentedDlogName + ".config"));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
	}

	private void closeOutput() {
		dlogOut.flush();
		dlogOut.close();
		configOut.flush();
		configOut.close();
	}

	private void fillHeader(String fileName) {
		Scanner sc;
		try {
			sc = new Scanner(new File(fileName));
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}
		while (sc.hasNext()) {
			String line = sc.nextLine().trim();
			if (line.startsWith(".")) {
				String headerType = line.substring(1,line.indexOf(' '));
				if (headerType.equals("include")) {
					headerInclude += line;
					headerInclude += "\n";
				} else if (headerType.equals("bddvarorder")) {
					headerBddvarorder = line;
				} else {
					Messages.warn("Unknown header discarded: " + line + "\n");
				}
			}
		}
	}

	private void generateProvenance() {
		for (Object o : rules) {
			InferenceRule r = (InferenceRule) o;
			Relation rel = r.getHead().getRelation();
			if(!inputRelations.contains(rel))
				outputRelations.add(rel);
		}
		for (int i = 0; i < rulesToInstr.size(); i++) {
			InferenceRule r = rulesToInstr.get(i);
			int rvc = 0;
			RuleTerm headTerm = r.getHead();
			Relation headRelation = headTerm.getRelation();
			List sgTerms = r.getSubgoals();
//			outputRelations.add(headRelation);
			if (sgTerms.size() == 0)
				continue;
			// //newRelationAttributes = headAttributes + all subGoalAttributes
			// Set<Attribute> attributes = new ArraySet<Attribute>();
			// attributes.addAll(headRelation.getAttributes());
			// for(Object o1 : sgTerms){
			// RuleTerm rt = (RuleTerm)o1;
			// attributes.addAll(rt.getRelation().getAttributes());
			// }
			// Relation headRelation1 =
			// solver.createRelation(headRelation.toString()+MAGIC+i,new
			// ArrayList<Attribute>(attributes));
			// headRelation1.initialize();
			// instrumentedRelations.add(headRelation1);
			List<Relation> rList = new ArrayList<Relation>();
			// rList.add(headRelation1);
			rList.add(headRelation);
			ArrayList<Variable> headVariables = new ArrayList<Variable>();
			// headVariables.addAll(headTerm.getVariables());
			// Add the variables of head relation to the super relation
			for (Object o1 : headTerm.getVariables()) {
				Variable v = (Variable) o1;
				if (v.getName().matches("^[0-9]+$"))// Don't record constant
					continue;
				if (!headVariables.contains(v))
					headVariables.add(v);
			}
			List<RuleTerm> nsgTerms = new ArrayList<RuleTerm>();
			for (Object o1 : sgTerms) {
				RuleTerm term = (RuleTerm) o1;
				List tvs = term.getVariables();
				List<Variable> ntvs = new ArrayList<Variable>();
				for (Object o2 : tvs) {
					Variable v = (Variable) o2;
					if (v.getName().equals("_")) {
						v = new Variable(instrumentName("v") + rvc++, v.getDomain());
					}
					ntvs.add(v);
					if (v.getName().matches("^[0-9]+$"))
						continue;
					if (!headVariables.contains(v))
						headVariables.add(v);
				}
				RuleTerm term1 = new RuleTerm(term.getRelation(), ntvs);
				nsgTerms.add(term1);
				rList.add(term.getRelation());
			}

			List<Attribute> headAttris = new ArrayList<Attribute>();
			for (Variable v : headVariables)
				headAttris.add(new Attribute(v.toString(), v.getDomain(), ""));
			Relation headRelation1 = solver.createRelation(instrumentName(headRelation.toString()) + i+"_"+id, headAttris);
			headRelation1.initialize();
			instrumentedRelations.add(headRelation1);
			rList.add(0, headRelation1);
			RuleTerm headTerm1 = new RuleTerm(headRelation1, headVariables);
			LSInferenceRule nRule = new LSInferenceRule(solver, nsgTerms, headTerm1);
			recordConfig(headTerm, headTerm1, nsgTerms);
			proRuleList.add(nRule);
			proRelList.add(rList);
		}
	}

	/**
	 * The goal of this method is to reduce the rules needing instrumentation.
	 * Two kinds rules will be kept: 1. Any relation on rhs could be determined
	 * by parameters.(for necessary condition) 2. Any relation on lhs could be
	 * determined by parameters.(for faster impossibility result)
	 */
	private void filterRules() {
		if (!ruleFilter) {
			for (Object o : rules) {
				InferenceRule r = (InferenceRule) o;
				rulesToInstr.add(r);
			}
			return;
		}
		Set<String> ptSet = new HashSet<String>();// the relations that could be
													// decided by parameters
		for (String s : paramTuples)
			ptSet.add(s);
		boolean changed = false;
		do {
			changed = false;
			for (Object o : rules) {
				InferenceRule r = (InferenceRule) o;
				
				if (!rulesToInstr.contains(r)) {
					// if any relation on the rhs can be determined by
					// parameters, the lhs relation could be determined by
					// parameters
					if (rhsContainInTS(r, ptSet)) {
						changed = true;
						ptSet.add(r.getHead().getRelation().toString());
						rulesToInstr.add(r);
					} else if (lhsContainInTS(r, ptSet)) {
						changed = true;
						rulesToInstr.add(r);
					}
				}
			}
		} while (changed);
	}

	private static boolean rhsContainInTS(InferenceRule r, Set<String> ts) {
		for (Object o : r.getSubgoals()) {
			RuleTerm rt = (RuleTerm) o;
			if (ts.contains(rt.getRelation().toString()))
				return true;
		}
		return false;
	}

	private static boolean lhsContainInTS(InferenceRule r, Set<String> ts) {
		if (ts.contains(r.getHead().getRelation().toString()))
			return true;
		return false;
	}

	private void recordConfig(RuleTerm headTerm, RuleTerm headTerm1, List<RuleTerm> nsgTerms) {
		configOut.print(headTerm1.getRelation().toString() + " ");
		List h1Variables = headTerm1.getVariables();
		configOut.print(headTerm.getRelation().toString() + " ");
		List hVariables = headTerm.getVariables();
		configOut.print(hVariables.size());
		int wcNum = 0;
		for (int i = 0; i < hVariables.size(); i++) {
			configOut.print(" ");
			Variable v = (Variable) hVariables.get(i);
			if (v.getName().matches("^[0-9]+$")) {
				configOut.print("_" + v.toString());
				continue;
			}
			configOut.print(h1Variables.indexOf(v));
		}
		for (RuleTerm sgt : nsgTerms) {
			configOut.print(" " + sgt.getRelation().toString());
			List sgVariables = sgt.getVariables();
			configOut.print(" " + sgVariables.size());
			for (Object o : sgVariables) {
				configOut.print(" ");
				Variable v = (Variable) o;
				if (v.getName().matches("^[0-9]+$")) {
					configOut.print("_" + v.toString() + " ");
					continue;
				}
				configOut.print(h1Variables.indexOf(v));
			}
		}
		configOut.println();
	}

	private void generateInstrumentedDlog() {
		dlogOut.println("# name=" + instrumentedDlogName);
		dlogOut.println(headerInclude);
		dlogOut.println(generateBddvarorder());
		dlogOut.println();
		dlogOut.println("#Input relations");
		for (Object o : inputRelations) {
			Relation r = (Relation) o;
			dlogOut.println(relationToString(r) + " input");
		}
		dlogOut.println();

		dlogOut.println("#Output relations");
		for (Relation r : outputRelations)
			dlogOut.println(relationToString(r) + " output");
		dlogOut.println();

		dlogOut.println("#Instrumented relations");
		for (Relation r : instrumentedRelations)
			dlogOut.println(relationToString(r) + " output");
		dlogOut.println();

		dlogOut.println("#Original rules");
		for (Object o : rules) {
			InferenceRule r = (InferenceRule) o;
			dlogOut.println(ruleToString(r));
		}
		dlogOut.println();

		dlogOut.println("#Instrumented rules");
		for (InferenceRule r : proRuleList) {
			dlogOut.println(ruleToString(r));
		}

		dlogOut.flush();
		dlogOut.close();
	}

	private static String relationToString(Relation r) {
		return r.verboseToString().replaceAll("\\[", "(").replaceAll("\\]", ")");
	}

	private static String ruleToString(InferenceRule r) {
		StringBuffer sb = new StringBuffer();
		sb.append(r.getHead());
		List subgoals = r.getSubgoals();
		if (!subgoals.isEmpty())
			sb.append(" :- ");
		for (Iterator i = subgoals.iterator(); i.hasNext();) {
			sb.append(i.next());
			if (i.hasNext())
				sb.append(", ");
		}
		sb.append(".");
		return sb.toString();
	}

	private String generateBddvarorder() {
		String bddvarorder = headerBddvarorder;
		for (Relation rel : instrumentedRelations) {
			Map<Domain,Integer> relDomCnt = new HashMap<>();
			for (Object attr : rel.getAttributes()) {
				Domain dom = ((Attribute) attr).getDomain();
				Integer domId = relDomCnt.getOrDefault(dom, 0);
				relDomCnt.put(dom, domId+1);
				String domOrd = dom.toString() + domId.toString();
				if (!bddvarorder.contains(domOrd)) {
					bddvarorder += "_" + domOrd;
				}
			}
		}
		return bddvarorder;
	}

	public static String instrumentName(String rawName) {
		if (rawName.contains(MAGIC)) {
			return rawName;
		} else {
			return rawName + MAGIC;
		}
	}

	public static String uninstrumentName(String instrName) {
		if (instrName.contains(MAGIC)) {
			return instrName.replaceFirst(MAGIC, "");
		} else {
			return instrName;
		}
	}
}
