package com.neuromancer42.tea.core;

import com.google.common.base.Stopwatch;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.core.analysis.Analysis;
import com.neuromancer42.tea.core.analysis.ProviderGrpc;
import com.neuromancer42.tea.libdai.DAIRuntime;
import io.grpc.*;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class Core {
    public static void main(String[] args) throws InterruptedException, IOException {
        String configFile = null;
        if (args.length > 0) {
            configFile = args[0];
        } else {
            Messages.error("Core: No configuration file set (pass the pass by argument)");
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
        Messages.log("Core: Run with configuration from %s", configFile);
        String root_workdir = Constants.DEFAULT_ROOT_DIR;
        if (args.length > 1)
            root_workdir = args[1];
        Path workPath = Paths.get(root_workdir, Constants.NAME_CORE);
        ProjectBuilder.init(workPath.toString());

        Stopwatch allTimer = Stopwatch.createStarted();

        // TODO: make inference engine running separately
        DAIRuntime.init(workPath);
        Messages.log("Core: initialized LibDAI at %s", allTimer);

        Map<String, ProviderGrpc.ProviderBlockingStub> providerMap = new LinkedHashMap<>();
        for (String providerName : config.getSections()) {
            if (!providerName.equals(Constants.NAME_CORE) && !providerName.equals(Constants.NAME_PROJ)) {
                String host = config.getSection(providerName).getString(Constants.OPT_HOST);
                int port = Integer.parseInt(config.getSection(providerName).getString(Constants.OPT_PORT));
                Messages.log("Core: configured provider %s [%s:%d] at %s", providerName, host, port, allTimer);
                Channel channel = Grpc.newChannelBuilderForAddress(host, port, InsecureChannelCredentials.create())
                        .maxInboundMessageSize(Integer.MAX_VALUE)
                        .build();
                ProviderGrpc.ProviderBlockingStub stub = ProviderGrpc.newBlockingStub(channel);
                providerMap.put(providerName, stub);
            }
        }

        Analysis.Configs.Builder projConfigBuilder = Analysis.Configs.newBuilder();
        Analysis.Configs projConfig = projConfigBuilder.build();
        for (var providerEntry : providerMap.entrySet()) {
            String providerName = providerEntry.getKey();
            ProviderGrpc.ProviderBlockingStub providerStub = providerEntry.getValue();
            Messages.debug("Core: processing provider %s at %s", providerName, providerStub.getChannel().toString());
            ProjectBuilder.g().queryProvider(projConfig, providerStub);
            Messages.log("Core: provider %s registered at %s", providerName, allTimer);
        }
        allTimer.stop();
        Messages.log("Core: all providers registered in %s", allTimer);
        int core_port = Integer.parseInt(config.getSection(Constants.NAME_CORE).getString(Constants.OPT_PORT));
        Server coreServer = Grpc.newServerBuilderForPort(core_port, InsecureServerCredentials.create())
                .addService(new CoreServiceImpl()).build();
        System.err.print("*** core server started on port " + core_port);
        coreServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down core server due to JVM shutdown");
            for (var entry : providerMap.entrySet()) {
                String name = entry.getKey();
                ProviderGrpc.ProviderBlockingStub stub = entry.getValue();
                System.err.println("** shutting down provider " + name);
                Analysis.ShutdownResponse resp = stub.shutdown(Analysis.ShutdownRequest.newBuilder().build());
            }
            try {
                coreServer.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** core server shut down!");
        }));
        coreServer.awaitTermination();
    }
}
