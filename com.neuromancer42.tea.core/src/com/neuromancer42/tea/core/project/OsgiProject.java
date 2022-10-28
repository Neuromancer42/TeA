package com.neuromancer42.tea.core.project;

import java.io.FilenameFilter;
import java.io.PrintWriter;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import com.neuromancer42.tea.core.analyses.*;
import com.neuromancer42.tea.core.util.StringUtil;
import org.osgi.framework.*;

import com.neuromancer42.tea.core.util.Timer;

/**
 * Osgi-style of processing Tasks and Trgts,
 * fetching them from registered objects
 *
 * @author Yifan Chen
 */
public class OsgiProject extends Project implements ServiceListener {

    private final Map<String, ITask> cachedNameToTaskMap = new HashMap<>();
    private final Map<String, Map<String, Trgt<?>>> cachedNameToInputs = new HashMap<>();
    private final Map<String, Map<String, Trgt<?>>> cachedNameToOutputs = new HashMap<>();
    private final BundleContext context;

    @Override
    public synchronized void serviceChanged(ServiceEvent event) {
        if (event.getType() == ServiceEvent.REGISTERED) {
            ServiceReference<JavaAnalysis> taskRef = (ServiceReference<JavaAnalysis>) event.getServiceReference();
            receiveTask(taskRef);
            notifyAll();
        } else if (event.getType() == ServiceEvent.UNREGISTERING) {
            ServiceReference<JavaAnalysis> taskRef = (ServiceReference<JavaAnalysis>) event.getServiceReference();
            JavaAnalysis task = context.getService(taskRef);
            String name = (String) taskRef.getProperty("name");
            if (name == null) {
                Messages.warn("OsgiProject: Anonymous task %s, using class simplename instead.", task.getClass().toString());
                name = task.getClass().getSimpleName();
            }
            if (cachedNameToTaskMap.containsKey(name)) {
                cachedNameToTaskMap.remove(name);
                cachedNameToInputs.remove(name);
                cachedNameToOutputs.remove(name);
                context.ungetService(event.getServiceReference());
            }
        }
    }

    public void receiveTask(ServiceReference<JavaAnalysis> taskRef) {
        JavaAnalysis task = context.getService(taskRef);
        String name = (String) taskRef.getProperty("name");

        if (name == null) {
            Messages.warn("OsgiProject: Anonymous task %s, using class simplename instead.", task.getClass().toString());
            name = task.getClass().getSimpleName();
        }
        Messages.log("OsgiProject: Receive task %s", name);
        if (cachedNameToTaskMap.put(name, task) != null) {
            Messages.fatal("OsgiProject: Multiple tasks named '%s' found in project.", name);
        }

        Map<String, Trgt<?>> inputs = (Map<String, Trgt<?>>) taskRef.getProperty("input");
        cachedNameToInputs.put(name, inputs);
        Map<String, Trgt<?>> outputs = (Map<String, Trgt<?>>) taskRef.getProperty("output");
        cachedNameToOutputs.put(name, outputs);
    }

    // register an analysis instance to Osgi Runtime
    public static void registerAnalysis(BundleContext context, JavaAnalysis task) {
        Messages.debug("OsgiProject: registering analysis %s from context %s", task.getName(), context.getBundle().getSymbolicName());
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("name", task.getName());
        props.put("input", task.getConsumerMap());
        props.put("output", task.getProducerMap());
        context.registerService(JavaAnalysis.class, task, props);
    }

    public static void registerAnalysisBuilder(BundleContext context, IAnalysisBuilder builder) {
        Messages.debug("OsgiProject: registering analysis builder %s from context %s, available analyses [%s]", builder.getName(), context.getBundle().getSymbolicName(), StringUtil.join(List.of(builder.availableAnalyses()), ","));
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("name", builder.getName());
        props.put("analyses", builder.availableAnalyses());
        context.registerService(IAnalysisBuilder.class, builder, props);
    }

    public synchronized void requireTasks(String ... taskNames) {
        Messages.log("OsgiProject: requiring tasks [%s]", StringUtil.join(List.of(taskNames), ","));
        while (!cachedNameToTaskMap.keySet().containsAll(Set.of(taskNames))) {
            try {
                Messages.debug("OsgiProject: requirements [%s] not meet yet", StringUtil.join(List.of(taskNames), ","));
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Messages.log("OsgiProject: found all required tasks [%s]", StringUtil.join(List.of(taskNames), ","));
            }
        }
        notifyAll();
    }

