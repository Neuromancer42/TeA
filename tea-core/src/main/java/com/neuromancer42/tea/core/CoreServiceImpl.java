package com.neuromancer42.tea.core;

import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import io.grpc.stub.StreamObserver;
import org.apache.commons.lang3.StringUtils;

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
        } else {
            CoreUtil.ApplicationResponse resp = CoreUtil.ApplicationResponse.newBuilder()
                    .setMsg(Constants.MSG_FAIL + ": failed to build project pipeline on required analyses")
                    .build();
            responseObserver.onNext(resp);
            responseObserver.onCompleted();
            return;
        }

        {
            CoreUtil.ApplicationResponse.Builder respBuilder = CoreUtil.ApplicationResponse.newBuilder();
            if (failMsg != null) {
                respBuilder.setMsg(failMsg);
            } else {
                respBuilder.setMsg(Constants.MSG_SUCC);
                List<String> alarm_rels = request.getAlarmRelList();
                respBuilder.addAllAlarm(proj.printRels(alarm_rels));
            }
            CoreUtil.ApplicationResponse response = respBuilder.build();
            responseObserver.onNext(response);
        }
        responseObserver.onCompleted();
    }
}
