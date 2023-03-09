package com.neuromancer42.tea.core;

import com.google.common.base.Stopwatch;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.core.analysis.Analysis;
import com.neuromancer42.tea.core.analysis.ProviderGrpc;
import com.neuromancer42.tea.libdai.DAIRuntime;
import io.grpc.*;
import org.apache.commons.cli.*;
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
    private static final String OPT_PROVIDERS = "providers";
    public static void main(String[] args) throws InterruptedException, IOException, ParseException {
        Options options = new Options();
        options.addOption("h", Constants.OPT_HELP, false, "show this message");
        options.addOption("p", Constants.OPT_PORT, true, "listening to port");
        options.addOption("d", Constants.OPT_WORK_DIR, true, "working directory");
        options.addOption(Option.builder("Q")
                .longOpt(OPT_PROVIDERS)
                .hasArgs()
                .valueSeparator('=')
                .desc("analyses providers with address")
                .build());
        CommandLine cmd = new DefaultParser().parse(options, args);
        if (cmd.hasOption(Constants.OPT_HELP)) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("tea-core", options);
            return;
        }

        String root_workdir = cmd.getOptionValue(Constants.OPT_WORK_DIR, Constants.DEFAULT_ROOT_DIR);
        Path workPath = Paths.get(root_workdir, Constants.NAME_CORE);
        ProjectBuilder.init(workPath.toString());
        System.err.println("*** core server works in directory " + workPath.toAbsolutePath());

        Stopwatch allTimer = Stopwatch.createStarted();

        // TODO: make inference engine running separately
        DAIRuntime.init(workPath);
        Messages.log("Core: initialized LibDAI at %s", allTimer);

        Map<String, ProviderGrpc.ProviderBlockingStub> providerMap = new LinkedHashMap<>();
        for (var entry : cmd.getOptionProperties(OPT_PROVIDERS).entrySet()) {
            String providerName = (String) entry.getKey();
            String addr = (String) entry.getValue();
            Messages.log("Core: configured provider %s [%s] at %s", providerName, addr, allTimer);
            Channel channel = Grpc.newChannelBuilder(addr, InsecureChannelCredentials.create())
                    .maxInboundMessageSize(Integer.MAX_VALUE)
                    .build();
            ProviderGrpc.ProviderBlockingStub stub = ProviderGrpc.newBlockingStub(channel);
            providerMap.put(providerName, stub);
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
        int core_port = Integer.parseInt(cmd.getOptionValue(Constants.OPT_PORT, Constants.DEFAULT_PORT));
        Server coreServer = Grpc.newServerBuilderForPort(core_port, InsecureServerCredentials.create())
                .addService(new CoreServiceImpl()).build();
        System.err.println("*** core server started on port " + core_port);
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
