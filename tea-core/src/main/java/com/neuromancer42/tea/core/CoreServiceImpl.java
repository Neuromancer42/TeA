package com.neuromancer42.tea.core;

import com.google.common.base.Stopwatch;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.inference.Categorical01;
import com.neuromancer42.tea.commons.provenance.ProvenanceUtil;
import com.neuromancer42.tea.core.analysis.Trgt;
import com.neuromancer42.tea.libdai.IteratingCausalDriver;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class CoreServiceImpl extends CoreServiceGrpc.CoreServiceImplBase {
    protected final Set<String> projects = new HashSet<>();
    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void runAnalyses(CoreUtil.ApplicationRequest request, StreamObserver<CoreUtil.ApplicationResponse> responseObserver) {
        Messages.log("Core: processing runAnalyses request:\n%s", request.toString());
        Stopwatch allTimer = Stopwatch.createStarted();
        {
            CoreUtil.ApplicationResponse.Builder respBuilder = CoreUtil.ApplicationResponse.newBuilder();
            respBuilder.setMsg(String.format("Initial: handling request for file '%s'", request.getSource().getSource()));
            CoreUtil.ApplicationResponse response = respBuilder.build();
            responseObserver.onNext(response);
        }
        String projId = request.getProjectId();
        projects.add(projId);
        Map<String, String> appOption = new LinkedHashMap<>(request.getOptionMap());
        CoreUtil.Compilation compInfo = request.getSource();
        appOption.put(Constants.OPT_SRC, compInfo.getSource());
        appOption.put(Constants.OPT_SRC_CMD, compInfo.getCommand());

        List<String> schedule = ProjectBuilder.g().scheduleProject(request.getAnalysisList());

        Project proj = null;
        String failMsg = null;
        if (schedule != null) {
            Stopwatch buildTimer = Stopwatch.createStarted();
            proj = ProjectBuilder.g().buildProject(projId, appOption, schedule);
            buildTimer.stop();
            if (proj == null) {
                failMsg = Constants.MSG_FAIL + ": exception happens when build project";
            } else {
                {
                    CoreUtil.ApplicationResponse.Builder respBuilder = CoreUtil.ApplicationResponse.newBuilder();
                    respBuilder.setMsg(String.format(Constants.MSG_SUCC + ": complete analysis schedule [%s] in %s", StringUtils.join(schedule.toArray(new String[0]), ","), buildTimer));
                    CoreUtil.ApplicationResponse response = respBuilder.build();
                    responseObserver.onNext(response);
                }
                for (String analysis : schedule) {
                    String msg = proj.runAnalysis(analysis);
                    CoreUtil.ApplicationResponse.Builder respBuilder = CoreUtil.ApplicationResponse.newBuilder();
                    respBuilder.setMsg(msg);
                    CoreUtil.ApplicationResponse response = respBuilder.build();
                    responseObserver.onNext(response);
                    if (msg.startsWith(Constants.MSG_FAIL)) {
                        failMsg = msg;
                        break;
                    }
                }
            }
        } else {
            failMsg = Constants.MSG_FAIL + ": failed to build project pipeline on required analyses";
        }

        if (failMsg != null) {
            CoreUtil.ApplicationResponse resp = CoreUtil.ApplicationResponse.newBuilder()
                    .setMsg(failMsg)
                    .build();
            responseObserver.onNext(resp);
            if (proj != null) {
                Messages.log("Core: release all instances for project %s", projId);
                proj.shutdown();
            }
            responseObserver.onCompleted();
            return;
        }

        List<String> alarm_rels = request.getAlarmRelList();
        if (!request.getNeedRank()) {
            CoreUtil.ApplicationResponse.Builder respBuilder = CoreUtil.ApplicationResponse.newBuilder();
            if (appOption.getOrDefault("tea.debug.summaryonly", "false").equals("true")) {
                for (var summary : proj.summaryRels(alarm_rels).entrySet()) {
                    respBuilder.addAlarm(summary.getKey() + ":" + summary.getValue());
                }
            } else {
                for (Trgt.Tuple alarm : proj.printRels(alarm_rels)) {
                    respBuilder.addAlarm(ProvenanceUtil.prettifyTuple(alarm));
                }
            }
            respBuilder.setMsg(String.format(Constants.MSG_SUCC + ": all analyses completed in %s", allTimer));
            CoreUtil.ApplicationResponse response = respBuilder.build();
            responseObserver.onNext(response);
        } else {
            Stopwatch provTimer = Stopwatch.createStarted();
            Trgt.Provenance prov = proj.proveRels(alarm_rels);
            ProvenanceUtil.dumpProvenance(prov, proj.getWorkDir());
            List<Trgt.Tuple> alarms = prov.getOutputList();
            provTimer.stop();
            {
                CoreUtil.ApplicationResponse.Builder respBuilder = CoreUtil.ApplicationResponse.newBuilder();
                respBuilder.setMsg(String.format(Constants.MSG_SUCC + ": proof succeeds in %s", provTimer));
                CoreUtil.ApplicationResponse response = respBuilder.build();
                responseObserver.onNext(response);
            }
            Stopwatch instrTimer = Stopwatch.createStarted();
            Set<Trgt.Tuple> obsTuples = proj.setObservation(prov, appOption);
            instrTimer.stop();
            {
                CoreUtil.ApplicationResponse response = CoreUtil.ApplicationResponse.newBuilder()
                        .setMsg(String.format(Constants.MSG_SUCC + ": instrumented %d tuples in %s", obsTuples.size(), instrTimer))
                        .build();
                responseObserver.onNext(response);
            }
            boolean testOnly = appOption.getOrDefault("tea.debug.testonly", "false").equals("true");
            if (!testOnly) {
                Stopwatch priorTimer = Stopwatch.createStarted();
                List<Map.Entry<Trgt.Tuple, Double>> priorRanking = proj.priorRanking(prov,
                        this::getRuleParam,
                        this::getRelParam,
                        alarms,
                        obsTuples,
                        appOption
                );
                priorTimer.stop();
                {
                    CoreUtil.ApplicationResponse.Builder respBuilder = CoreUtil.ApplicationResponse.newBuilder();
                    respBuilder.setMsg(String.format(Constants.MSG_SUCC + ": compute prior ranking in %s", priorTimer));
                    for (int i = 0; i < priorRanking.size(); ++i) {
                        var alarmProb = priorRanking.get(i);
                        Trgt.Tuple alarm = alarmProb.getKey();
                        Double prob = alarmProb.getValue();
                        respBuilder.addAlarm(String.format("%04d", i+1) + ":" + ProvenanceUtil.prettifyProbability(prob) + ":" + ProvenanceUtil.prettifyTuple(alarm));
                    }
                    CoreUtil.ApplicationResponse response = respBuilder.build();
                    responseObserver.onNext(response);
                }
            }
            Stopwatch testTimer = Stopwatch.createStarted();
            List<Map<Trgt.Tuple, Boolean>> trace = new ArrayList<>();
            for (CoreUtil.Test testCase : request.getTestSuiteList()) {
                Map<Trgt.Tuple, Boolean> obs = proj.testAndObserve(testCase, appOption);
                trace.add(obs);
            }
            testTimer.stop();
            {
                CoreUtil.ApplicationResponse response = CoreUtil.ApplicationResponse.newBuilder()
                        .setMsg(String.format(Constants.MSG_SUCC + ": testing %d testcases in %s",request.getTestSuiteCount(), testTimer))
                        .build();
                responseObserver.onNext(response);
            }
            if (!testOnly) {
                Stopwatch postTimer = Stopwatch.createStarted();
                List<Map.Entry<Trgt.Tuple, Double>> postRanking;
                postRanking = proj.postRanking(alarms, trace);
                postTimer.stop();
                {
                    CoreUtil.ApplicationResponse.Builder respBuilder = CoreUtil.ApplicationResponse.newBuilder();

                    respBuilder.setMsg(String.format(Constants.MSG_SUCC + ": all analyses completed in %s", postTimer));

                    for (var alarmProb : postRanking) {
                        Trgt.Tuple alarm = alarmProb.getKey();
                        Double prob = alarmProb.getValue();
                        respBuilder.addAlarm(ProvenanceUtil.prettifyProbability(prob) + ":" + ProvenanceUtil.prettifyTuple(alarm));
                    }
                    CoreUtil.ApplicationResponse response = respBuilder.build();
                    responseObserver.onNext(response);
                }
            }
            {
                CoreUtil.ApplicationResponse response = CoreUtil.ApplicationResponse.newBuilder()
                        .setMsg(String.format(Constants.MSG_SUCC + ": all analyses completed in %s", allTimer))
                        .build();
                responseObserver.onNext(response);
            }
        }
        Messages.log("Core: release all instances for project %s", projId);
        proj.shutdown();
        projects.remove(projId);
        responseObserver.onCompleted();
    }

    private final Map<String, Categorical01> probMap;
    private final Categorical01 defaultProb;

    public CoreServiceImpl(Map<String, Categorical01> probMap, Categorical01 defaultProb) {
        this.probMap = probMap;
        this.defaultProb = defaultProb;
    }

    public Categorical01 getRuleParam(String ruleInfo) {
        // Note: jsouffle-recorded rule info always consists of "<headRelName>.@info.<ruleIdx>"
        String headRelName = ruleInfo.substring(0, ruleInfo.indexOf(".@info"));
        Categorical01 dist = getRelParam(headRelName);
//        Messages.debug("rule %s with prior dist %s", ruleInfo, dist);
        return dist;
    }

    public Categorical01 getRelParam(String relName) {
        Categorical01 prob = probMap.getOrDefault(relName, defaultProb);
        // Note: return a new instance to avoid sharing
        Categorical01 dist = new Categorical01(prob);
//        Messages.debug("rel %s with prior dist %s", relName, dist);
        return dist;
    }
}
