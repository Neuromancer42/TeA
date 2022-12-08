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

        Map<String, ProviderGrpc.ProviderBlockingStub> providerMap = new LinkedHashMap<>();
        for (String providerName : config.getSections()) {
            if (!providerName.equals(Constants.NAME_CORE)) {
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
        for (ProviderGrpc.ProviderBlockingStub providerStub : providerMap.values()) {
            ProjectBuilder.g().queryProvider(projConfig, providerStub);
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
