package com.neuromancer42.tea.core.project;

import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.osgi.framework.BundleContext;
import org.osgi.framework.Constants;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.analyses.TrgtInfo;
import com.neuromancer42.tea.core.util.Timer;
import com.neuromancer42.tea.core.util.Utils;
import com.neuromancer42.tea.core.util.tuple.object.Pair;

/**
 * Osgi-style of processing Tasks and Trgts,
 * fetching them from registered objects
 * 
 * @author Yifan Chen 
 */
public class OsgiProject extends Project {


    private OsgiProject() { }

    private static OsgiProject project = null;

	public static void init()
	{
        project = new OsgiProject();
        Project.setProject(project);
	}

    public static OsgiProject g()
	{
        return project;
    }

    // record dependency info
    private Map<String, ITask> nameToTaskMap = new HashMap<>();
    private Map<ITask, Map<String, Supplier<Object>>> taskToTrgtProducersMap = new HashMap<>();
    private Map<ITask, Map<String, Consumer<Object>>> taskToTrgtConsumersMap = new HashMap<>();
    
    private Map<String, Set<TrgtInfo>> nameToTrgtInfosMap = new HashMap<>();
    private TrgtParser trgtParser = null;
    
    private Map<String, Set<ITask>> trgtNameToProducingTasksMap = new HashMap<>();
    //private Map<ITask, Set<String>> taskToConsumedTrgtNamesMap = new HashMap<>();

    // record done jobs
    private Set<ITask> doneTasks = new HashSet<>();
    private Map<String, Supplier<Object>> doneNameToTrgtProducerMap = new HashMap<>();
    private Map<String, Object> doneCachedNameToTrgtMap = new HashMap<>();
    private Map<Object, Set<ITask>> doneTrgtToConsumingTasksMap = new HashMap<>();
    
    private boolean isBuilt = false;
	private Set<String> scheduledTaskNames;

