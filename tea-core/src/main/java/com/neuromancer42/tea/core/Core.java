package com.neuromancer42.tea.core;

import com.google.common.base.Stopwatch;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.inference.Categorical01;
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
import java.nio.file.Files;
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
        options.addOption("t", Constants.OPT_DIST, true, "[optional] path to list of derivation prior params");
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

        Map<String, Categorical01> distMap = new LinkedHashMap<>();
        Categorical01 defaultDist = new Categorical01(0.5, 1.0);
        if (cmd.hasOption(Constants.OPT_DIST)) {
            List<String> distLines = Files.readAllLines(Paths.get(cmd.getOptionValue(Constants.OPT_DIST)));
            for (String distLine : distLines) {
                String[] tokens = distLine.split("\t");
                String relName = tokens[0];
                double[] supports = new double[tokens.length-1];
                double[] weights = new double[tokens.length-1];
                // TODO: for now, we suppose all prior params has equal probability
                for (int i = 0; i  < supports.length; ++i) {
                    String[] token = tokens[i+1].split(":");
                    supports[i] = Double.parseDouble(token[0]);
                    if (token.length > 1)
                        weights[i] = Double.parseDouble(token[1]);
                    else
                        weights[i] = 1;
                }
                Categorical01 dist = new Categorical01(supports, weights);
                if (relName.equals("default")) {
                    defaultDist = dist;
                    Messages.log("Core: set default prior distribution for rels as: %s", dist.toString());
                } else {
                    distMap.put(relName, dist);
                    Messages.log("Core: set prior distribution of rel %s (or its derivation) as: %s", relName, dist.toString());
                }
            }
        }

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
        var core_impl = new CoreServiceImpl(distMap, defaultDist);
        Server coreServer = Grpc.newServerBuilderForPort(core_port, InsecureServerCredentials.create())
                .addService(core_impl).build();
        System.err.println("*** core server started on port " + core_port);
        coreServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down core server due to JVM shutdown");
            for (var proj : core_impl.projects) {
                for (var entry : providerMap.entrySet()) {
                    String name = entry.getKey();
                    ProviderGrpc.ProviderBlockingStub stub = entry.getValue();
                    System.err.println("** release project " + proj + " on provider " + name);
                    Analysis.ShutdownResponse resp = stub.shutdown(Analysis.ShutdownRequest.newBuilder().setProjectId(proj).build());
                }
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
