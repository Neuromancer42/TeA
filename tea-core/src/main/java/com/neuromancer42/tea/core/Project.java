package com.neuromancer42.tea.core;

import com.google.common.base.Stopwatch;
import com.google.protobuf.TextFormat;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.inference.AbstractCausalDriver;
import com.neuromancer42.tea.commons.inference.Categorical01;
import com.neuromancer42.tea.commons.inference.CausalGraph;
import com.neuromancer42.tea.commons.provenance.ProvenanceBuilder;
import com.neuromancer42.tea.commons.provenance.ProvenanceUtil;
import com.neuromancer42.tea.commons.util.StringUtil;
import com.neuromancer42.tea.core.analysis.Analysis;
import com.neuromancer42.tea.core.analysis.ProviderGrpc;
import com.neuromancer42.tea.core.analysis.Trgt;
import com.neuromancer42.tea.libdai.DAIDriverFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Project {
    private final String ID;
    private final Map<String, String> producedDoms = new HashMap<>();
    private final Map<String, String> producedRels = new HashMap<>();

    private final Map<String, String> option;
    private final Path workDir;

    private final List<String> schedule;
    private final Map<String, Analysis.AnalysisInfo> analysisInfo;
    private final Map<String, ProviderGrpc.ProviderBlockingStub> analysisProvider;
    private final Map<String, String[]> relSign;
    private final Set<String> provableRels;
    private final Map<String, String> relProducer = new HashMap<>();
    private final Map<ProviderGrpc.ProviderBlockingStub, Set<String>> observableRels;

    public Project(String projId,
                   Map<String, String> option,
                   Path workDir,
                   List<String> schedule,
                   Map<String, String[]> relSign,
                   Map<String, Analysis.AnalysisInfo> analysisInfo,
                   Map<String, ProviderGrpc.ProviderBlockingStub> analysisProvider,
                   Set<String> provableRels,
                   Map<ProviderGrpc.ProviderBlockingStub, Set<String>> observableRels) {
        this.ID = projId;
        this.option = option;
        this.workDir = workDir;
        this.schedule = schedule;
        this.relSign = relSign;
        this.analysisInfo = analysisInfo;
        this.analysisProvider = analysisProvider;
        this.provableRels = provableRels;
        this.observableRels = observableRels;
    }


    public Path getWorkDir() {
        return workDir;
    }

    public String runAnalysis(String analysis) {
        if (!schedule.contains(analysis)) {
            return String.format(Constants.MSG_FAIL + ": NOT-SCHEDULED analysis '%s'", analysis);
        }
        Messages.log("Project %s: started running analysis %s", ID, analysis);
        Stopwatch inclusiveTimer = Stopwatch.createStarted();
        // 1. build input message
        Analysis.AnalysisInfo info = analysisInfo.get(analysis);
        Analysis.RunRequest.Builder inputBuilder = Analysis.RunRequest.newBuilder();
        inputBuilder.setProjectId(ID);
        inputBuilder.setOption(Analysis.Configs.newBuilder().putAllProperty(option));
        inputBuilder.setAnalysisName(analysis);
        List<String> lost_doms = new ArrayList<>();
        List<String> lost_rels = new ArrayList<>();
        for (Trgt.DomInfo domInfo : info.getConsumingDomList()) {
            String domName = domInfo.getName();
            String domLoc = producedDoms.get(domName);
            if (domLoc == null) {
                lost_doms.add(domName);
            } else {
                Trgt.DomTrgt dom = Trgt.DomTrgt.newBuilder().setInfo(domInfo).setLocation(domLoc).build();
                inputBuilder.addDomInput(dom);
            }
        }
        for (Trgt.RelInfo relInfo : info.getConsumingRelList()) {
            String relName = relInfo.getName();
            String relLoc = producedRels.get(relName);
            if (relLoc == null) {
                lost_rels.add(relName);
            } else {
                Trgt.RelTrgt rel = Trgt.RelTrgt.newBuilder().setInfo(relInfo).setLocation(relLoc).build();
                inputBuilder.addRelInput(rel);
            }
        }
        if (!lost_doms.isEmpty() || !lost_rels.isEmpty()) {
            return Constants.MSG_FAIL + ": LOST-INPUT " +
                    "doms [" + StringUtil.join(lost_doms, ",") + "], " +
                    "rels [" + StringUtil.join(lost_rels, ",") + "]";
        }
        Analysis.RunRequest input = inputBuilder.build();

        // 2. call analysis
        Analysis.RunResults output = analysisProvider.get(analysis).runAnalysis(input);

        // 3. handle results
        for (Trgt.DomTrgt dom : output.getDomOutputList()) {
            String domName = dom.getInfo().getName();
            String domLoc = dom.getLocation();
            producedDoms.put(domName, domLoc);
        }
        for (Trgt.RelTrgt rel : output.getRelOutputList()) {
            String relName = rel.getInfo().getName();
            String relLoc = rel.getLocation();
            producedRels.put(relName, relLoc);
            relProducer.put(relName, analysis);
        }
        for (Trgt.DomInfo domInfo : info.getProducingDomList()) {
            String domName = domInfo.getName();
            assert producedDoms.containsKey(domName);
        }
        for (Trgt.RelInfo relInfo : info.getProducingRelList()) {
            String relName = relInfo.getName();
            assert producedRels.containsKey(relName);
        }
        inclusiveTimer.stop();
        Messages.log("Project %s: finished running analysis %s in %s", ID, analysis, inclusiveTimer);
        return Constants.MSG_SUCC + ": " + analysis + " completed in " + inclusiveTimer;
    }

    public List<Trgt.Tuple> printRels(List<String> relNames) {
        List<Trgt.Tuple> alarms = new ArrayList<>();
        for (String relName : relNames) {
            printRel(relName, alarms);
        }
        return alarms;
    }

    private void printRel(String relName, List<Trgt.Tuple> alarmList) {
        ProgramRel rel = loadRel(relName);
        if (rel == null) return;
        for (Object[] tuple : rel.getValTuples()) {
            Trgt.Tuple.Builder tupleBuilder = Trgt.Tuple.newBuilder();
            tupleBuilder.setRelName(rel.getName());
            for (Object attr : tuple)
                tupleBuilder.addAttribute((String) attr);
            alarmList.add(tupleBuilder.build());
//            StringBuilder sb = new StringBuilder();
//            sb.append(relName);
//            sb.append("(");
//            for (int i = 0; i < tuple.length; ++i) {
//                if (i != 0)
//                    sb.append(",");
//                sb.append((String) tuple[i]);
//            }
//            sb.append(")");
//            alarmList.add(sb.toString());
        }
        rel.close();
    }

    private ProgramRel loadRel(String relName) {
        String[] domKinds = relSign.get(relName);
        ProgramDom[] doms = new ProgramDom[domKinds.length];
        Map<String, ProgramDom> domMap = new HashMap<>();
        for (int i = 0; i < domKinds.length; ++i) {
            String domKind = domKinds[i];
            if (domMap.containsKey(domKind)) {
                doms[i] = domMap.get(domKind);
            } else {
                ProgramDom dom = new ProgramDom(domKind);
                String domLoc = producedDoms.get(domKind);
                if (domLoc == null) {
                    Messages.error("Project %s: dom '%s' is not produced", ID, domKind);
                    return null;
                }
                dom.load(domLoc);
                domMap.put(domKind, dom);
                doms[i] = domMap.get(domKind);
            }
        }
        ProgramRel rel = new ProgramRel(relName, doms);
        String relLoc = producedRels.get(relName);
        if (relLoc == null) {
            Messages.error("Project %s: rel '%s' is not produced", ID, relName);
            return null;
        }
        rel.attach(relLoc);
        rel.load();
        Messages.debug("Project %s: rel %s loaded from %s, size: %d", ID, relName, relLoc, rel.size());
        return rel;
    }

    public Trgt.Provenance proveRels(Collection<String> relNames) {
        Messages.log("Project %s: provenance started", ID);

        ProvenanceBuilder provBuilder = new ProvenanceBuilder(ID, option);
        Map<String, Set<Trgt.Tuple>> analysisToTuples = new HashMap<>();

        for (String relName: relNames) {
            String analysis = relProducer.get(relName);
            if (analysis == null) {
                Messages.error("Project %s: rel '%s' is not produced", ID, relName);
                continue;
            }
            Messages.debug("Project %s: rel '%s' is produced by analysis %s", ID, relName, analysis);
            ProgramRel rel = loadRel(relName);
            assert rel != null;
            for (Object[] tuple : rel.getValTuples()) {
                Trgt.Tuple.Builder tupleBuilder = Trgt.Tuple.newBuilder();
                tupleBuilder.setRelName(rel.getName());
                for (Object attr : tuple)
                    tupleBuilder.addAttribute((String) attr);
                analysisToTuples.computeIfAbsent(analysis, k -> new LinkedHashSet<>()).add(tupleBuilder.build());
            }
            Messages.debug("Project %s: attribute %d alarm tuples to analysis %s", ID, rel.size(), analysis);
        }
        for (Set<Trgt.Tuple> outputTuples : analysisToTuples.values()) {
            provBuilder.addOutputTuples(outputTuples);
        }
        List<Trgt.Tuple> inputTuples = new ArrayList<>();

        // Note: construct full provenance, cumulating from back to forth
        for (int i = schedule.size() - 1; i >= 0; --i) {
            String analysis = schedule.get(i);
            Messages.log("Project %s: introspecting analysis %s", ID, analysis);
            Set<Trgt.Tuple> targets = new LinkedHashSet<>();

            for (Trgt.Tuple tuple : analysisToTuples.getOrDefault(analysis, new HashSet<>())) {
                if (provableRels.contains(tuple.getRelName())) {
//                    Messages.debug("Project %s: tuple '%s' is provable by %s", ID, TextFormat.shortDebugString(tuple), analysis);
                    targets.add(tuple);
                } else {
//                    Messages.debug("Project %s: tuple '%s' is not provable by %s", ID, TextFormat.shortDebugString(tuple), analysis);
                    inputTuples.add(tuple);
                }
            }
            Messages.log("Project %s: %d tuples provable by analysis %s", ID, targets.size(), analysis);
            if (!targets.isEmpty()) {

                Messages.log("Project %s: started running prover %s", ID,  analysis);
                Stopwatch inclusiveTimer = Stopwatch.createStarted();
                ProviderGrpc.ProviderBlockingStub provider = analysisProvider.get(analysis);
                Analysis.ProveRequest req = Analysis.ProveRequest.newBuilder()
                        .setProjectId(ID)
                        .setOption(Analysis.Configs.newBuilder().putAllProperty(option))
                        .addAllTargetTuple(targets)
                        .build();
                Analysis.ProveResponse resp = provider.prove(req);
                provBuilder.addConstraints(resp.getConstraintList());
                for (Trgt.Tuple unsolved : resp.getUnsolvedTupleList()) {
                    String prevAnalysis = relProducer.get(unsolved.getRelName());
                    if (prevAnalysis == null) {
                        inputTuples.add(unsolved);
                    } else {
                        analysisToTuples.computeIfAbsent(prevAnalysis, k -> new LinkedHashSet<>()).add(unsolved);
                    }
                }
                inclusiveTimer.stop();
                Messages.log("Project %s: finished running prover %s in %s", ID, analysis, inclusiveTimer);
            }
        }
        provBuilder.addInputTuples(inputTuples);
        provBuilder.computeProvenance();
        Messages.log("Project %s: provenance completed", ID);
        return provBuilder.getProvenance();
    }

    private int rank_time = 0;
    private AbstractCausalDriver driver = null;

    private void prepareRanking(Trgt.Provenance provenance, Function<String, Categorical01> ruleDist, Function<String, Categorical01> inputRelDist, String driverType) {
        CausalGraph cg = ProvenanceUtil.buildCausalGraph(provenance,
                constr -> ruleDist.apply(constr.getRuleInfo()),
                input -> inputRelDist.apply(input.getRelName())
        );
        driver = new DAIDriverFactory(workDir).createCausalDriver(driverType, driverType+"-"+cg.getName(), cg);
    }

    private void prepareRanking(Trgt.Provenance provenance, Function<String, Categorical01> ruleDist, Function<String, Categorical01> inputRelDist, Set<Trgt.Tuple> reservedTuples, String driverType) {
        CausalGraph cg = ProvenanceUtil.buildSqueezedCausalGraph(provenance,
                constr -> ruleDist.apply(constr.getRuleInfo()),
                input -> inputRelDist.apply(input.getRelName()),
                reservedTuples
        );
        driver = new DAIDriverFactory(workDir).createCausalDriver(driverType, driverType+"-"+cg.getName(), cg);
    }

    public List<Map.Entry<Trgt.Tuple, Double>> priorRanking(Trgt.Provenance provenance,
                                                            Function<String, Categorical01> ruleDist,
                                                            Function<String, Categorical01> inputRelDist,
                                                            List<Trgt.Tuple> alarms,
                                                            Set<Trgt.Tuple> observations,
                                                            Map<String, String> options) {
        boolean squeeze = options.getOrDefault(Constants.OPT_SQZ, "false").equals("true");
        String driverType = options.getOrDefault(Constants.OPT_DRIVER, Constants.DEFAULT_DRIVER);
        if (squeeze) {
            Set<Trgt.Tuple> reservedTuples = new HashSet<>(observations);
            reservedTuples.addAll(alarms);
            prepareRanking(provenance, ruleDist, inputRelDist, reservedTuples, driverType);
        } else {
            prepareRanking(provenance, ruleDist, inputRelDist, driverType);
        }
        return postRanking(alarms, null);
    }

    public List<Map.Entry<Trgt.Tuple, Double>> postRanking(List<Trgt.Tuple> alarms, List<Map<Trgt.Tuple, Boolean>> trace) {
        Map<String, Trgt.Tuple> outputDict = new LinkedHashMap<>();
        for (Trgt.Tuple alarm : alarms) {
            outputDict.put(ProvenanceUtil.encodeTuple(alarm), alarm);
        }
        if (driver == null) {
            Messages.fatal("Project %s: causal driver not built yet before querying", ID);
            assert false;
        }
        if (trace != null) {
            List<Map<String, Boolean>> encTrace = new ArrayList<>();
            for (Map<Trgt.Tuple, Boolean> obs : trace) {
                Map<String, Boolean> encObs = new LinkedHashMap<>();
                for (var entry : obs.entrySet()) {
                    encObs.put(ProvenanceUtil.encodeTuple(entry.getKey()), entry.getValue());
                }
                encTrace.add(encObs);
            }
            driver.appendObservations(encTrace);
            rank_time += trace.size();
        }
        Map<String, Double> dist = driver.queryPossibilities(outputDict.keySet());
        Map<Trgt.Tuple, Double> unorderedAlarms = new LinkedHashMap<>();
        for (String outputRepr : dist.keySet()) {
            Trgt.Tuple output = outputDict.get(outputRepr);
            unorderedAlarms.put(output, dist.get(outputRepr));
        }
        List<Map.Entry<Trgt.Tuple, Double>> sortedAlarmProb = unorderedAlarms.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .collect(Collectors.toList());

        List<String> logLines = new ArrayList<>();
        for (var entry : sortedAlarmProb) {
            logLines.add(entry.getValue() + "\t" + ProvenanceUtil.prettifyTuple(entry.getKey()) + "\t" + ProvenanceUtil.encodeTuple(entry.getKey()));
        }
        Path logPath = workDir.resolve(String.format("rank_%03d.list", rank_time));
        try {
            Files.write(logPath, logLines, StandardCharsets.UTF_8);
            Messages.debug("Project %s: dumping posterior ranking to file %s", ID, logPath.toAbsolutePath());
        } catch (IOException e) {
            Messages.error("Project %s: failed to dump ranking results, skip: %s", ID, e.getMessage());
        }

        return sortedAlarmProb;
    }

    private Set<ProviderGrpc.ProviderBlockingStub> observers = new LinkedHashSet<>();

    public Set<Trgt.Tuple> setObservation(Trgt.Provenance provenance, Map<String, String> options) {
        Set<Trgt.Tuple> observableTuples = new LinkedHashSet<>();
        for (ProviderGrpc.ProviderBlockingStub observer : observableRels.keySet()) {
            Set<Trgt.Tuple> obsTuples = ProvenanceUtil.filterTuple(provenance, observableRels.get(observer));
            Analysis.InstrumentRequest instrReq = Analysis.InstrumentRequest.newBuilder()
                    .setProjectId(ID)
                    .setOption(Analysis.Configs.newBuilder().putAllProperty(options))
                    .addAllInstrTuple(obsTuples)
                    .build();
            Analysis.InstrumentResponse resp = observer.instrument(instrReq);
            if (resp.getSuccTupleCount() > 0) {
                observableTuples.addAll(resp.getSuccTupleList());
                observers.add(observer);
            }
        }
        Messages.log("Project %s: instrumented %d observable tuples", ID, observableTuples.size());
        return observableTuples;
    }

    public Map<Trgt.Tuple, Boolean> testAndObserve(CoreUtil.Test testCase, Map<String, String> options) {
        Messages.log("Project %s: started to running test %s", ID, TextFormat.shortDebugString(testCase));
        Analysis.TestRequest testReq = Analysis.TestRequest.newBuilder()
                .setProjectId(ID)
                .setOption(Analysis.Configs.newBuilder().putAllProperty(options))
                .addAllArg(testCase.getArgList())
                .build();
        Map<Trgt.Tuple, Boolean> obs = new LinkedHashMap<>();
        for (ProviderGrpc.ProviderBlockingStub observer : observers) {
            Analysis.TestResponse testResp = observer.test(testReq);
            for (Trgt.Tuple tuple : testResp.getTriggeredTupleList()) {
                obs.put(tuple, true);
                Messages.log("Project %s: observed true  tuple %s", ID, TextFormat.shortDebugString(tuple));
            }
            for (Trgt.Tuple tuple : testResp.getNegatedTupleList()) {
                obs.put(tuple, false);
                Messages.log("Project %s: observed false tuple %s", ID, TextFormat.shortDebugString(tuple));
            }
        }
        Messages.log("Project %s: observed %d tuples", ID, obs.size());
        return obs;
    }

    public void shutdown() {
        for (ProviderGrpc.ProviderBlockingStub provider : new LinkedHashSet<>(analysisProvider.values())) {
            Messages.log("Project %s: release instances in provider: %s", ID, provider.getChannel().toString());
            Analysis.ShutdownResponse shutdownResp = provider.shutdown(Analysis.ShutdownRequest.newBuilder().setProjectId(ID).build());
        }
    }
}
