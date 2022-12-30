package com.neuromancer42.tea.core;

import com.google.common.base.Stopwatch;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.provenance.Provenance;
import com.neuromancer42.tea.commons.provenance.ProvenanceUtil;
import com.neuromancer42.tea.core.analysis.Trgt;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CoreServiceImpl extends CoreServiceGrpc.CoreServiceImplBase {
    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void runAnalyses(CoreUtil.ApplicationRequest request, StreamObserver<CoreUtil.ApplicationResponse> responseObserver) {
        Messages.log("Core: processing runAnalyses request");
        Stopwatch allTimer = Stopwatch.createStarted();
        {
            CoreUtil.ApplicationResponse.Builder respBuilder = CoreUtil.ApplicationResponse.newBuilder();
            respBuilder.setMsg(String.format("Initial: handling request for file '%s'", request.getSource().getSource()));
            CoreUtil.ApplicationResponse response = respBuilder.build();
            responseObserver.onNext(response);
        }
        Map<String, String> appOption = new LinkedHashMap<>();
        appOption.putAll(request.getOptionMap());
        CoreUtil.Compilation compInfo = request.getSource();
        appOption.put(Constants.OPT_SRC, compInfo.getSource());
        appOption.put(Constants.OPT_SRC_FLAGS, StringUtils.join(compInfo.getFlagList().toArray(new String[0]), " "));

        List<String> schedule = ProjectBuilder.g().scheduleProject(request.getAnalysisList());

        Project proj = null;
        String failMsg = null;
        if (schedule != null) {
            proj = ProjectBuilder.g().buildProject(appOption, schedule);
            if (proj == null) {
                failMsg = Constants.MSG_FAIL + ": exception happens when build project";
            } else {
                {
                    CoreUtil.ApplicationResponse.Builder respBuilder = CoreUtil.ApplicationResponse.newBuilder();
                    respBuilder.setMsg(String.format(Constants.MSG_SUCC + ": complete analysis schedule [%s] in %s", StringUtils.join(schedule.toArray(new String[0]), ","), allTimer));
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
            responseObserver.onCompleted();
            return;
        }

        List<String> alarm_rels = request.getAlarmRelList();
        if (!request.getNeedRank()) {
            CoreUtil.ApplicationResponse.Builder respBuilder = CoreUtil.ApplicationResponse.newBuilder();
            respBuilder.addAllAlarm(proj.printRels(alarm_rels));

            respBuilder.setMsg(String.format(Constants.MSG_SUCC + ": all analyses completed in %s", allTimer));
            CoreUtil.ApplicationResponse response = respBuilder.build();
            responseObserver.onNext(response);
        } else {
            Stopwatch provTimer = Stopwatch.createStarted();
            Trgt.Provenance prov = proj.proveRels(alarm_rels);
            ProvenanceUtil.dumpProvenance(prov, proj.getWorkDir());
            provTimer.stop();
            {
                CoreUtil.ApplicationResponse.Builder respBuilder = CoreUtil.ApplicationResponse.newBuilder();
                respBuilder.setMsg(String.format(Constants.MSG_SUCC + ": proof succeeds in %s", allTimer));
                CoreUtil.ApplicationResponse response = respBuilder.build();
                responseObserver.onNext(response);
            }
            {
                CoreUtil.ApplicationResponse.Builder respBuilder = CoreUtil.ApplicationResponse.newBuilder();
                respBuilder.addAllAlarm(proj.printRels(alarm_rels));
                respBuilder.setMsg(String.format(Constants.MSG_SUCC + ": all analyses completed in %s", allTimer));
                CoreUtil.ApplicationResponse response = respBuilder.build();
                responseObserver.onNext(response);
            }
        }
        responseObserver.onCompleted();
    }
}