    @Override
    public void build() {
        if (isBuilt)
            return;
        //doneTrgts = new HashSet<Object>();
        
    	BundleContext context = FrameworkUtil.getBundle(this.getClass()).getBundleContext();
        if (context == null) {
        	Messages.fatal("OsgiProject: No bundle context, is it running in a osgi framework?");
        }

        // build nameToTaskMap 
        // along with registered target getter/setters
        Collection<ServiceReference<JavaAnalysis>> taskRefs = null;
		try {
			taskRefs = context.getServiceReferences(JavaAnalysis.class, null);
		} catch (InvalidSyntaxException e) {
            Messages.error("OsgiProject: failed to fetch available tasks.");
			Messages.fatal(e);
		}
		
		// for typecheck 
		Map<String, Set<Class<?>>> trgtNameToProducedTypesMap = new HashMap<>();
		
        for (ServiceReference<JavaAnalysis> taskRef : taskRefs) {
        	JavaAnalysis task = context.getService(taskRef);
        	String name = (String) taskRef.getProperty("name");
        	String location = taskRef.getProperty(Constants.OBJECTCLASS).toString();
        	
        	if (name == null) {
                Messages.warn("OsgiProject: Anonymous task %s, using class simplename instead.", task.getClass().toString());
        		name = task.getClass().getSimpleName();
        	}
        	if (nameToTaskMap.put(name, task) != null) {
        		Messages.fatal("OsgiProject: Multiple tasks named '%s' found in project.", name);
        	}

        	// first, find signatures in the registered properties
        	// note that both should be exact
        	Map<String, Pair<TrgtInfo, Supplier<Object>>> production = null;
        	try {
        		production = (Map<String, Pair<TrgtInfo, Supplier<Object>>>) taskRef.getProperty("output");
        	} catch (ClassCastException e) {
        		Messages.warn("OsgiProject: Error in casting configuration of task %s: %s", location, e);
        	}
        	Map<String, Supplier<Object>> producerMap = new HashMap<>();
        	if (production != null) {
	        	for (String trgtName : production.keySet()) {
	        		// build producer map
	        		Pair<TrgtInfo, Supplier<Object>> trgtPair = production.get(trgtName);
	        		TrgtInfo trgtInfo = trgtPair.val0;
	        		markTrgtInfo(trgtName, trgtInfo);
	        		Supplier<Object> trgtSupplier = trgtPair.val1;
	        		producerMap.put(trgtName, trgtSupplier);
	        		// link dependency
	        		Set<ITask> producers = trgtNameToProducingTasksMap.get(trgtName);
	        		if (producers == null) {
	        			producers = new HashSet<>();
	        			trgtNameToProducingTasksMap.put(trgtName, producers);
	        		}
	        		producers.add(task);
	        		Set<Class<?>> producedTypes = trgtNameToProducedTypesMap.get(trgtName);
	        		if (producedTypes == null) {
	        			producedTypes = new HashSet<>();
	        			trgtNameToProducedTypesMap.put(trgtName, producedTypes);
	        		}
	        		producedTypes.add(trgtInfo.type);
	        	}
        	} else {
        		if (Config.v().verbose >= 2)
        			Messages.log("OsgiProject: Task %s registers no output", location);
        	}
        	taskToTrgtProducersMap.put(task, producerMap);
        	
        	Map<String, Pair<TrgtInfo, Consumer<Object>>> consumption = null;
        	try {
        		consumption = (Map<String, Pair<TrgtInfo, Consumer<Object>>>) taskRef.getProperty("input");
        	} catch (ClassCastException e) {
        		Messages.warn("OsgiProject: Error in casting configuration of task %s: %s", location, e);
        	}
        	Map<String, Consumer<Object>> consumerMap = new HashMap<>();
	        if (consumption != null) {
	        	for (String trgtName : consumption.keySet()) {
	        		// build consumer map
	        		Pair<TrgtInfo, Consumer<Object>> trgtPair = consumption.get(trgtName);
	        		TrgtInfo trgtInfo = trgtPair.val0;
	        		markTrgtInfo(trgtName, trgtInfo);
	        		Consumer<Object> trgtConsumer = trgtPair.val1;
	        		consumerMap.put(trgtName, trgtConsumer);
	        	}
        	} else {
        		if (Config.v().verbose >= 2) 
        			Messages.log("OsgiProject: Task %s registers no input", location);
        	}
	        taskToTrgtConsumersMap.put(task, consumerMap);
        }
       
        // Use TrgtParser only to check type consistency
        TrgtParser trgtParser = new TrgtParser(nameToTrgtInfosMap);
        if (!trgtParser.run()) {
            Messages.fatal("OsgiProject: failed to parse target information.");
        }
        // for casting safety in consumers, producers should provide subclasses
        for (String trgtName : trgtParser.getNameToTrgtTypeMap().keySet()) {
        	Set<Class<?>> producedTypes  = trgtNameToProducedTypesMap.get(trgtName);
        	if (producedTypes == null || producedTypes.isEmpty()) {
        		Messages.warn("OsgiProject: No task producing target '%s' found in project.", trgtName);
        		continue;
        	}
        	Class<?> trgtType = trgtParser.getNameToTrgtTypeMap().get(trgtName);
        	for (Class<?> type : producedTypes) {
        		if (!Utils.isSubclass(type, trgtType)) {
        			Messages.warn("OsgiProject: Typecheck fail for trgt %s, produced type %s may be cast to %s", trgtName, type, trgtType);
        		}
        	}
        }
        
        isBuilt = true;
    }

	private void markTrgtInfo(String name, TrgtInfo info) {
		Set<TrgtInfo> infos = nameToTrgtInfosMap.get(name);
		if (infos == null) {
			infos = new HashSet<>();
			nameToTrgtInfosMap.put(name, infos);
		}
		infos.add(info);
	}
    
