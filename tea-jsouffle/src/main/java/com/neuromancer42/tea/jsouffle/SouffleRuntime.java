package com.neuromancer42.tea.jsouffle;

import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.commons.provenance.ConstraintItem;
import com.neuromancer42.tea.commons.provenance.Provenance;
import com.neuromancer42.tea.commons.provenance.RawTuple;
import com.neuromancer42.tea.commons.util.Timer;
import com.neuromancer42.tea.core.analysis.Analysis;
import com.neuromancer42.tea.core.analysis.ProviderGrpc;
import com.neuromancer42.tea.core.analysis.Trgt;
import com.neuromancer42.tea.jsouffle.swig.SWIGSouffleProgram;
import com.neuromancer42.tea.jsouffle.swig.SwigInterface;
import io.grpc.Grpc;
import io.grpc.InsecureServerCredentials;
import io.grpc.Server;
import io.grpc.stub.StreamObserver;
import org.apache.commons.configuration2.INIConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.commons.lang3.SystemUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.TimeUnit;


public final class SouffleRuntime {

    static private final String BUILD_DIR_NAME = "build";
    static private final String CACHE_DIR_NAME = "cache";
    private static final String NAME_SOUFFLE = "souffle";
    private static final String PREFIX_SOUFFLE = "analysis-";

