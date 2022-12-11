package com.neuromancer42.tea.core;

import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.core.analysis.Analysis;
import com.neuromancer42.tea.core.analysis.ProviderGrpc;
import io.grpc.*;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.FileReader;
import java.io.IOException;
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
        String core_workdir = config.getSection(Constants.NAME_CORE).getString(Constants.OPT_WORK_DIR);
        ProjectBuilder.init(core_workdir);

        Map<String, ProviderGrpc.ProviderBlockingStub> providerMap = new LinkedHashMap<>();
        for (String providerName : config.getSections()) {
            if (!providerName.equals(Constants.NAME_CORE) && !providerName.equals(Constants.NAME_PROJ)) {
                String host = config.getSection(providerName).getString(Constants.OPT_HOST);
                int port = Integer.parseInt(config.getSection(providerName).getString(Constants.OPT_PORT));
                Messages.log("Core: configured provider %s at [%s:%d]", providerName, host, port);
                Channel channel = Grpc.newChannelBuilderForAddress(host, port, InsecureChannelCredentials.create()).build();
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
            Messages.log("Core: provider %s registered", providerName);
        }

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
