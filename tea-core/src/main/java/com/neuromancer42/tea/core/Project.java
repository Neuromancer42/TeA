package com.neuromancer42.tea.core;

import com.google.common.base.Stopwatch;
import com.google.protobuf.TextFormat;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.provenance.ProvenanceBuilder;
import com.neuromancer42.tea.commons.util.StringUtil;
import com.neuromancer42.tea.core.analysis.Analysis;
import com.neuromancer42.tea.core.analysis.ProviderGrpc;
import com.neuromancer42.tea.core.analysis.Trgt;

import java.nio.file.Path;
import java.util.*;

public class Project {
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

    public Project(Map<String, String> option,
                   Path workDir,
                   List<String> schedule,
                   Map<String, String[]> relSign,
                   Map<String, Analysis.AnalysisInfo> analysisInfo,
                   Map<String, ProviderGrpc.ProviderBlockingStub> analysisProvider,
                   Set<String> provableRels) {
        this.option = option;
        this.workDir = workDir;
        this.schedule = schedule;
        this.relSign = relSign;
        this.analysisInfo = analysisInfo;
        this.analysisProvider = analysisProvider;
        this.provableRels = provableRels;
    }

    public Path getWorkDir() {
        return workDir;
    }

    public String runAnalysis(String analysis) {
        if (!schedule.contains(analysis)) {
            return String.format(Constants.MSG_FAIL + ": NOT-SCHEDULED analysis '%s'", analysis);
        }
        Messages.log("Project: started running analysis %s", analysis);
        Stopwatch inclusiveTimer = Stopwatch.createStarted();
        // 1. build input message
        Analysis.AnalysisInfo info = analysisInfo.get(analysis);
        Analysis.RunRequest.Builder inputBuilder = Analysis.RunRequest.newBuilder();
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
        Messages.log("Project: finished running analysis %s in %s", analysis, inclusiveTimer);
        return Constants.MSG_SUCC + ": " + analysis + " completed in " + inclusiveTimer;
    }

    public List<String> printRels(List<String> relNames) {
        List<String> alarms = new ArrayList<>();
        for (String relName : relNames) {
            printRel(relName, alarms);
        }
        return alarms;
    }

    private void printRel(String relName, List<String> alarmList) {
        ProgramRel rel = loadRel(relName);
        if (rel == null) return;
        for (Object[] tuple : rel.getValTuples()) {
            StringBuilder sb = new StringBuilder();
            sb.append(relName);
            sb.append("(");
            for (int i = 0; i < tuple.length; ++i) {
                if (i != 0)
                    sb.append(",");
                sb.append((String) tuple[i]);
            }
            sb.append(")");
            alarmList.add(sb.toString());
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
                    Messages.error("Project: dom '%s' is not produced", domKind);
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
            Messages.error("Project: rel '%s' is not produced", relName);
            return null;
        }
        rel.attach(relLoc);
        rel.load();
        Messages.debug("Project: rel %s loaded from %s, size: %d", relName, relLoc, rel.size());
        return rel;
    }

    public Trgt.Provenance proveRels(List<String> relNames) {
        Messages.log("Project: provenance started");

        ProvenanceBuilder provBuilder = new ProvenanceBuilder("provenance", option);
        Map<String, Set<Trgt.Tuple>> analysisToTuples = new HashMap<>();

        for (String relName: relNames) {
            String analysis = relProducer.get(relName);
            if (analysis == null) {
                Messages.error("Project: rel '%s' is not produced", relName);
                continue;
            }
            Messages.debug("Project: rel '%s' is produced by analysis %s", relName, analysis);
            ProgramRel rel = loadRel(relName);
            assert rel != null;
            for (Object[] tuple : rel.getValTuples()) {
                Trgt.Tuple.Builder tupleBuilder = Trgt.Tuple.newBuilder();
                tupleBuilder.setRelName(rel.getName());
                for (Object attr : tuple)
                    tupleBuilder.addAttribute((String) attr);
                analysisToTuples.computeIfAbsent(analysis, k -> new LinkedHashSet<>()).add(tupleBuilder.build());
            }
            Messages.debug("Project: attribute %d alarm tuples to analysis %s", rel.size(), analysis);
        }
        for (Set<Trgt.Tuple> outputTuples : analysisToTuples.values()) {
            provBuilder.addOutputTuples(outputTuples);
        }
        List<Trgt.Tuple> inputTuples = new ArrayList<>();

        // Note: construct full provenance, cumulating from back to forth
        for (int i = schedule.size() - 1; i >= 0; --i) {
            String analysis = schedule.get(i);
            Messages.log("Project: introspecting analysis %s", analysis);
            Set<Trgt.Tuple> targets = new LinkedHashSet<>();

            for (Trgt.Tuple tuple : analysisToTuples.getOrDefault(analysis, new HashSet<>())) {
                if (provableRels.contains(tuple.getRelName())) {
//                    Messages.debug("Project: tuple '%s' is provable by %s", TextFormat.shortDebugString(tuple), analysis);
                    targets.add(tuple);
                } else {
//                    Messages.debug("Project: tuple '%s' is not provable by %s", TextFormat.shortDebugString(tuple), analysis);
                    inputTuples.add(tuple);
                }
            }
            Messages.log("Project: %d tuples provable by analysis %s", targets.size(), analysis);
            if (!targets.isEmpty()) {

                Messages.log("Core: started running prover %s", analysis);
                Stopwatch inclusiveTimer = Stopwatch.createStarted();
                ProviderGrpc.ProviderBlockingStub provider = analysisProvider.get(analysis);
                Analysis.ProveRequest req = Analysis.ProveRequest.newBuilder()
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
                Messages.log("Core: finished running prover %s in %s", analysis, inclusiveTimer);
            }
        }
        provBuilder.addInputTuples(inputTuples);
        provBuilder.computeProvenance();
        Messages.log("Project: provenance completed");
        return provBuilder.getProvenance();
    }

    // TODO from provenance to bnet!
}
