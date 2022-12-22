package com.neuromancer42.tea.core;

import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.util.StringUtil;
import com.neuromancer42.tea.core.analysis.Analysis;
import com.neuromancer42.tea.core.analysis.Trgt;

import java.util.*;
import java.util.function.Function;

public class Project {
    private final Map<String, String> producedDoms = new HashMap<>();
    private final Map<String, String> producedRels = new HashMap<>();

    private final Map<String, String> option;

    private final List<String> schedule;
    private final Map<String, Analysis.AnalysisInfo> analysisInfo;
    private final Map<String, Function<Analysis.RunRequest, Analysis.RunResults>> analysisProvider;
    private final Map<String, String[]> relSign;

    public Project(Map<String, String> option, List<String> schedule, Map<String, String[]> relSign, Map<String, Analysis.AnalysisInfo> analysisInfo, Map<String, Function<Analysis.RunRequest, Analysis.RunResults>> analysisProvider) {
        this.option = option;
        this.schedule = schedule;
        this.relSign = relSign;
        this.analysisInfo = analysisInfo;
        this.analysisProvider = analysisProvider;
    }

    public String runAnalysis(String analysis) {
        if (!schedule.contains(analysis)) {
            return String.format(Constants.MSG_FAIL + ": NOT-SCHEDULED analysis '%s'", analysis);
        }
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
        Analysis.RunResults output = analysisProvider.get(analysis).apply(input);

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
        }
        for (Trgt.DomInfo domInfo : info.getProducingDomList()) {
            String domName = domInfo.getName();
            assert producedDoms.containsKey(domName);
        }
        for (Trgt.RelInfo relInfo : info.getProducingRelList()) {
            String relName = relInfo.getName();
            assert producedRels.containsKey(relName);
        }
        return Constants.MSG_SUCC;
    }

    public List<String> printRels(List<String> relNames) {
        List<String> alarms = new ArrayList<>();
        for (String relName : relNames) {
            printRel(relName, alarms);
        }
        return alarms;
    }

    private void printRel(String relName, List<String> alarmList) {
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
                    return;
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
            return;
        }
        rel.attach(relLoc);
        rel.load();
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
}
