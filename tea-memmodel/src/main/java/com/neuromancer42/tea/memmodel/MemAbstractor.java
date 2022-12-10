package com.neuromancer42.tea.memmodel;

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

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class MemAbstractor extends ProviderGrpc.ProviderImplBase {
    private final static String NAME_MEMABS = "memory";

    public static void main(String[] args) throws IOException, InterruptedException {
        String configFile = System.getProperty(Constants.OPT_CONFIG);
        if (configFile == null) {
            Messages.error("Core: No configuration file set (set this by '-Dtea.config.path=<path-to-ini>')");
            System.exit(-1);
        }
        INIConfiguration config = new INIConfiguration();
        try (FileReader reader = new FileReader(configFile)) {
            config.read(reader);
        } catch (ConfigurationException | IOException e) {
            Messages.error("Core: Failed to read config in %s", configFile);
            e.printStackTrace(System.err);
            System.exit(-1);
        }

        int mem_port = Integer.parseInt(config.getSection(NAME_MEMABS).getString(Constants.OPT_PORT));

        Server cdtServer = Grpc.newServerBuilderForPort(mem_port, InsecureServerCredentials.create())
                .addService(new MemAbstractor()).build();
        System.err.print("*** cdt server started on port " + mem_port);
        cdtServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down jsouffle server due to JVM shutdown");
            try {
                cdtServer.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** jsouffle server shut down!");
        }));
        cdtServer.awaitTermination();
    }

    /**
     * @param request
     * @param responseObserver
     */
    @Override
    public void getFeature(Analysis.Configs request, StreamObserver<Analysis.ProviderInfo> responseObserver) {
        Analysis.ProviderInfo.Builder infoBuilder = Analysis.ProviderInfo.newBuilder();
        infoBuilder.setName(NAME_MEMABS);
        infoBuilder.addAnalysis(AnalysisUtil.parseAnalysisInfo(CMemoryModel.class));
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
        Analysis.RunResults results;
        if (!request.getAnalysisName().equals(CMemoryModel.name)) {
            results = Analysis.RunResults.newBuilder()
                    .setMsg(Constants.MSG_FAIL + ": analysis not found")
                    .build();
        } else {
            try {
                Analysis.Configs config = request.getOption();
                String workDir = config.getPropertyOrDefault("workdir", "test-out/test-cmemmodel");
                Path workPath = Paths.get(workDir);
                Files.createDirectories(workPath);
                CMemoryModel cMemModel = new CMemoryModel(workPath);
                results = AnalysisUtil.runAnalysis(cMemModel, request);
            } catch (IOException e) {
                Messages.error("MemAbstractor: failed to create working directory for analysis: \n %s");
                results = Analysis.RunResults.newBuilder()
                        .setMsg(Constants.MSG_FAIL + ": analysis execution failed")
                        .build();
            }
        }
        responseObserver.onNext(results);
        responseObserver.onCompleted();
    }
}