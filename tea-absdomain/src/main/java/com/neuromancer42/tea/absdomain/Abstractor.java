package com.neuromancer42.tea.absdomain;

import com.neuromancer42.tea.absdomain.memmodel.LLVMMemoryModel;
import com.neuromancer42.tea.absdomain.misc.ExtMethMarker;
import com.neuromancer42.tea.absdomain.misc.Cardinals;
import com.neuromancer42.tea.absdomain.interval.IntervalGenerator;
import com.neuromancer42.tea.absdomain.memmodel.PostPointerAnalysis;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.analyses.AnalysisUtil;
import com.neuromancer42.tea.core.analysis.Analysis;
import com.neuromancer42.tea.core.analysis.ProviderGrpc;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import org.apache.commons.cli.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class Abstractor extends ProviderGrpc.ProviderImplBase {
    private final static String NAME_ABS = "absdomain";
    private static String default_workdir;

    public static void main(String[] args) throws IOException, InterruptedException, ParseException {
        Options options = new Options();
        options.addOption("h", Constants.OPT_HELP, false, "show this message");
        options.addOption("p", Constants.OPT_PORT, true, "listening to port");
        options.addOption("d", Constants.OPT_WORK_DIR, true, "working directory");
        CommandLine cmd = new DefaultParser().parse(options, args);
        if (cmd.hasOption(Constants.OPT_HELP)) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("tea-absdomain", options);
            return;
        }

        default_workdir = cmd.getOptionValue(Constants.OPT_WORK_DIR, Constants.DEFAULT_ROOT_DIR) + File.separator + NAME_ABS;
        System.err.println("*** abstractor server works in directory " + Paths.get(default_workdir).toAbsolutePath());

        int mem_port = Integer.parseInt(cmd.getOptionValue(Constants.OPT_PORT, Constants.DEFAULT_PORT));

        Server memServer = Grpc.newServerBuilderForPort(mem_port, InsecureServerCredentials.create())
                .maxInboundMessageSize(Integer.MAX_VALUE)
                .addService(new Abstractor()).build();
        System.err.println("*** abstractor server started on port " + mem_port);
        memServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down abstractor server due to JVM shutdown");
            try {
                memServer.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** abstractor server shut down!");
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
        infoBuilder.addAnalysis(AnalysisUtil.parseAnalysisInfo(LLVMMemoryModel.class));
        infoBuilder.addAnalysis(AnalysisUtil.parseAnalysisInfo(PostPointerAnalysis.class));
        infoBuilder.addAnalysis(AnalysisUtil.parseAnalysisInfo(ExtMethMarker.class));
        infoBuilder.addAnalysis(AnalysisUtil.parseAnalysisInfo(IntervalGenerator.class));
        infoBuilder.addAnalysis(AnalysisUtil.parseAnalysisInfo(Cardinals.class));
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
            case LLVMMemoryModel.name :
                try {
                    Files.createDirectories(workPath);
                    LLVMMemoryModel llvmMemModel = new LLVMMemoryModel(workPath);
                    results = AnalysisUtil.runAnalysis(llvmMemModel, request);
                } catch (IOException e) {
                    Messages.error("Abstractor: failed to create working directory for analysis %s: %s", LLVMMemoryModel.name, e.getMessage());
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
            case ExtMethMarker.name:
                try {
                    Files.createDirectories(workPath);
                    ExtMethMarker extMethMarker = new ExtMethMarker(workPath);
                    results = AnalysisUtil.runAnalysis(extMethMarker, request);
                } catch (IOException e) {
                    Messages.error("Abstractor: failed to create working directory for analysis %s: %s", ExtMethMarker.name, e.getMessage());
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
            case Cardinals.name:
                try {
                    Files.createDirectories(workPath);
                    Cardinals card = new Cardinals(workPath);
                    results = AnalysisUtil.runAnalysis(card, request);
                } catch (IOException e) {
                    Messages.error("Abstractor: failed to create working directory for analysis %s: %s", Cardinals.name, e.getMessage());
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