    public void refresh() {
    	nameToTaskMap = new HashMap<>();
    	taskToTrgtProducersMap = new HashMap<>();
    	taskToTrgtConsumersMap = new HashMap<>();
    	nameToTrgtInfosMap = new HashMap<>();
    	trgtParser = null;
    	
    	trgtNameToProducingTasksMap = new HashMap<>();
    	
    	doneTasks = new HashSet<>();
    	doneNameToTrgtProducerMap = new HashMap<>();
    	doneCachedNameToTrgtMap = new HashMap<>();
    	doneTrgtToConsumingTasksMap = new HashMap<>();
    	
    	isBuilt = false;
    	build();
    }

    @Override
    public void run(String[] taskNames) {
    	scheduledTaskNames = new HashSet<>();
        scheduledTaskNames.addAll(Arrays.asList(taskNames));
        for (String name : taskNames)
            runTask(name);
    }

    @Override
    public void print() {
        build();
        PrintWriter out;
        out = OutDirUtils.newPrintWriter("targets.xml");
        out.println("<targets " +
            "java_analysis_path=\"" + Config.v().javaAnalysisPathName + "\" " +
            "dlog_analysis_path=\"" + Config.v().dlogAnalysisPathName + "\">");
        Map<String, Class<?>> nameToTrgtTypeMap = trgtParser.getNameToTrgtTypeMap();
        for (String trgtName : nameToTrgtTypeMap.keySet()) {
            Class<?> trgtClass = nameToTrgtTypeMap.get(trgtName);
            String kind;
            if (Utils.isSubclass(trgtClass, ProgramDom.class))
                kind = "domain";
            else if (Utils.isSubclass(trgtClass, ProgramRel.class))
                kind = "relation";
            else
                kind = "other";
            Set<ITask> tasks = trgtNameToProducingTasksMap.get(trgtName);
            Iterator<ITask> it = tasks.iterator();
            String producerStr;
            StringBuilder otherProducersStr = new StringBuilder();
            if (it.hasNext()) {
                ITask fstTask = it.next();
                producerStr = getNameAndURL(fstTask);
                while (it.hasNext()) {
                    ITask task = it.next();
                    otherProducersStr.append("<producer ").append(getNameAndURL(task)).append("/>");
                }
            } else
                producerStr = "producer_name=\"-\" producer_url=\"-\"";
            out.println("\t<target name=\"" + trgtName + "\" kind=\"" + kind +
                "\" " + producerStr  + ">" +
                otherProducersStr + "</target>");
        }
        out.println("</targets>");
        out.close();
        out = OutDirUtils.newPrintWriter("taskgraph.dot");
        out.println("digraph G {");
        for (String trgtName : nameToTrgtTypeMap.keySet()) {
            String trgtId = "\"" + trgtName + "_trgt\"";
            out.println(trgtId + "[label=\"\",shape=ellipse,style=filled,color=blue];");
            Class<?> trgtClass = nameToTrgtTypeMap.get(trgtName);
            for (ITask task : trgtNameToProducingTasksMap.get(trgtName)) {
                String taskId = "\"" + task.getName() + "_task\"";
                out.println(taskId + " -> " + trgtId + ";");
            }
        }
        for (ITask task : taskToTrgtConsumersMap.keySet()) {
            String taskId = "\"" + task.getName() + "_task\"";
            for (String trgtName : taskToTrgtConsumersMap.get(task).keySet()) {
                String trgtId = "\"" + trgtName + "_trgt\"";
            	out.println(trgtId + " -> " + taskId + ";");
            }
        }
        for (String name : nameToTaskMap.keySet()) {
            String taskId = "\"" + name + "_task\"";
            out.println(taskId + "[label=\"\",shape=square,style=filled,color=red];");
        }
        out.println("}");
        out.close();
        OutDirUtils.copyResourceByName("web/style.css");
        OutDirUtils.copyResourceByName("web/targets.xsl");
        OutDirUtils.copyResourceByName("web/targets.dtd");
        OutDirUtils.runSaxon("targets.xml", "targets.xsl");
    }

