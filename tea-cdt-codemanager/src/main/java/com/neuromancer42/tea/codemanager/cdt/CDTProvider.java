package com.neuromancer42.tea.codemanager.cdt;

import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class CDTProvider {
    private static final String NAME_CDT = "cdt";
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

        String workDir = config.getSection(NAME_CDT).getString(Constants.OPT_WORK_DIR);
        CDTProvider.init(workDir);

        int cdt_port = Integer.parseInt(config.getSection(NAME_CDT).getString(Constants.OPT_PORT));

        Server cdtServer = Grpc.newServerBuilderForPort(cdt_port, InsecureServerCredentials.create())
                .addService(new CDTProviderImpl()).build();
        System.err.print("*** cdt server started on port " + cdt_port);
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

    private static CDTProvider provider = null;

    public static CDTProvider g() {
        if (provider == null) {
            Messages.fatal("CDTProvider: souffle runtime not initialized yet!");
            assert false;
        }
        return provider;
    }

    public static void init(String workDir) throws IOException {
        Files.createDirectories(Paths.get(workDir));
    }

    private final Path workPath;

    public CDTProvider(Path path) {
        this.workPath = path;
    }

    public Path getWorkPath() {
        return workPath;
    }
}