    public static void main(String[] args) throws IOException, InterruptedException {
        String configFile = null;
        if (args.length > 0) {
            configFile = args[0];
        } else {
            Messages.error("SouffleRuntime: No configuration file set (pass the path by argument)");
            System.exit(-1);
        }
        INIConfiguration config = new INIConfiguration();
        try (FileReader reader = new FileReader(configFile)) {
            config.read(reader);
        } catch (ConfigurationException | IOException e) {
            Messages.error("SouffleRuntime: Failed to read config in %s", configFile);
            e.printStackTrace(System.err);
            System.exit(-1);
        }
        Messages.log("SouffleRuntime: Run with configuration from %s", configFile);

        Map<String, SouffleAnalysis> analysisMap = new LinkedHashMap<>();
        String workDir = config.getSection(NAME_SOUFFLE).getString(Constants.OPT_WORK_DIR);
        SouffleRuntime.init(Paths.get(workDir));
        for (Iterator<String> it = config.getSection(NAME_SOUFFLE).getKeys(); it.hasNext(); ) {
            String key = it.next();
            if (!key.startsWith(PREFIX_SOUFFLE))
                continue;
            String analysisName = key.substring(PREFIX_SOUFFLE.length());
            String dlog = config.getSection(NAME_SOUFFLE).getString(key);
            File dlogFile = new File(dlog);
            SouffleAnalysis analysis = runtime.createSouffleAnalysisFromFile(analysisName, analysisName, dlogFile);
            Messages.log("SouffleRuntime: created souffle analysis %s from dlog %s", analysisName, dlogFile.getAbsolutePath());
            analysisMap.put(analysisName, analysis);
        }
        int souffle_port = Integer.parseInt(config.getSection(NAME_SOUFFLE).getString(Constants.OPT_PORT));

        Server jSouffleServer = Grpc.newServerBuilderForPort(souffle_port, InsecureServerCredentials.create())
                .addService(new SouffleProvider(analysisMap)).build();
        System.err.print("*** jsouffle server started on port " + souffle_port);
        jSouffleServer.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.err.println("*** shutting down jsouffle server due to JVM shutdown");
            try {
                jSouffleServer.shutdown().awaitTermination(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace(System.err);
            }
            System.err.println("*** jsouffle server shut down!");
        }));
        jSouffleServer.awaitTermination();
    }

    private static SouffleRuntime runtime = null;

    public static SouffleRuntime g() {
        if (runtime == null) {
            Messages.fatal("SouffleRuntime: souffle runtime not initialized yet!");
            assert false;
        }
        return runtime;
    }

    public static void init(Path baseWorkDirPath) {
        Timer timer = new Timer(NAME_SOUFFLE);
        Messages.log("ENTER: Souffle Runtime Initialization started at " + (new Date()));
        timer.init();
        if (runtime != null) {
            Messages.warn("SouffleRuntime: runtime has been built before, are you sure to rebuild it?");
        }

        // 1. New runtime instance, setting paths and toolchains
        try {
            Files.createDirectories(baseWorkDirPath.resolve(BUILD_DIR_NAME));
            Files.createDirectories(baseWorkDirPath.resolve(CACHE_DIR_NAME));
        } catch (IOException e) {
            Messages.error("SouffleRuntime: failed to create working directory");
            Messages.fatal(e);
        }
        runtime = new SouffleRuntime(baseWorkDirPath);
        try {
            // 0. copy files from bundles
            {
                InputStream souffleCMakeStream = SouffleRuntime.class.getResourceAsStream("swig/CMakeLists.txt");
                Path souffleCMakePath = runtime.buildPath.resolve("CMakeLists.txt");
                assert souffleCMakeStream != null;
                Files.copy(souffleCMakeStream, souffleCMakePath, StandardCopyOption.REPLACE_EXISTING);
                InputStream souffleSrcStream = SouffleRuntime.class.getResourceAsStream("swig/souffle-swig-interface_wrap.cxx");
                Path souffleSrcPath = runtime.buildPath.resolve("souffle-swig-interface_wrap.cxx");
                assert souffleSrcStream != null;
                Files.copy(souffleSrcStream, souffleSrcPath, StandardCopyOption.REPLACE_EXISTING);
                InputStream souffleHeaderStream = SouffleRuntime.class.getResourceAsStream("swig/souffle-swig-interface.h");
                Path souffleHeaderPath = runtime.buildPath.resolve("souffle-swig-interface.h");
                assert souffleHeaderStream != null;
                Files.copy(souffleHeaderStream, souffleHeaderPath, StandardCopyOption.REPLACE_EXISTING);
            }

            // 1. get target name for specific OS
            String libraryFileName;
            if (SystemUtils.IS_OS_MAC_OSX) {
                libraryFileName = "lib"+"souffle"+".dylib";
            } else {
                throw new RuntimeException("Not supported yet!");
            }

            // 2. build native library and install it to workdir
            // we do not cache the generated targets, as cmake takes this responsibility
            Path cmakeDir = Files.createDirectories(runtime.buildPath.resolve("cmake-build"));
            {
                List<String> cmakeCmd = new ArrayList<>();
                cmakeCmd.add("cmake");
                cmakeCmd.add("-DBUILD_WRAPPER=On");
                if (System.getProperty("java.home") != null)
                    cmakeCmd.add("-DJAVA_HOME=" + System.getProperty("java.home"));
                cmakeCmd.add("..");
                executeExternal(cmakeCmd, cmakeDir);
            }
            {
                List<String> makeCmd = new ArrayList<>();
                makeCmd.add("make");
                executeExternal(makeCmd, cmakeDir);
            }
            {
                List<String> installCmd = new ArrayList<>();
                installCmd.add("cmake");
                installCmd.add("--install");
                installCmd.add(".");
                installCmd.add("--prefix");
                installCmd.add(runtime.buildPath.toAbsolutePath().toString());
                executeExternal(installCmd, cmakeDir);
            }


            // 3. load library
            Path souffleJNIPath = runtime.buildPath.resolve("native").resolve(libraryFileName);
            System.load(souffleJNIPath.toAbsolutePath().toString());
            Messages.debug("SouffleRuntime: souffle runtime has been loaded");
        } catch (IOException | RuntimeException | InterruptedException e) {
            Messages.error("SouffleRuntime: failed to initialize souffle runtime.");
            Messages.fatal(e);
        }
        timer.done();
        Messages.log("LEAVE: Souffle Runtime Initialization finished");
        Timer.printTimer(timer);
    }

    private static void executeExternal(List<String> cmd, Path path) throws IOException, InterruptedException {
        ProcessBuilder cmakeBuilder = new ProcessBuilder(cmd);
        cmakeBuilder.directory(path.toFile());
        Process cmakeProcess = cmakeBuilder.start();
        int cmakeRetVal = cmakeProcess.waitFor();
        if (cmakeRetVal != 0) {
            String outputStr = new String(cmakeProcess.getInputStream().readAllBytes());
            outputStr = outputStr.replace("%", "%%");
            Messages.error("CParser: " + outputStr);
            String errString = new String(cmakeProcess.getErrorStream().readAllBytes());
            throw new RuntimeException(errString);
        }
    }

    public Path getBuildPath() {
        return buildPath;
    }

    private final Path buildPath;

    public Path getCachePath() {
        return cachePath;
    }

    private final Path cachePath;

    public Set<String> getLoadedLibraries() {
        return loadedLibraries;
    }

    private final Set<String> loadedLibraries;
    private final Map<String, String> loadedProvenances;

    public SouffleRuntime(Path rootDir) {
        this.buildPath = rootDir.resolve(BUILD_DIR_NAME);
        this.cachePath = rootDir.resolve(CACHE_DIR_NAME);
        loadedLibraries = new HashSet<>();
        loadedProvenances = new HashMap<>();
    }

    private synchronized void loadDlog(String analysis, InputStream dlogStream, boolean withProvenance, boolean withDebug) {
        if (loadedLibraries.contains(analysis)) {
            Messages.warn("SouffleRuntime: analysis %s has been loaded before!", analysis);
            return;
        }
        try {
            // 0. copy files
            String dlogFileName = analysis + ".dl";
            Path dlogFilePath = buildPath.resolve(dlogFileName);
            Messages.debug("SouffleRuntime: dumping dlog to file " + dlogFilePath);
            Files.copy(dlogStream, dlogFilePath, StandardCopyOption.REPLACE_EXISTING);

            // 1. get target name for specific OS
            String provenance = analysis + "_wP";
            String analysisLibName;
            String provLibName;
            if (SystemUtils.IS_OS_MAC_OSX) {
                analysisLibName = "lib"+analysis+".dylib";
                provLibName = "lib"+provenance+".dylib";
            } else {
                throw new RuntimeException("Not supported yet!");
            }

            // 1. build native library and load it
            Path cmakeDir = buildPath.resolve("cmake-build");
            {
                List<String> cmakeCmd = new ArrayList<>();
                cmakeCmd.add("cmake");
                cmakeCmd.add("-DBUILD_WRAPPER=Off");
                cmakeCmd.add("-DANALYSIS_NAME="+analysis);
                if (withDebug) {
                    cmakeCmd.add("-DENABLE_EXE=On");
                } else {
                    cmakeCmd.add("-DENABLE_EXE=Off");
                }
                if (withProvenance) {
                    cmakeCmd.add("-DENABLE_PROVENANCE=On");
                } else {
                    cmakeCmd.add("-DENABLE_PROVENANCE=Off");
                }
                if (System.getProperty("java.home") != null)
                    cmakeCmd.add("-DJAVA_HOME=" + System.getProperty("java.home"));
                cmakeCmd.add("..");
                executeExternal(cmakeCmd, cmakeDir);
            }
            {
                List<String> makeCmd = new ArrayList<>();
                makeCmd.add("make");
                executeExternal(makeCmd, cmakeDir);
            }
            {
                List<String> installCmd = new ArrayList<>();
                installCmd.add("cmake");
                installCmd.add("--install");
                installCmd.add(".");
                installCmd.add("--prefix");
                installCmd.add(runtime.buildPath.toAbsolutePath().toString());
                executeExternal(installCmd, cmakeDir);
            }

            // 3. load library
            Path analysisLibPath = runtime.buildPath.resolve("native").resolve(analysisLibName);
            System.load(analysisLibPath.toAbsolutePath().toString());
            Messages.debug("SouffleRuntime: analysis runtime %s has been loaded", analysisLibName);
            loadedLibraries.add(analysis);
            if (withProvenance) {
                Path provLibPath = runtime.buildPath.resolve("native").resolve(provLibName);
                System.load(provLibPath.toAbsolutePath().toString());
                Messages.debug("SouffleRuntime: provenance runtime %s has been loaded", provLibName);
                loadedProvenances.put(analysis, provenance);
            }
        } catch (IOException | InterruptedException e) {
            Messages.error("SouffelRuntime: failed to compile runtime of analysis %s", analysis);
            Messages.fatal(e);
        }
    }

    public boolean hasLoaded(String analysis) {
        return loadedLibraries.contains(analysis);
    }

    public String hasLoadedProvenance(String analysis) {
        return loadedProvenances.get(analysis);
    }

    public SouffleAnalysis createSouffleAnalysisFromFile(String name, String analysis, File dlogFile) {
        SouffleAnalysis ret = null;
        try {
            ret = createSouffleAnalysisFromStream(name, analysis, new FileInputStream(dlogFile));
        } catch (FileNotFoundException e) {
            Messages.error("SouffleRuntime: the referenced dlog file of analysis %s does not exists!", name, analysis);
        }
        return ret;
    }

    public SouffleAnalysis createSouffleAnalysisFromStream(String name, String analysis, InputStream dlogStream) {
        if (hasLoaded(analysis)) {
            Messages.warn("SouffleRuntime: the analysis %s has been loaded before!");
        } else {
            loadDlog(analysis, dlogStream, true, false);
        }
        return createSouffleAnalysis(name, analysis);
    }

    public synchronized SouffleAnalysis createSouffleAnalysis(String name, String analysis) {
        if (!hasLoaded(analysis)) {
            Messages.fatal("SouffleRuntime: the analysis %s has not been loaded yet!");
        }
        SWIGSouffleProgram program = SwigInterface.newInstance(analysis);
        if (program == null) {
            Messages.fatal("SouffleRuntime: failed to create instance of analysis %s", analysis);
        }
        SWIGSouffleProgram provProgram = null;
        String provName = hasLoadedProvenance(analysis);
        if (provName != null) {
            provProgram = SwigInterface.newInstance(provName);
        }
        return new SouffleAnalysis(name, analysis, cachePath.resolve(name), program, provProgram);
    }

    private static class SouffleProvider extends ProviderGrpc.ProviderImplBase {
        private final Map<String, SouffleAnalysis> analysisMap;
        private final Map<String, SouffleAnalysis> relNameToProducers = new HashMap<>();
        public SouffleProvider(Map<String, SouffleAnalysis> analysisMap) {
            this.analysisMap = analysisMap;
        }

        /**
         * @param request
         * @param responseObserver
         */
        @Override
        public void getFeature(Analysis.Configs request, StreamObserver<Analysis.ProviderInfo> responseObserver) {
            Messages.log("MemAbstractor: processing getFeature request");
            Analysis.ProviderInfo.Builder respBuilder = Analysis.ProviderInfo.newBuilder();
            respBuilder.setName(NAME_SOUFFLE);
            List<Trgt.RelInfo> outputRels = new ArrayList<>();
            for (var entry : analysisMap.entrySet()) {
                Analysis.AnalysisInfo.Builder analysisInfoBuilder = Analysis.AnalysisInfo.newBuilder();
                String name = entry.getKey();
                analysisInfoBuilder.setName(name);
                SouffleAnalysis analysis = entry.getValue();
                for (String domName : analysis.getAllDomKinds()) {
                    Trgt.DomInfo domInfo = Trgt.DomInfo.newBuilder()
                            .setName(domName)
                            .setDescription("declared dom in souffle analysis " + analysis.getName())
                            .build();
                    analysisInfoBuilder.addConsumingDom(domInfo);
                }
                for (var inputEntry : analysis.getInputRels().entrySet()) {
                    String relName = inputEntry.getKey();
                    String[] domKinds = inputEntry.getValue();
                    Trgt.RelInfo relInfo = Trgt.RelInfo.newBuilder()
                            .setName(relName)
                            .addAllDom(List.of(domKinds))
                            .setDescription("declared input rel in souffle analysis " + analysis.getName())
                            .build();
                    analysisInfoBuilder.addConsumingRel(relInfo);
                }
                for (var outputEntry : analysis.getOutputRels().entrySet()) {
                    String relName = outputEntry.getKey();
                    String[] domKinds = outputEntry.getValue();
                    Trgt.RelInfo relInfo = Trgt.RelInfo.newBuilder()
                            .setName(relName)
                            .addAllDom(List.of(domKinds))
                            .setDescription("declared input rel in souffle analysis " + analysis.getName())
                            .build();
                    analysisInfoBuilder.addProducingRel(relInfo);

                    outputRels.add(relInfo);
                }
                respBuilder.addAnalysis(analysisInfoBuilder.build());
            }
            respBuilder.addAllProvableRel(outputRels);

            Analysis.ProviderInfo info = respBuilder.build();
            responseObserver.onNext(info);
            responseObserver.onCompleted();
        }

        /**
         * @param request
         * @param responseObserver
         */
        @Override
        public void runAnalysis(Analysis.RunRequest request, StreamObserver<Analysis.RunResults> responseObserver) {
            Messages.log("MemAbstractor: processing getFeature request");
            SouffleAnalysis analysis = analysisMap.get(request.getAnalysisName());
            Analysis.RunResults.Builder respBuilder = Analysis.RunResults.newBuilder();
            if (analysis == null) {
                respBuilder.setMsg(Constants.MSG_FAIL + ": analysis not found");
            } else {
                Map<String, ProgramDom> domMap = new HashMap<>();
                for (Trgt.DomTrgt domTrgt : request.getDomInputList()) {
                    String domName = domTrgt.getInfo().getName();
                    String domLoc = domTrgt.getLocation();
                    ProgramDom dom = new ProgramDom(domName);
                    dom.load(domLoc);
                    domMap.put(domName, dom);
                }

                Map<String, ProgramRel> inputRelMap = new HashMap<>();
                for (Trgt.RelTrgt relTrgt : request.getRelInputList()) {
                    String relName = relTrgt.getInfo().getName();
                    List<ProgramDom> doms = new ArrayList<>();
                    for (String domName : relTrgt.getInfo().getDomList()) {
                        doms.add(domMap.get(domName));
                    }
                    String relLoc = relTrgt.getLocation();
                    ProgramRel rel = new ProgramRel(relName, doms.toArray(new ProgramDom[0]));
                    rel.attach(relLoc);
                    inputRelMap.put(relName, rel);
                }

                Collection<ProgramRel> outputRels = analysis.run(domMap, inputRelMap);
                for (ProgramRel rel : outputRels) {

                    String relName = rel.getName();
                    String[] domKinds = rel.getSign().getDomKinds();
                    Trgt.RelInfo relInfo = Trgt.RelInfo.newBuilder()
                            .setName(relName)
                            .addAllDom(List.of(domKinds))
                            .setDescription("generated by souffle analysis " + analysis.getName())
                            .build();

                    Trgt.RelTrgt relTrgt = Trgt.RelTrgt.newBuilder()
                            .setInfo(relInfo)
                            .setLocation(rel.getLocation())
                            .build();
                    respBuilder.addRelOutput(relTrgt);

                    // side work: mark producer for future provenancce
                    relNameToProducers.put(relName, analysis);
                }
                respBuilder.setMsg(Constants.MSG_SUCC);
            }
            Analysis.RunResults results = respBuilder.build();
            responseObserver.onNext(results);
            responseObserver.onCompleted();
        }

        /**
         * @param request
         * @param responseObserver
         */
        @Override
        public void prove(Analysis.ProveRequest request, StreamObserver<Analysis.ProveResponse> responseObserver) {
            Analysis.ProveResponse.Builder respBuilder = Analysis.ProveResponse.newBuilder();
            Map<String, List<String[]>> queries = new LinkedHashMap<>();
            for (Trgt.Tuple target : request.getTargetTupleList()) {
                String relName = target.getRelName();
                queries.computeIfAbsent(relName, r -> new ArrayList<>()).add(target.getAttributeList().toArray(new String[0]));
            }
            Map<SouffleAnalysis, List<Map.Entry<String, List<String[]>>>> analysisTargets = new LinkedHashMap<>();
            for (var entry : queries.entrySet()) {
                String relName = entry.getKey();
                SouffleAnalysis analysis = relNameToProducers.get(relName);
                if (analysis != null) {
                    analysisTargets.computeIfAbsent(analysis, a -> new ArrayList<>()).add(entry);
                }
            }
            for (var entry : analysisTargets.entrySet()) {
                SouffleAnalysis analysis = entry.getKey();
                // TODO: truncate provenance according to targets
                Provenance proof = analysis.getProvenance();
                for (ConstraintItem constraintItem : proof.getClauses()) {
                    Trgt.Constraint.Builder constrBuilder = Trgt.Constraint.newBuilder();
                    // TODO: translate between String and Index of a dom
                    {
                        RawTuple head = constraintItem.getHeadTuple();
                        String relName = head.getRelName();
                        Trgt.Tuple headTuple = Trgt.Tuple.newBuilder()
                                .setRelName(relName)
                                .addAllAttribute(List.of(analysis.translateTuple(relName, head.getIndices())))
                                .build();
                        constrBuilder.setHeadTuple(headTuple);
                    }
                    for (RawTuple body : constraintItem.getSubTuples()) {
                        String relName = body.getRelName();
                        Trgt.Tuple bodyTuple = Trgt.Tuple.newBuilder()
                                .setRelName(relName)
                                .addAllAttribute(List.of(analysis.translateTuple(relName, body.getIndices())))
                                .build();
                        constrBuilder.setHeadTuple(bodyTuple);
                    }
                    constrBuilder.setRuleInfo("souffle-" + analysis.getName() + "#" + constraintItem.getRuleId());
                    Trgt.Constraint constraint = constrBuilder.build();
                    respBuilder.addConstraint(constraint);
                }
            }
            Analysis.ProveResponse proveResp = respBuilder.build();
            responseObserver.onNext(proveResp);
            responseObserver.onCompleted();
        }

        /**
         * @param request
         * @param responseObserver
         */
        @Override
        public void shutdown(Analysis.ShutdownRequest request, StreamObserver<Analysis.ShutdownResponse> responseObserver) {
            Messages.log("*** shutting down jsouffle server due to core request");
            responseObserver.onNext(Analysis.ShutdownResponse.getDefaultInstance());
            responseObserver.onCompleted();
            System.exit(0);
        }
    }
}