    private OsgiProject(BundleContext context) {
        this.context = context;
    }

    private static OsgiProject project = null;

	public static void init(BundleContext context)
	{
        project = new OsgiProject(context);
        Project.setProject(project);
	}

    public static OsgiProject g()
	{
        if (project == null) {
            Messages.fatal("OsgiProject: project not initialized");
        }
        return project;
    }

    // record dependency info
    private Map<String, ITask> nameToTaskMap = new HashMap<>();
    private Map<ITask, Map<String, Supplier<Object>>> taskToTrgtProducersMap = new HashMap<>();
    private Map<ITask, Map<String, Consumer<Object>>> taskToTrgtConsumersMap = new HashMap<>();

    private Map<String, Set<ITask>> trgtNameToProducingTasksMap = new HashMap<>();
    //private Map<ITask, Set<String>> taskToConsumedTrgtNamesMap = new HashMap<>();

    // record done jobs
    private Set<ITask> doneTasks = new HashSet<>();
    private Map<String, Supplier<Object>> doneNameToTrgtProducerMap = new HashMap<>();
    private Map<String, Object> doneFetchedNameToTrgtMap = new HashMap<>();
    private Map<Object, Set<ITask>> doneTrgtToConsumingTasksMap = new HashMap<>();
    
    private boolean isBuilt = false;
	private Set<String> scheduledTaskNames;

    @Override
    public synchronized void build() {
        if (isBuilt)
            return;
        //doneTrgts = new HashSet<Object>();
		
		// for typecheck 
		Map<String, Set<TrgtInfo>> nameToProducerInfosMap = new HashMap<>();
        Map<String, Set<TrgtInfo>> nameToConsumerInfosMap = new HashMap<>();

        for (var entry : cachedNameToTaskMap.entrySet()) {
            String name = entry.getKey();
            ITask task = entry.getValue();
            nameToTaskMap.put(name, task);
        	// first, find signatures in the registered properties
        	// note that both should be exact
        	Map<String, Trgt<?>> production = cachedNameToOutputs.get(name);
        	Map<String, Supplier<Object>> producerMap = new HashMap<>();
        	if (production != null) {
	        	for (String trgtName : production.keySet()) {
	        		// build producer map
	        		Trgt<?> trgt = production.get(trgtName);
	        		TrgtInfo trgtInfo = trgt.getTrgtInfo();
                    nameToProducerInfosMap.computeIfAbsent(trgtName, k -> new HashSet<>()).add(trgtInfo);
	        		Supplier<Object> trgtSupplier = trgt.supplier();
	        		producerMap.put(trgtName, trgtSupplier);
	        		// link dependency
                    trgtNameToProducingTasksMap.computeIfAbsent(trgtName, k -> new HashSet<>()).add(task);
	        	}
        	} else {
        		Messages.debug("OsgiProject: Task %s registers no output", name);
        	}
        	taskToTrgtProducersMap.put(task, producerMap);

        	Map<String, Trgt<?>> consumption = cachedNameToInputs.get(name);
        	Map<String, Consumer<Object>> consumerMap = new HashMap<>();
	        if (consumption != null) {
	        	for (String trgtName : consumption.keySet()) {
	        		// build consumer map
	        		Trgt<?> trgt = consumption.get(trgtName);
	        		TrgtInfo trgtInfo = trgt.getTrgtInfo();
	        		nameToConsumerInfosMap.computeIfAbsent(trgtName, k -> new HashSet<>()).add(trgtInfo);
	        		Consumer<Object> trgtConsumer = trgt.consumer();
	        		consumerMap.put(trgtName, trgtConsumer);
	        	}
        	} else {
        		Messages.debug("OsgiProject: Task %s registers no input", name);
        	}
	        taskToTrgtConsumersMap.put(task, consumerMap);
        }

        // for casting safety in consumers, producers should provide subclasses
        for (var consumerEntry : nameToConsumerInfosMap.entrySet()) {
            String trgtName = consumerEntry.getKey();
            Set<TrgtInfo> consumerInfos = consumerEntry.getValue();
            Set<TrgtInfo> producerInfos = nameToProducerInfosMap.get(trgtName);
            if (producerInfos == null || producerInfos.isEmpty()) {
        		Messages.warn("OsgiProject: No task producing target '%s' found in project.", trgtName);
        		continue;
        	}
        	for (TrgtInfo consumerInfo: consumerInfos) {
                for (TrgtInfo producerInfo: producerInfos) {
                    if (!consumerInfo.consumable(producerInfo)) {
                        Messages.warn("OsgiProject: Typecheck fail for trgt %s, produced trgt %s may be cast to %s", trgtName, producerInfo, consumerInfo);
                    }
                }
        	}
        }
        
        isBuilt = true;
    }

