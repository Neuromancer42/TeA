package com.neuromancer42.tea.core;

import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.util.StringUtil;
import com.neuromancer42.tea.core.analysis.Analysis;
import com.neuromancer42.tea.core.analysis.ProviderGrpc;
import com.neuromancer42.tea.core.analysis.Trgt;
import io.grpc.CallOptions;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Function;

public class ProjectBuilder {
    private static ProjectBuilder builder;
    private final Path workPath;

    public static ProjectBuilder g() {
        return builder;
    }

    public static void init(String workdir) {
        try {
            Path path = Files.createDirectories(Paths.get(workdir));
            builder = new ProjectBuilder(path);
        } catch (IOException e) {
            Messages.error("Core: failed to create working directory");
            Messages.fatal(e);
        }
    }

    public ProjectBuilder(Path path) {
        workPath = path;
    }

    private final Map<String, Analysis.AnalysisInfo> analyses = new LinkedHashMap<>();
    private final Map<String, ProviderGrpc.ProviderBlockingStub> analysisProviderMap = new HashMap<>();
    private final Map<String, String[]> relSignMap = new HashMap<>();
    private final Map<Trgt.RelInfo, ProviderGrpc.ProviderBlockingStub> relProverMap = new HashMap<>();
    private final Map<Trgt.RelInfo, ProviderGrpc.ProviderBlockingStub> relObserverMap = new HashMap<>();

    public void queryProvider(Analysis.Configs config, ProviderGrpc.ProviderBlockingStub provider) {
        Analysis.ProviderInfo providerInfo = provider.withWaitForReady().getFeature(config);
        registerProvider(provider, providerInfo);
    }

    public synchronized void registerProvider(ProviderGrpc.ProviderBlockingStub provider, Analysis.ProviderInfo providerInfo) {
        for (Analysis.AnalysisInfo analysisInfo : providerInfo.getAnalysisList()) {
            String analysisName = analysisInfo.getName();
            Analysis.AnalysisInfo previous = analyses.put(analysisName, analysisInfo);
            if (previous != null) {
                Messages.fatal("ProjectBuilder: Multiple task named '%s', discard the previously registered.");
            }
            analysisProviderMap.put(analysisName, provider);
        }
        for (Trgt.RelInfo provableRel : providerInfo.getProvableRelList()) {
            if (relProverMap.put(provableRel, provider) != null) {
                Messages.fatal("ProjectBuilder: Multiple tasks proves relation '%s', use the latter one.", provableRel.getName());
            }
        }
        for (Trgt.RelInfo observableRel : providerInfo.getObservableRelList()) {
            if (relObserverMap.put(observableRel, provider) != null) {
                Messages.fatal("ProjectBuilder: Multiple tasks can observe relation '%s', use the latter one.", observableRel.getName());
            }
        }
        updateDependencyGraph();
    }

