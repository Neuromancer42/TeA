package com.neuromancer42.tea.souffle;

import com.neuromancer42.tea.core.project.Config;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.souffle.swig.SWIGSouffleProgram;
import com.neuromancer42.tea.souffle.swig.SwigInterface;
import org.apache.commons.lang3.SystemUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class SouffleRuntime {
    private static SouffleRuntime runtime = null;

    public static SouffleRuntime g() {
        if (runtime == null) {
            Messages.fatal("SouffleRuntime: souffle runtime should be inited first");
        }
        return runtime;
    }

    public static void init() {
        if (runtime != null) {
            Messages.warn("SouffleRuntime: runtime has been built before, are you sure to rebuild it?");
        }

        // 1. New runtime instance, setting paths and toolchains
        Path tmpWorkDir = null;
        try {
            tmpWorkDir = Files.createDirectories(Paths.get(Config.v().workDirName).resolve("souffle"));
        } catch (IOException e) {
            Messages.error("SouffleRuntime: failed to create working directory");
            Messages.fatal(e);
        }
        runtime = new SouffleRuntime(tmpWorkDir);
        try {
            // 0. copy files from bundles
            {
                InputStream souffleCMakeStream = SouffleRuntime.class.getResourceAsStream("swig/CMakeLists.txt");
                Path souffleCMakePath = runtime.workDir.resolve("CMakeLists.txt");
                assert souffleCMakeStream != null;
                Files.copy(souffleCMakeStream, souffleCMakePath, StandardCopyOption.REPLACE_EXISTING);
                InputStream souffleSrcStream = SouffleRuntime.class.getResourceAsStream("swig/souffle-swig-interface_wrap.cxx");
                Path souffleSrcPath = runtime.workDir.resolve("souffle-swig-interface_wrap.cxx");
                assert souffleSrcStream != null;
                Files.copy(souffleSrcStream, souffleSrcPath, StandardCopyOption.REPLACE_EXISTING);
                InputStream souffleHeaderStream = SouffleRuntime.class.getResourceAsStream("swig/souffle-swig-interface.h");
                Path souffleHeaderPath = runtime.workDir.resolve("souffle-swig-interface.h");
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
            Path cmakeDir = Files.createDirectories(runtime.workDir.resolve("cmake-build"));
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
                installCmd.add(runtime.workDir.toAbsolutePath().toString());
                executeExternal(installCmd, cmakeDir);
            }


            // 3. load library
            Path souffleJNIPath = runtime.workDir.resolve("native").resolve(libraryFileName);
            System.load(souffleJNIPath.toAbsolutePath().toString());
            Messages.log("SouffleRuntime: souffle runtime has been loaded");
        } catch (IOException | RuntimeException | InterruptedException e) {
            Messages.error("SouffleRuntime: failed to initialize souffle runtime.");
            Messages.fatal(e);
        }
    }

    private static void executeExternal(List<String> cmd, Path path) throws IOException, InterruptedException {
        ProcessBuilder cmakeBuilder = new ProcessBuilder(cmd);
        cmakeBuilder.directory(path.toFile());
        Process cmakeProcess = cmakeBuilder.start();
        int cmakeRetVal = cmakeProcess.waitFor();
        if (cmakeRetVal != 0) {
            Messages.log(new String(cmakeProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            String errString = new String(cmakeProcess.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new RuntimeException(errString);
        }
    }

    public Path getWorkDir() {
        return workDir;
    }

    private final Path workDir;

    public Set<String> getLoadedLibraries() {
        return loadedLibraries;
    }

    private final Set<String> loadedLibraries;
    private final Map<String, String> loadedProvenances;

    private SouffleRuntime(Path workDir) {
        this.workDir = workDir;
        loadedLibraries = new HashSet<>();
        loadedProvenances = new HashMap<>();
    }

    public void loadDlog(String analysis, InputStream dlogStream, boolean withProvenance, boolean withDebug) {
        if (loadedLibraries.contains(analysis)) {
            Messages.warn("SouffleRuntime: analysis %s has been loaded before!", analysis);
            return;
        }
        try {
            // 0. copy files
            String dlogFileName = analysis + ".dl";
            Path dlogFilePath = workDir.resolve(dlogFileName);
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
            Path cmakeDir = workDir.resolve("cmake-build");
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
                installCmd.add(runtime.workDir.toAbsolutePath().toString());
                executeExternal(installCmd, cmakeDir);
            }

            // 3. load library
            Path analysisLibPath = runtime.workDir.resolve("native").resolve(analysisLibName);
            System.load(analysisLibPath.toAbsolutePath().toString());
            Messages.log("SouffleRuntime: analysis runtime %s has been loaded", analysisLibName);
            loadedLibraries.add(analysis);
            if (withProvenance) {
                Path provLibPath = runtime.workDir.resolve("native").resolve(provLibName);
                System.load(provLibPath.toAbsolutePath().toString());
                Messages.log("SouffleRuntime: provenance runtime %s has been loaded", provLibName);
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

    public SouffleAnalysis createSouffleAnalysis(String name, String analysis) {
        if (!hasLoaded(analysis)) {
            Messages.fatal("SouffleRuntime: the analysis %s has not been loaded yet!");
        }
        SWIGSouffleProgram program = SwigInterface.newInstance(analysis);
        SWIGSouffleProgram provProgram = null;
        String provName = hasLoadedProvenance(analysis);
        if (provName != null) {
            provProgram = SwigInterface.newInstance(provName);
        }
        return new SouffleAnalysis(name, analysis, program, provProgram);
    }
}
