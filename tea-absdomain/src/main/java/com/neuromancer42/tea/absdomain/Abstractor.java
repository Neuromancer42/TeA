package com.neuromancer42.tea.absdomain;

import com.neuromancer42.tea.absdomain.dataflow.InputMarker;
import com.neuromancer42.tea.absdomain.dataflow.IntervalGenerator;
import com.neuromancer42.tea.absdomain.memmodel.PostPointerAnalysis;
import com.neuromancer42.tea.absdomain.memmodel.CMemoryModel;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.analyses.AnalysisUtil;
import com.neuromancer42.tea.core.analysis.Analysis;
import com.neuromancer42.tea.core.analysis.ProviderGrpc;
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
import java.util.concurrent.TimeUnit;

public class Abstractor extends ProviderGrpc.ProviderImplBase {
    private final static String NAME_ABS = "absdomain";
    private static String default_workdir;

    public static void main(String[] args) throws IOException, InterruptedException {
        String configFile = null;
        if (args.length > 0) {
            configFile = args[0];
        } else {
            Messages.error("Abstractor: No configuration file set (pass the path by argument)");
            System.exit(-1);
        }
        INIConfiguration config = new INIConfiguration();
        try (FileReader reader = new FileReader(configFile)) {
            config.read(reader);
        } catch (ConfigurationException | IOException e) {
            Messages.error("Abstractor: Failed to read config in %s", configFile);
            e.printStackTrace(System.err);
            System.exit(-1);
        }
        Messages.log("Abstractor: Run with configuration from %s", configFile);

        default_workdir = Constants.DEFAULT_ROOT_DIR + File.separator + NAME_ABS;
        if (args.length > 1)
            default_workdir = args[1] + File.separator + NAME_ABS;

        int mem_port = Integer.parseInt(config.getSection(NAME_ABS).getString(Constants.OPT_PORT));

        Server memServer = Grpc.newServerBuilderForPort(mem_port, InsecureServerCredentials.create())
                .addService(new Abstractor()).build();
        System.err.print("*** memory server started on port " + mem_port);
        memServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down memory server due to JVM shutdown");
            try {
                memServer.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** memory server shut down!");
        }));
        memServer.awaitTermination();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void getFeature(Analysis.Configs request, StreamObserver<Analysis.ProviderInfo> responseObserver) {
        Messages.log("Abstractor: processing getFeature request");
        Analysis.ProviderInfo.Builder infoBuilder = Analysis.ProviderInfo.newBuilder();
        infoBuilder.setName(NAME_ABS);
        infoBuilder.addAnalysis(AnalysisUtil.parseAnalysisInfo(CMemoryModel.class));
        infoBuilder.addAnalysis(AnalysisUtil.parseAnalysisInfo(PostPointerAnalysis.class));
        infoBuilder.addAnalysis(AnalysisUtil.parseAnalysisInfo(InputMarker.class));
        infoBuilder.addAnalysis(AnalysisUtil.parseAnalysisInfo(IntervalGenerator.class));
        Analysis.ProviderInfo info = infoBuilder.build();
        responseObserver.onNext(info);
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void runAnalysis(Analysis.RunRequest request, StreamObserver<Analysis.RunResults> responseObserver) {
        Messages.log("Abstractor: processing runAnalysis request");
        Analysis.RunResults results;

        Analysis.Configs config = request.getOption();
        String workDir = config.getPropertyOrDefault(Constants.OPT_WORK_DIR,
                default_workdir + File.separator + request.getProjectId() + File.separator + request.getAnalysisName());
        Path workPath = Paths.get(workDir);
        switch (request.getAnalysisName()) {
            case CMemoryModel.name :
                try {
                    Files.createDirectories(workPath);
                    CMemoryModel cMemModel = new CMemoryModel(workPath);
                    results = AnalysisUtil.runAnalysis(cMemModel, request);
                } catch (IOException e) {
                    Messages.error("Abstractor: failed to create working directory for analysis %s: %s", CMemoryModel.name, e.getMessage());
                    e.printStackTrace();
                    results = Analysis.RunResults.newBuilder()
                            .setMsg(Constants.MSG_FAIL + ": analysis execution failed")
                            .build();
                }
                break;
            case PostPointerAnalysis.name:
                try {
                    Files.createDirectories(workPath);
                    PostPointerAnalysis preDataflow = new PostPointerAnalysis(workPath);
                    results = AnalysisUtil.runAnalysis(preDataflow, request);
                } catch (IOException e) {
                    Messages.error("Abstractor: failed to create working directory for analysis %s: %s", PostPointerAnalysis.name, e.getMessage());
                    e.printStackTrace();
                    results = Analysis.RunResults.newBuilder()
                            .setMsg(Constants.MSG_FAIL + ": analysis execution failed")
                            .build();
                }
                break;
            case InputMarker.name:
                try {
                    Files.createDirectories(workPath);
                    InputMarker inputMarker = new InputMarker(workPath);
                    results = AnalysisUtil.runAnalysis(inputMarker, request);
                } catch (IOException e) {
                    Messages.error("Abstractor: failed to create working directory for analysis %s: %s", InputMarker.name, e.getMessage());
                    e.printStackTrace();
                    results = Analysis.RunResults.newBuilder()
                            .setMsg(Constants.MSG_FAIL + ": analysis execution failed")
                            .build();
                }
                break;
            case IntervalGenerator.name:
                try {
                    Files.createDirectories(workPath);
                    IntervalGenerator itvGen = new IntervalGenerator(workPath);
                    results = AnalysisUtil.runAnalysis(itvGen, request);
                } catch (IOException e) {
                    Messages.error("Abstractor: failed to create working directory for analysis %s: %s", IntervalGenerator.name, e.getMessage());
                    e.printStackTrace();
                    results = Analysis.RunResults.newBuilder()
                            .setMsg(Constants.MSG_FAIL + ": analysis execution failed")
                            .build();
                }
                break;
            default:
                results = Analysis.RunResults.newBuilder()
                    .setMsg(Constants.MSG_FAIL + ": analysis not found")
                    .build();
        }
        responseObserver.onNext(results);
        responseObserver.onCompleted();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void shutdown(Analysis.ShutdownRequest request, StreamObserver<Analysis.ShutdownResponse> responseObserver) {
        responseObserver.onNext(Analysis.ShutdownResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}