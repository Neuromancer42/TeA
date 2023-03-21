package com.neuromancer42.tea.codemanager.cdt;

import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.analyses.AnalysisUtil;
import com.neuromancer42.tea.core.analysis.Analysis;
import com.neuromancer42.tea.core.analysis.ProviderGrpc;
import com.neuromancer42.tea.core.analysis.Trgt;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import org.apache.commons.cli.*;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class CDTProvider extends ProviderGrpc.ProviderImplBase {
    private static final String NAME_CDT = "cdt";

    public static void init(Path workPath) throws IOException {
        Files.createDirectories(workPath);
        CDTCManager.setDummySysroot(workPath);
    }

    public static void main(String[] args) throws IOException, InterruptedException, ParseException {
        Options options = new Options();
        options.addOption("h", Constants.OPT_HELP, false, "show this message");
        options.addOption("p", Constants.OPT_PORT, true, "listening to port");
        options.addOption("d", Constants.OPT_WORK_DIR, true, "working directory");
        CommandLine cmd = new DefaultParser().parse(options, args);
        if (cmd.hasOption(Constants.OPT_HELP)) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("tea-cdt-codemanager", options);
            return;
        }

        String workDir = cmd.getOptionValue(Constants.OPT_WORK_DIR, Constants.DEFAULT_ROOT_DIR);
        Path workPath = Paths.get(workDir, NAME_CDT);
        CDTProvider.init(workPath);
        System.err.println("*** cdt server works in directory " + workPath.toAbsolutePath());

        int cdt_port = Integer.parseInt(cmd.getOptionValue(Constants.OPT_PORT, Constants.DEFAULT_PORT));

        Server cdtServer = Grpc.newServerBuilderForPort(cdt_port, InsecureServerCredentials.create())
                .maxInboundMessageSize(Integer.MAX_VALUE)
                .addService(new CDTProvider(workPath)).build();
        System.err.println("*** cdt server started on port " + cdt_port);
        cdtServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down cdt server due to JVM shutdown");
            try {
                cdtServer.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** cdt server shut down!");
        }));
        cdtServer.awaitTermination();
    }

    private final Map<String, CDTCManager> managerMap = new HashMap<>();

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void getFeature(Analysis.Configs request, StreamObserver<Analysis.ProviderInfo> responseObserver) {
        Messages.log("CParser: processing getFeature request");
        Analysis.ProviderInfo.Builder infoBuilder = Analysis.ProviderInfo.newBuilder();
        infoBuilder.setName("cdt-codemanager");

        Analysis.AnalysisInfo cmanagerInfo = AnalysisUtil.parseAnalysisInfo(CDTCManager.class);
        infoBuilder.addAnalysis(cmanagerInfo);
        // TODO make it reflectable
        for (String relInfo : CDTCManager.observableRels) {
            infoBuilder.addObservableRel(
                    AnalysisUtil.parseRelInfo(relInfo)
            );
        }
        Analysis.ProviderInfo cdtInfo = infoBuilder.build();
        responseObserver.onNext(cdtInfo);
        responseObserver.onCompleted();
    }

    private final Path workPath;
    public CDTProvider(Path path) {
        workPath = path;
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void runAnalysis(Analysis.RunRequest request, StreamObserver<Analysis.RunResults> responseObserver) {
        Messages.log("CParser: processing runAnalysis request: \n%s", request.toString());
        assert request.getAnalysisName().equals("cmanager");
        Map<String, String> option = request.getOption().getPropertyMap();
        Analysis.RunResults runResults;
        if (!option.containsKey(Constants.OPT_SRC)) {
            String failMsg = String.format("no source file specified in options for analysis %s in project %s", request.getAnalysisName(), request.getProjectId());
            Messages.error("CParser: " + failMsg);
            runResults = Analysis.RunResults.newBuilder()
                    .setMsg(Constants.MSG_FAIL + ": No source file specified")
                    .build();
        } else {
            String projId = request.getProjectId();
            String sourceFile = option.get(Constants.OPT_SRC);
            String cmd = option.getOrDefault(Constants.OPT_SRC_CMD, "");
            Path projPath = workPath.resolve(projId);
            try {
                Files.createDirectories(projPath);
                CDTCManager manager = new CDTCManager(projPath, sourceFile, cmd);
                managerMap.put(projId, manager);
                runResults = AnalysisUtil.runAnalysis(manager, request);
            } catch (IOException e) {
                String failMsg = String.format("failed to create working directory for analysis %s in project %s: %s", request.getAnalysisName(), request.getProjectId(), e.getMessage());
                Messages.error("CParser: " + failMsg);
                runResults = Analysis.RunResults.newBuilder().setMsg(Constants.MSG_FAIL + ": " + failMsg).build();
            }
        }
        responseObserver.onNext(runResults);
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void prove(Analysis.ProveRequest request, StreamObserver<Analysis.ProveResponse> responseObserver) {
        responseObserver.onNext(Analysis.ProveResponse.newBuilder().build());
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void instrument(Analysis.InstrumentRequest request, StreamObserver<Analysis.InstrumentResponse> responseObserver) {
        Messages.log("CParser: processing instrument request");
        Analysis.InstrumentResponse.Builder respBuilder = Analysis.InstrumentResponse.newBuilder();
        CDTCManager cmanager = managerMap.get(request.getProjectId());
        if (cmanager != null) {
            cmanager.setInstrument();
            for (Trgt.Tuple tuple : request.getInstrTupleList()) {
                if (cmanager.getInstrument().instrument(tuple)) {
                    respBuilder.addSuccTuple(tuple);
                }
            }
        } else {
            Messages.error("CParser: project %s not parsed before instrumenting", request.getProjectId());
        }
        responseObserver.onNext(respBuilder.build());
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void test(Analysis.TestRequest request, StreamObserver<Analysis.TestResponse> responseObserver) {
        Analysis.TestResponse.Builder respBuilder = Analysis.TestResponse.newBuilder();
        CDTCManager cmanager = managerMap.get(request.getProjectId());
        if (cmanager != null) {
            Set<Trgt.Tuple> triggered = cmanager.getInstrument().test(request.getArgList());
            respBuilder.addAllTriggeredTuple(triggered);
        } else {
            Messages.error("CParser: project %s not parsed before testing", request.getProjectId());
        }
        responseObserver.onNext(respBuilder.build());
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void shutdown(Analysis.ShutdownRequest request, StreamObserver<Analysis.ShutdownResponse> responseObserver) {
        CDTCManager cmanager = managerMap.remove(request.getProjectId());
        if (cmanager == null) {
            Messages.error("CParser: project %s never parsed before shutdown", request.getProjectId());
        } else {
            cmanager = null;
            Messages.log("CParser: release Manager instance for project %s", request.getProjectId());
        }
        responseObserver.onNext(Analysis.ShutdownResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
