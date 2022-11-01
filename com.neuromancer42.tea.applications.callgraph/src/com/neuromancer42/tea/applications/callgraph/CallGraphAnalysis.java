package com.neuromancer42.tea.applications.callgraph;

import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.application.AbstractApplication;
import com.neuromancer42.tea.core.inference.AbstractCausalDriver;
import com.neuromancer42.tea.core.inference.Categorical01;
import com.neuromancer42.tea.core.inference.CausalGraph;
import com.neuromancer42.tea.core.inference.ICausalDriverFactory;
import com.neuromancer42.tea.core.project.Config;
import com.neuromancer42.tea.core.project.ITask;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.OsgiProject;
import com.neuromancer42.tea.core.provenance.IProvable;
import com.neuromancer42.tea.core.provenance.Provenance;
import com.neuromancer42.tea.core.provenance.Tuple;
import com.neuromancer42.tea.core.util.StringUtil;
import com.neuromancer42.tea.core.util.Timer;
import com.neuromancer42.tea.program.cdt.parser.CParser;
import com.neuromancer42.tea.program.cdt.parser.CParserAnalysis;
import org.osgi.framework.BundleContext;
import org.osgi.util.tracker.ServiceTracker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public class CallGraphAnalysis extends AbstractApplication {
    @Override
    protected String getName() {
        return "CallGraph";
    }

    @Override
    protected String[] requiredAnalyses() {
        return new String[]{"ciPointerAnalysis"};
    }

    @Override
    protected void runApplication(BundleContext context, OsgiProject project) {
        project.requireTasks("ciPointerAnalysis");
        project.run("ciPointerAnalysis");
        project.printRels(new String[]{"ci_IM", "MPentry"});

        List<String> queries = new ArrayList<>();
        List<Map<String, Boolean>> trace = new ArrayList<>();

        ITask task = project.getTask("ciPointerAnalysis");
        Provenance provenance = ((IProvable) task).getProvenance();

        ProgramRel ciIM = (ProgramRel) project.getTrgt("ci_IM");
        ciIM.load();
        Map<Integer, Set<Integer>> imMap = new LinkedHashMap<>();
        for (int[] tuple : ciIM.getIntTuples()) {
            int iId = tuple[0];
            int mId = tuple[1];
            imMap.computeIfAbsent(iId, k -> new LinkedHashSet<>()).add(mId);
        }
        Set<Integer> instrIIds = new LinkedHashSet<>();
        Set<Integer> instrMIds = new LinkedHashSet<>();
        for (var entry : imMap.entrySet()) {
            Integer iId = entry.getKey();
            Set<Integer> mIds = entry.getValue();
            if (mIds.size() > 1) {
                instrIIds.add(iId);
                instrMIds.addAll(mIds);
                for (int mId : mIds) {
                    Tuple im = new Tuple("ci_IM", iId, mId);
                    queries.add(provenance.encodeTuple(im));
                }
            }
        }

        String inputListFileName = System.getProperty("tea.inputs.list", "inputs.list");
        Path inputListPath = Paths.get(inputListFileName);
        List<String> inputCases = null;
        try {
            inputCases = Files.readAllLines(inputListPath);
        } catch (IOException e) {
            Messages.error("CInstrument: failed to read input list from file %s", inputListFileName);
            Messages.fatal(e);
            assert false;
        }

        CParser cParser = ((CParserAnalysis) project.getTask("CParser")).getParser();
        CParser.CInstrument cInstr = cParser.getInstrument();
        Timer instrTimer = new Timer(cInstr.getName());
        Messages.log("ENTER: "+ cInstr.getName() + " at " + (new Date()));
        instrTimer.init();
        Map<Integer, Integer> instrInvkMap = new LinkedHashMap<>();
        for (int iId : instrIIds) {
            int id = cInstr.instrumentBeforeInvoke(iId);
            if (id >= 0)
                instrInvkMap.put(id, iId);
        }
        Map<Integer, Integer> instrMethMap = new LinkedHashMap<>();
        for (int mId : instrMIds) {
            int id = cInstr.instrumentEnterMethod(mId);
            if (id >= 0)
                instrMethMap.put(id, mId);
        }
        try {
            Path instrWorkDirPath = Files.createDirectories(Paths.get(Config.v().workDirName).resolve("instr"));
            Path instrFile = instrWorkDirPath.resolve("instrumented.c");
            cInstr.dumpInstrumented(instrFile);
            List<String> compileCmd = new ArrayList<>();
            compileCmd.add("clang");
            String[] flags = System.getProperty("chord.source.flags", "").split(" ");
            if (flags.length > 0)
                compileCmd.addAll(List.of(flags));
            compileCmd.addAll(List.of("instrumented.c", "-o", "instrumented"));
            executeExternal(false, compileCmd, instrWorkDirPath);
            for (String input : inputCases) {
                List<String> executeCmd = new ArrayList<>();
                executeCmd.add("./instrumented");
                executeCmd.addAll(List.of(input.split(" ")));
                String output = executeExternal(true, executeCmd, instrWorkDirPath);

                Map<String, Integer> methAddr2methIdMap = new LinkedHashMap<>();
                Map<Integer, Set<String>> invkId2methAddrMap = new LinkedHashMap<>();

                String[] lines = output.split("\n");
                for (String line : lines) {
                    String[] words = line.split("\t");
                    if (words.length >= 3 && words[0].equals("peek")) {
                        int instrId = Integer.parseInt(words[1]);
                        String mAddr = words[2];
                        if (instrMethMap.containsKey(instrId)) {
                            int mId = instrMethMap.get(instrId);
                            methAddr2methIdMap.put(mAddr, mId);
                        }
                        if (instrInvkMap.containsKey(instrId)) {
                            int iId = instrInvkMap.get(instrId);
                            invkId2methAddrMap.computeIfAbsent(iId, k -> new LinkedHashSet<>()).add(mAddr);
                        }
                    }
                }
                Map<String, Boolean> obs = new LinkedHashMap<>();
                for (int iId : invkId2methAddrMap.keySet()) {
                    for (String mAddr : invkId2methAddrMap.get(iId)) {
                        Integer mId = methAddr2methIdMap.get(mAddr);
                        if (mId != null) {
                            Tuple im = new Tuple("ci_IM", iId, mId);
                            obs.put(provenance.encodeTuple(im), true);
                        }
                    }
                }
                trace.add(obs);
            }
        } catch (IOException | InterruptedException e) {
            Messages.error("CInstrument: failed to execute instrumenting commands");
            Messages.fatal(e);
        }

        instrTimer.done();
        Messages.log("LEAVE: " + cInstr.getName());
        Timer.printTimer(instrTimer);

        CausalGraph<String> causalGraph = CausalGraph.buildCausalGraph(provenance,
                cons -> new Categorical01(0.1, 0.5, 1.0),
                input -> new Categorical01(0.1, 0.5, 1.0));
        causalGraph.dump(Path.of(Config.v().outDirName));
        ServiceTracker<ICausalDriverFactory, ICausalDriverFactory> factoryWatcher = new ServiceTracker<>(context, ICausalDriverFactory.class, null);
        factoryWatcher.open();
        ICausalDriverFactory causalDriverFactory = null;
        try {
            Messages.debug("CIntervalAnalysis: started to wait for a causal driver factory");
            //ICausalDriverFactory causalDriverFactory = context.getService(context.getServiceReference(ICausalDriverFactory.class));
            causalDriverFactory = factoryWatcher.waitForService(100000);
        } catch (InterruptedException e) {
            Messages.debug("CIntervalAnalysis: interrupted when waiting for causal driver factory");
        }
        if (causalDriverFactory == null) {
            Messages.error("CIntervalAnalysis: no causal driver loaded");
            assert false;
        }

        String driverType = System.getProperty("tea.inference.driver", "oneshot");

        assert Arrays.asList(causalDriverFactory.getAlgorithms()).contains("oneshot");
        assert Arrays.asList(causalDriverFactory.getAlgorithms()).contains("iterating");


        AbstractCausalDriver driver = causalDriverFactory.createCausalDriver(driverType, "test-" + driverType + "-callgraph", causalGraph);
        Timer causalTimer = new Timer(driver.getName());
        Messages.log("ENTER: "+ driver.getName() + " at " + (new Date()));
        causalTimer.init();
        Map<String, Double> prior = driver.queryPossibilities(queries);
        Messages.log("Prior: ");
        for (var entry: prior.entrySet()) {
            Messages.log("P(%s) = %f%%", entry.getKey(), 100.0 * entry.getValue());
        }
        driver.run(trace);
        Map<String, Double> post = driver.queryPossibilities(queries);
        Messages.log("Post: ");
        for (var entry: post.entrySet()) {
            Messages.log("P(%s) = %f%%", entry.getKey(), 100.0 * entry.getValue());
        }
        causalTimer.done();
        Messages.log("LEAVE: " + driver.getName());
        Timer.printTimer(causalTimer);
    }

    private static String executeExternal(boolean ignoreRetVal, List<String> cmd, Path path) throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(cmd);
        builder.directory(path.toFile());
        Messages.log("Executing: " + StringUtil.join(cmd, " "));
        Process cmdProcess = builder.start();
        int cmdRetVal = cmdProcess.waitFor();
        String outputStr = new String(cmdProcess.getInputStream().readAllBytes());
        if (!ignoreRetVal && cmdRetVal != 0) {
            String errString = new String(cmdProcess.getErrorStream().readAllBytes());
            Messages.fatal(new RuntimeException(outputStr + '\n' + errString));
        }
        return outputStr;
    }
}