    private void updateDependencyGraph() {
        List<String> lines = new ArrayList<>();
        lines.add("digraph G{");
        for (String analysis : analyses.keySet()) {
            Analysis.AnalysisInfo info = analyses.get(analysis);
            for (Trgt.DomInfo inDom : info.getConsumingDomList()) {
                lines.add("dom_" + inDom.getName() + " -> " + "a_" + analysis + ";");
            }
            for (Trgt.RelInfo inRel : info.getConsumingRelList()) {
                lines.add("rel_" + inRel.getName() + " -> " + "a_" + analysis + ";");
            }for (Trgt.DomInfo outDom : info.getProducingDomList()) {
                lines.add("a_" + analysis + "->" + "dom_" + outDom.getName() + ";");
            }
            for (Trgt.RelInfo outRel : info.getProducingRelList()) {
                lines.add("a_" + analysis + " -> " + "rel_" + outRel.getName() + ";");
            }
        }
        lines.add("}");
        try {
            Files.write(this.workPath.resolve("dependency.dot"), lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Messages.error("Core: failed to update dependency graph!");
        }
    }

    public synchronized List<String> scheduleProject(List<String> requiredAnalyses) {
        Map<String, Set<String>> domToProducers = new LinkedHashMap<>();
        Map<String, Set<String>> relToProducers = new LinkedHashMap<>();
        for (String analysisName : analyses.keySet()) {
            Analysis.AnalysisInfo info = analyses.get(analysisName);
            for (Trgt.DomInfo producingDom : info.getProducingDomList()) {
                domToProducers.computeIfAbsent(producingDom.getName(), k -> new LinkedHashSet<>()).add(analysisName);
            }
            for (Trgt.RelInfo producingRel : info.getProducingRelList()) {
                String relName = producingRel.getName();
                relToProducers.computeIfAbsent(relName, k -> new LinkedHashSet<>()).add(analysisName);

                String[] relSign = producingRel.getDomList().toArray(new String[0]);
                relSignMap.putIfAbsent(relName, relSign);
                if (!Arrays.equals(relSignMap.get(relName), relSign)) {
                    Messages.fatal("ProjectBuilder: inconsistent signature of rel '%s' ('%s' & '%s')", relName, Arrays.toString(relSign), Arrays.toString(relSignMap.get(relName)));
                }
            }
            for (Trgt.RelInfo consumingRel : info.getConsumingRelList()) {
                String relName = consumingRel.getName();
                String[] relSign = consumingRel.getDomList().toArray(new String[0]);
                relSignMap.putIfAbsent(relName, relSign);
                if (!Arrays.equals(relSignMap.get(relName), relSign)) {
                    Messages.fatal("ProjectBuilder: inconsistent signature of rel '%s', ('%s' & '%s')", relName, Arrays.toString(relSign), Arrays.toString(relSignMap.get(relName)));
                }
            }
        }
        Map<String, String> fixedDomToProducer = new HashMap<>();
        Map<String, String> fixedRelToProducer = new HashMap<>();
        for (String analysis : requiredAnalyses) {
            Analysis.AnalysisInfo info = analyses.get(analysis);
            if (info == null) {
                Messages.error("ProjectBuilder: required analysis '%s' not found", analysis);
                return null;
            }
            for (Trgt.DomInfo producingDom : info.getProducingDomList()) {
                String domName = producingDom.getName();
                String prev = fixedDomToProducer.put(domName, analysis);
                if (prev != null) {
                    Messages.error("ProjectBuilder: required analysis '%s' and '%s' produced same dom '%s'", prev, analysis, domName);
                    return null;
                }
            }
            for (Trgt.RelInfo producingRel : info.getProducingRelList()) {
                String relName = producingRel.getName();
                String prev = fixedRelToProducer.put(relName, analysis);
                if (prev != null) {
                    Messages.error("ProjectBuilder: required analysis '%s' and '%s' produced same rel '%s'", prev, analysis, relName);
                    return null;
                }
            }
        }

        List<String> schedule = new ArrayList<>();
        for (String analysis : requiredAnalyses) {
            if (!scheduleAnalysis(analysis, schedule, fixedDomToProducer, fixedRelToProducer, domToProducers, relToProducers)) {
                return null;
            }
        }

        return schedule;
    }

    private boolean scheduleAnalysis(String analysis, List<String> schedule, Map<String, String> fixedDomToProducer, Map<String, String> fixedRelToProducer, Map<String, Set<String>> domToProducers, Map<String, Set<String>> relToProducers) {
        if (schedule.contains(analysis)) {
            return true;
        }
        Analysis.AnalysisInfo info = analyses.get(analysis);
        for (Trgt.DomInfo inputDom : info.getConsumingDomList()) {
            String domName = inputDom.getName();
            if (fixedDomToProducer.containsKey(domName)) {
                String domDep = fixedDomToProducer.get(domName);
                if (!scheduleAnalysis(domDep, schedule, fixedDomToProducer, fixedRelToProducer, domToProducers, relToProducers)) {
                    return false;
                }
            } else {
                Set<String> domDeps = domToProducers.get(domName);
                if (domDeps.size() == 1) {
                    for (String domDep: domDeps) {
                        if (!scheduleAnalysis(domDep, schedule, fixedDomToProducer, fixedRelToProducer, domToProducers, relToProducers)) {
                            return false;
                        }
                    }
                } else if (domDeps.isEmpty()) {
                    Messages.error("ProjectBuilder: no analysis producing dom '%s' required by '%s'", domName, analysis);
                    return false;
                } else {
                    Messages.error("ProjectBuilder: dom '%s' produced by multiple candidates [%s]", domName, StringUtil.join(domDeps, ","));
                    return false;
                }
            }
        }
        for (Trgt.RelInfo inputRel : info.getConsumingRelList()) {
            String relName = inputRel.getName();
            if (fixedRelToProducer.containsKey(relName)) {
                String relDep = fixedRelToProducer.get(relName);
                if (!scheduleAnalysis(relDep, schedule, fixedDomToProducer, fixedRelToProducer, domToProducers, relToProducers)) {
                    return false;
                }
            } else {
                Set<String> relDeps = relToProducers.get(relName);
                if (relDeps == null || relDeps.isEmpty()) {
                    Messages.error("ProjectBuilder: no analysis producing rel '%s' required by '%s'", relName, analysis);
                    return false;
                } else if (relDeps.size() == 1) {
                    for (String relDep: relDeps) {
                        if (!scheduleAnalysis(relDep, schedule, fixedDomToProducer, fixedRelToProducer, domToProducers, relToProducers)) {
                            return false;
                        }
                    }
                } else {
                    Messages.error("ProjectBuilder: rel '%s' produced by multiple candidates [%s]", relName, StringUtil.join(relDeps, ","));
                    return false;
                }
            }
        }
        schedule.add(analysis);
        return true;
    }

    public Project buildProject(Map<String, String> option, List<String> schedule) {
        Map<String, String[]> relSign = new HashMap<>();
        Map<String, Analysis.AnalysisInfo> analysisInfo = new HashMap<>();
        Map<String, Function<Analysis.RunRequest, Analysis.RunResults>> analysisProvider = new HashMap<>();
        for (String analysis : schedule) {
            Analysis.AnalysisInfo info = analyses.get(analysis);
            for (Trgt.RelInfo relInfo : info.getProducingRelList()) {
                relSign.put(relInfo.getName(), relInfo.getDomList().toArray(new String[0]));
            }
            analysisInfo.put(analysis, info);
            analysisProvider.put(analysis, analysisProviderMap.get(analysis)::runAnalysis);
        }
        return new Project(option, schedule, relSign, analysisInfo, analysisProvider);
    }
}