    @Override
    public Set<String> getTasks() {
    	build();
    	return Collections.unmodifiableSet(nameToTaskMap.keySet());
    }

    @Override
    public Map<String, Object> getTrgts() {
        build();
        return Collections.unmodifiableMap(doneCachedNameToTrgtMap);
    }

    @Override
    public void printRels(String[] relNames) {
        // build(); similarly, only done jobs are considered
        for (String relName : relNames) {
            Object obj = getTrgt(relName);
            if (!(obj instanceof ProgramRel))
                Messages.fatal("Failed to load relation " + relName);
            else {
                ProgramRel rel = (ProgramRel) obj;
                rel.load();
                rel.print();
            }
        }
    }

    public ITask getTask(String name) {
        build();
        ITask task = nameToTaskMap.get(name);
        if (task == null)
        	Messages.fatal("OsgiProject: Task named '%s' not found in project.", name);
        return task;
    }

    public Object getTrgt(String name) {
    	Object obj = doneCachedNameToTrgtMap.get(name);
        if (obj == null) { 
        	Supplier<Object> producer = doneNameToTrgtProducerMap.get(name);
        	if (producer != null)
        		obj = producer.get();
        }
        if (obj == null) {
        	Messages.warn("OsgiProject: Target named '%s' not produced yet in other tasks", name);
        }
        return obj;
    }
    private Set<ITask> getTasksProducingTrgtName(String trgtName) {
        Set<ITask> tasks = trgtNameToProducingTasksMap.get(trgtName);
        int n = tasks.size();
        if (n > 1) {
            StringBuilder tasksStr = new StringBuilder();
            for (ITask task : tasks)
                tasksStr.append(" ").append(task.getName());
            if (Config.v().verbose >= 2)
            	Messages.warn("OsgiProject: Multiple tasks produced target '%s'", tasksStr.substring(1), trgtName);
        }
        if (n == 0)
            Messages.fatal("OsgiProject: No task producing target '%s' found in project.", trgtName);
        return tasks;
    }

    private ITask getTaskProducingTrgtName(String trgtName) {
        Set<ITask> candidates = getTasksProducingTrgtName(trgtName);
        if (candidates.size() > 1) {
        	candidates = candidates.stream()
        			.filter(t -> scheduledTaskNames.contains(t.getName()))
        			.collect(Collectors.toSet());
        }
        if (candidates.size() != 1) {
        	Messages.fatal("OsgiProject: Cannot decide task for trgt %s; include exactly one of them via property 'chord.run.analyses'.", trgtName);
        }
        return candidates.iterator().next();
    }
    
    public void runTask(ITask task) {
        if (isTaskDone(task)) {
            if (Config.v().verbose >= 1)
                Messages.log("OsgiProject: task %s already done.", task.getName());
            return;
        }
        Timer timer = new Timer(task.getName());
        if (Config.v().verbose >= 1)
            Messages.log("ENTER: " + task + " at " + (new Date()));
        timer.init();
        timer.pause();
        Map<String, Consumer<Object>> trgtConsumers = taskToTrgtConsumersMap.get(task);
        for (String trgtName : trgtConsumers.keySet()) {
            if (!isTrgtDone(trgtName)) {
            	ITask task2 = getTaskProducingTrgtName(trgtName);
            	runTask(task2);
            }
            Object trgt = getTrgt(trgtName);
            assert (trgt != null);
            doneCachedNameToTrgtMap.put(trgtName, trgt);
//            Messages.log("OsgiProject: passing target %s: %s", trgtName, trgt.toString());
            trgtConsumers.get(trgtName).accept(trgt);
            Set<ITask> consumingTasks = doneTrgtToConsumingTasksMap.get(trgt);
            if (consumingTasks == null) {
            	consumingTasks = new HashSet<>();
            	doneTrgtToConsumingTasksMap.put(trgt, consumingTasks);
            }
            consumingTasks.add(task);
        }
        timer.resume();
        task.run();
        timer.done();
        if (Config.v().verbose >= 1) {
            System.out.println("LEAVE: " + task);
            Timer.printTimer(timer);
        }
        setTaskDone(task);
        Map<String, Supplier<Object>> newProduced = taskToTrgtProducersMap.get(task);
        for (String trgtName : newProduced.keySet()) {
            setTrgtDone(trgtName, newProduced.get(trgtName));
        }
    }