    private void reset() {
        if (!isBuilt) {
            return;
        }
        nameToTaskMap = new HashMap<>();
        taskToTrgtProducersMap = new HashMap<>();
        taskToTrgtConsumersMap = new HashMap<>();

        trgtNameToProducingTasksMap = new HashMap<>();

        doneTasks = new HashSet<>();
        doneNameToTrgtProducerMap = new HashMap<>();
        doneFetchedNameToTrgtMap = new HashMap<>();
        doneTrgtToConsumingTasksMap = new HashMap<>();

        isBuilt = false;
    }
    
    public void refresh() {
        reset();
    	build();
    }

    @Override
    public void run(String ... taskNames) {
        build();
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
        for (String trgtName : trgtNameToProducingTasksMap.keySet()) {
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
            out.println("\t<target name=\"" + trgtName +
                "\" " + producerStr  + ">" +
                otherProducersStr + "</target>");
        }
        out.println("</targets>");
        out.close();
        out = OutDirUtils.newPrintWriter("taskgraph.dot");
        out.println("digraph G {");
        for (String trgtName : trgtNameToProducingTasksMap.keySet()) {
            String trgtId = "\"" + trgtName + "_trgt\"";
            out.println(trgtId + "[label=\"\",shape=ellipse,style=filled,color=blue];");
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
        return Collections.unmodifiableMap(doneFetchedNameToTrgtMap);
    }

    @Override
    public void printRels(String[] relNames) {
        build();
        // build(); similarly, only done jobs are considered
        for (String relName : relNames) {
            Object obj = getTrgt(relName);
            if (!(obj instanceof ProgramRel))
                Messages.fatal("Failed to load relation " + relName);
            else {
                ProgramRel rel = (ProgramRel) obj;
                rel.load();
                rel.print();
                rel.close();
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
        build();
    	Object obj = doneFetchedNameToTrgtMap.get(name);
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
        if (tasks == null || tasks.size() == 0) {
            Messages.fatal("OsgiProject: No task producing target '%s' found in project.", trgtName);
            assert false;
        }
        if (tasks.size() > 1) {
            StringBuilder tasksStr = new StringBuilder();
            for (ITask task : tasks)
                tasksStr.append(" ").append(task.getName());
            Messages.warn("OsgiProject: Multiple tasks produced target '%s'", tasksStr.substring(1), trgtName);
        }
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
            Messages.log("OsgiProject: task %s already done.", task.getName());
            return;
        }
        Timer timer = new Timer(task.getName());
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
            doneFetchedNameToTrgtMap.put(trgtName, trgt);
//            Messages.log("OsgiProject: passing target %s: %s", trgtName, trgt.toString());
            trgtConsumers.get(trgtName).accept(trgt);
            doneTrgtToConsumingTasksMap.computeIfAbsent(trgt, k -> new HashSet<>()).add(task);
        }
        timer.resume();
        task.run();
        timer.done();

        Messages.log("LEAVE: " + task);
        Timer.printTimer(timer);

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
        doneFetchedNameToTrgtMap.clear();
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
        doneFetchedNameToTrgtMap.remove(name);
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
    
    private void redefinedTarget(String name, List<String> producerTaskNames) {
        StringBuilder msg = new StringBuilder("'" + name + "' declared as produced name of multiple tasks:\n");
        for (String taskName : producerTaskNames)
            msg.append("\t'").append(taskName).append("'\n");
        Messages.warn("OsgiProject: " + msg);
    }

    public Set<ITask> getDoneTasks() {
        return doneTasks;
    }
}
