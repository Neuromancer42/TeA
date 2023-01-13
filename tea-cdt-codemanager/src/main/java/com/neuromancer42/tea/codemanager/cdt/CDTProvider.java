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
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.File;
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

    public static void main(String[] args) throws IOException, InterruptedException {
        String configFile = null;
        if (args.length > 0) {
            configFile = args[0];
        } else {
            Messages.error("CParser: No configuration file set (pass the pass by argument)");
            System.exit(-1);
        }
        INIConfiguration config = new INIConfiguration();
        try (FileReader reader = new FileReader(configFile)) {
            config.read(reader);
        } catch (ConfigurationException | IOException e) {
            Messages.error("CParser: Failed to read config in %s", configFile);
            e.printStackTrace(System.err);
            System.exit(-1);
        }
        Messages.log("CParser: Run with configuration from %s", configFile);


        String workDir = Constants.DEFAULT_ROOT_DIR;
        if (args.length > 1)
            workDir = args[1];
        Path workPath = Paths.get(workDir, NAME_CDT);
        CDTProvider.init(workPath);

        int cdt_port = Integer.parseInt(config.getSection(NAME_CDT).getString(Constants.OPT_PORT));

        Server cdtServer = Grpc.newServerBuilderForPort(cdt_port, InsecureServerCredentials.create())
                .addService(new CDTProvider(workPath)).build();
        System.err.print("*** cdt server started on port " + cdt_port);
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

    private CDTCManager cmanager;

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void getFeature(Analysis.Configs request, StreamObserver<Analysis.ProviderInfo> responseObserver) {
        Messages.log("CParser: processing getFeature request");
        Analysis.ProviderInfo.Builder infoBuilder = Analysis.ProviderInfo.newBuilder();
        infoBuilder.setName("cdt-codemanager");
        Analysis.AnalysisInfo.Builder analysisBuilder = Analysis.AnalysisInfo.newBuilder();

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
        Messages.log("CParser: processing runAnalysis request");
        assert request.getAnalysisName().equals("cmanager");
        Map<String, String> option = request.getOption().getPropertyMap();
        Analysis.RunResults runResults;
        if (!option.containsKey(Constants.OPT_SRC)) {
            runResults = Analysis.RunResults.newBuilder()
                    .setMsg(Constants.MSG_FAIL + ": No source file specified")
                    .build();
        } else {
            String sourceFile = option.get(Constants.OPT_SRC);
            String cmd = option.getOrDefault(Constants.OPT_SRC_CMD, "");
            cmanager = new CDTCManager(workPath, sourceFile, cmd);
            runResults = AnalysisUtil.runAnalysis(cmanager, request);
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
        cmanager.setInstrument();
        for (Trgt.Tuple tuple : request.getInstrTupleList()) {
            if (cmanager.getInstrument().instrument(tuple)) {
                respBuilder.addSuccTuple(tuple);
            }
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
        Set<Trgt.Tuple> triggered = cmanager.getInstrument().test(request.getArgList());
        respBuilder.addAllTriggeredTuple(triggered);
        responseObserver.onNext(respBuilder.build());
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void shutdown(Analysis.ShutdownRequest request, StreamObserver<Analysis.ShutdownResponse> responseObserver) {
        Messages.log("*** shutting down cdt server due to core request");
        responseObserver.onNext(Analysis.ShutdownResponse.getDefaultInstance());
        responseObserver.onCompleted();
        System.exit(0);
    }
}