    public ITask runTask(String name) {
        ITask task = nameToTaskMap.get(name);
        runTask(task);
        return task;
    }

    private boolean isTrgtDone(String name) {
        return doneNameToTrgtProducerMap.get(name) != null;
    }
    

    private void setTrgtDone(String name, Supplier<Object> producer) {
        if (doneNameToTrgtProducerMap.put(name, producer) != null) {
        	Messages.warn("OsgiProject: Multiple tasks produced target '%s'", name);
        }
    }

    private void resetAll() {
        doneNameToTrgtProducerMap.clear();
        doneCachedNameToTrgtMap.clear();
        doneTrgtToConsumingTasksMap.clear();
    	doneTasks.clear();
    }

    private void resetTrgtDone(String name) {
        Object trgt = getTrgt(name);
        if (trgt != null) {
        	for (ITask task : doneTrgtToConsumingTasksMap.get(trgt)) {
        		resetTaskDone(task);
        	}
        }
        doneCachedNameToTrgtMap.remove(name);
        doneNameToTrgtProducerMap.remove(name);
    }

    private boolean isTaskDone(ITask task) {
        return doneTasks.contains(task);
    }

    private boolean isTaskDone(String name) {
        return isTaskDone(getTask(name));
    }

    private void setTaskDone(ITask task) {
        doneTasks.add(task);
    }

    private void setTaskDone(String name) {
        setTaskDone(getTask(name));
    }

    private void resetTaskDone(ITask task) {
        if (doneTasks.remove(task)) {
            for (String trgtName : taskToTrgtProducersMap.get(task).keySet()) {
                resetTrgtDone(trgtName);
            }
        }
    }

    private void resetTaskDone(String name) {
        resetTaskDone(getTask(name));
    }

    private static final FilenameFilter filter = (dir, name) -> !name.startsWith(".");
    
    private static String getNameAndURL(ITask task) {
        Class<?> clazz = task.getClass();
        String loc;
//        if (clazz == DlogAnalysis.class) {
//            loc = ((DlogAnalysis) task).getFileName();
//            loc = (new File(loc)).getName();
//        } else
            loc = clazz.getName().replace(".", "/") + ".html";
        //loc = Config.v().javadocURL + loc;
        return "producer_name=\"" + task.getName() +
            "\" producer_url=\"" + loc + "\"";
    }

    private static String getSourceName(ITask analysis) {
        Class<?> clazz = analysis.getClass();
//        if (clazz == DlogAnalysis.class)
//            return ((DlogAnalysis) analysis).getFileName();
        return clazz.getName();
    }

    private void undefinedTarget(String name, List<String> consumerTaskNames) {
        if (Config.v().verbose >= 2) {
            StringBuilder msg = new StringBuilder("'" + name + "' not declared as produced name of any task");
            if (consumerTaskNames.isEmpty())
                msg.append("\n");
            else {
                msg.append("; declared as consumed name of following tasks:\n");
                for (String taskName : consumerTaskNames)
                    msg.append("\t'").append(taskName).append("'\n");
            }
            Messages.warn("OsgiProject: " + msg);
        }
    }
    
    private void redefinedTarget(String name, List<String> producerTaskNames) {
        if (Config.v().verbose >= 2) {
            StringBuilder msg = new StringBuilder("'" + name + "' declared as produced name of multiple tasks:\n");
            for (String taskName : producerTaskNames) 
                msg.append("\t'").append(taskName).append("'\n");
            Messages.warn("OsgiProject: " + msg);
        }
    }

    public Set<ITask> getDoneTasks() {
        return doneTasks;
    }
}
