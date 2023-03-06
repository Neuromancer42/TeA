package com.neuromancer42.tea.core;

import com.google.common.base.Stopwatch;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.inference.Categorical01;
import com.neuromancer42.tea.commons.provenance.ProvenanceUtil;
import com.neuromancer42.tea.core.analysis.Trgt;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class CoreServiceImpl extends CoreServiceGrpc.CoreServiceImplBase {
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
            for (Trgt.Tuple alarm : proj.printRels(alarm_rels)) {
                respBuilder.addAlarm(ProvenanceUtil.prettifyTuple(alarm));
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
            Stopwatch priorTimer = Stopwatch.createStarted();
            List<Map.Entry<Trgt.Tuple, Double>> priorRanking = proj.priorRanking(prov,
                    rule -> new Categorical01(0.5, 1.0),
                    rel -> new Categorical01(0.5, 1.0),
                    alarms,
                    obsTuples,
                    appOption
            );
            priorTimer.stop();
            {
                CoreUtil.ApplicationResponse.Builder respBuilder = CoreUtil.ApplicationResponse.newBuilder();
                respBuilder.setMsg(String.format(Constants.MSG_SUCC + ": compute prior ranking in %s", priorTimer));
                for (var alarmProb : priorRanking) {
                    Trgt.Tuple alarm = alarmProb.getKey();
                    Double prob = alarmProb.getValue();
                    respBuilder.addAlarm(ProvenanceUtil.prettifyProbability(prob) + ":" + ProvenanceUtil.prettifyTuple(alarm));
                }
                CoreUtil.ApplicationResponse response = respBuilder.build();
                responseObserver.onNext(response);
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

            Stopwatch postTimer = Stopwatch.createStarted();
            List<Map.Entry<Trgt.Tuple, Double>> postRanking = proj.postRanking(alarms, trace);
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
            {
                CoreUtil.ApplicationResponse response = CoreUtil.ApplicationResponse.newBuilder()
                        .setMsg(String.format(Constants.MSG_SUCC + ": all analyses completed in %s", allTimer))
                        .build();
                responseObserver.onNext(response);
            }
        }
        Messages.log("Core: release all instances for project %s", projId);
        proj.shutdown();
        responseObserver.onCompleted();
    }
}
