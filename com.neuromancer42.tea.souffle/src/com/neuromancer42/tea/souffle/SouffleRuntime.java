package com.neuromancer42.tea.souffle;

import com.neuromancer42.tea.core.project.Config;
import com.neuromancer42.tea.core.project.Messages;
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
            Messages.warn("Souffle runtime has been built before, are you sure to rebuild it?");
        }
        try {
            // 1. New runtime instance, setting paths and toolchains
            runtime = new SouffleRuntime();

            // 2. copy source files from bundles
            InputStream swigInterfaceHeaderStream = SouffleRuntime.class.getResourceAsStream("swig/SwigInterface.h");
            Path swigInterfaceHeader = runtime.workDir.resolve("SwigInterface.h");
            assert swigInterfaceHeaderStream != null;
            Files.copy(swigInterfaceHeaderStream, swigInterfaceHeader, StandardCopyOption.REPLACE_EXISTING);
            InputStream swigInterfaceCXXStream = SouffleRuntime.class.getResourceAsStream("swig/SwigInterface_wrap.cxx");
            Path swigInterfaceCXX = runtime.workDir.resolve("SwigInterface_wrap.cxx");
            assert swigInterfaceCXXStream != null;
            Files.copy(swigInterfaceCXXStream, swigInterfaceCXX, StandardCopyOption.REPLACE_EXISTING);

            // 3. Use C++ to compile libSwigInterface and load it
            Path libSwigIntarfacePath = runtime.compileAndLinkSouffleCPP(swigInterfaceCXX.toFile(), "libSwigInterface", true);
            System.load(libSwigIntarfacePath.toAbsolutePath().toString());
        } catch (IOException | RuntimeException | InterruptedException e) {
            Messages.error("SouffleRuntime: failed to initialize souffle runtime.");
            Messages.fatal(e);
        }
    }

    private final Path cacheDir;

    private final String souffle;
    private final String cppcompiler;
    private final String[] systemIncludes;
    private final String javaHeaders;
    private final String jniHeaders;

    private final String[] linkOptions;
    private final String[] rpaths;

    public Path getWorkDir() {
        return workDir;
    }

    private final Path workDir;

    private Set<String> loadedLibraries;

    private SouffleRuntime() throws IOException {
        // 0. TODO: get paths from environmental variables
        cppcompiler = "/usr/local/bin/g++-11";
        systemIncludes = new String[]{"/usr/local/include","/Library/Developer/CommandLineTools/SDKs/MacOSX12.3.sdk/usr/include"};
        javaHeaders = "/Library/Java/JavaVirtualMachines/jdk-11.0.14.jdk/Contents/Home/include";
        jniHeaders = "/Library/Java/JavaVirtualMachines/jdk-11.0.14.jdk/Contents/Home/include/darwin";

        linkOptions = new String[]{"/Library/Developer/CommandLineTools/SDKs/MacOSX12.3.sdk/usr/lib/libsqlite3.tbd", "/Library/Developer/CommandLineTools/SDKs/MacOSX12.3.sdk/usr/lib/libz.tbd", "/Library/Developer/CommandLineTools/SDKs/MacOSX12.3.sdk/usr/lib/libncurses.tbd"};
        rpaths = new String[]{"/Library/Developer/CommandLineTools/SDKs/MacOSX12.3.sdk/usr/lib","/Library/Developer/CommandLineTools/SDKs/MacOSX12.3.sdk/usr/lib","/Library/Developer/CommandLineTools/SDKs/MacOSX12.3.sdk/usr/lib"};

        cacheDir = Paths.get(Config.v().workDirName);
        workDir = Files.createDirectories(cacheDir.resolve("souffle"));
        souffle = "souffle";

        loadedLibraries = new HashSet<>();
    }

    private Path compileAndLinkSouffleCPP(File cppSource, String targetName, boolean debug) throws IOException, InterruptedException {
        // compile source file to object
        String objectFile = targetName + ".o";
        List<String> compileCmd = new ArrayList<>();
        compileCmd.add(cppcompiler);
        compileCmd.add("-c");
        compileCmd.add(cppSource.getAbsolutePath());
        final String std_flag = "-std=c++17";
        compileCmd.add(std_flag);
        final String release_cxx_flags = "-O3";
        final String debug_cxx_flags = "-g";
        if (debug) {
            compileCmd.add(debug_cxx_flags);
        } else {
            compileCmd.add(release_cxx_flags);
        }
        final String[] cxx_flags = {"-fPIC", "-fopenmp"};
        compileCmd.addAll(Arrays.asList(cxx_flags));
        final String[] definitions = {"-D__EMBEDDED_SOUFFLE__","-DUSE_NCURSES","-DUSE_LIBZ","-DUSE_SQLITE"};
        compileCmd.addAll(Arrays.asList(definitions));
        if (!debug)
            compileCmd.add("-DNDEBUG");
        List<String> includes = new ArrayList<>(List.of(systemIncludes));
        includes.add(javaHeaders);
        includes.add(jniHeaders);
        for (String inc : includes) {
            compileCmd.add("-I");
            compileCmd.add(inc);
        }
        compileCmd.add("-o");
        compileCmd.add(objectFile);

        ProcessBuilder compileBuilder = new ProcessBuilder(compileCmd);
        compileBuilder.directory(workDir.toFile());
        Process compileProcess = compileBuilder.start();
        int compileRetVal = compileProcess.waitFor();
        if (compileRetVal != 0) {
            Messages.log(new String(compileProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            String errString = new String(compileProcess.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new RuntimeException(errString);
        }

        // link the object and generate a dynamic library loadable by jvm
        List<String> linkCmd = new ArrayList<>();
        linkCmd.add(cppcompiler);
        linkCmd.add(objectFile);
        String libraryFile;
        if (SystemUtils.IS_OS_MAC_OSX) {
            linkCmd.add("-dynamiclib");
            libraryFile = targetName + ".dylib";
        } else if (SystemUtils.IS_OS_LINUX) {
            linkCmd.add("-shared");
            libraryFile = targetName + ".so";
        } else {
            throw new RuntimeException("Not supported yet!");
        }
        linkCmd.add("-o");
        linkCmd.add(libraryFile);
        linkCmd.add("-fopenmp");
        linkCmd.addAll(Arrays.asList(linkOptions));
        for (String rpath: rpaths) {
            linkCmd.add(String.format("-Wl,-rpath,%s", rpath));
        }
        ProcessBuilder linkBuilder = new ProcessBuilder(linkCmd);
        linkBuilder.directory(workDir.toFile());
        Process linkProcess = linkBuilder.start();
        int linkRetVal = linkProcess.waitFor();
        if (linkRetVal != 0) {
            Messages.log(new String(linkProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            String errString = new String(linkProcess.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new RuntimeException(errString);
        }
        Path libraryPath = workDir.resolve(libraryFile);
        return libraryPath;
    }

    public void loadDlog(String analysis, InputStream dlogStream, boolean withProvenance) throws IOException, InterruptedException {
        String dlogFileName = analysis + ".dl";
        Path dlogFilePath = workDir.resolve(analysis + ".dl");
        Files.copy(dlogStream, dlogFilePath, StandardCopyOption.REPLACE_EXISTING);

        String cppFileName = analysis + ".cpp";
        List<String> souffleCmd = new ArrayList<>();
        souffleCmd.add(souffle);
        souffleCmd.add(dlogFileName);
        souffleCmd.add("-g");
        souffleCmd.add(cppFileName);
        if (withProvenance) {
            souffleCmd.add("-t");
            souffleCmd.add("none");
        }
        ProcessBuilder souffleBuilder = new ProcessBuilder(souffleCmd);
        souffleBuilder.directory(workDir.toFile());
        Process souffleProcess = souffleBuilder.start();
        int souffleRetVal = souffleProcess.waitFor();
        if (souffleRetVal != 0) {
            Messages.log(new String(souffleProcess.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            String errString = new String(souffleProcess.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            throw new RuntimeException(errString);
        }

        Path cppSourcePath = workDir.resolve(cppFileName);
        Path libraryPath = compileAndLinkSouffleCPP(cppSourcePath.toFile(), analysis, true);
        if (loadedLibraries.contains(analysis)) {
            Messages.warn("SouffleRuntime: analysis " + analysis + " has been loaded before.");
        }
        System.load(libraryPath.toAbsolutePath().toString());
        loadedLibraries.add(analysis);
    }

    public void loadDlog(String analysis, File dlogFile, boolean withProvenance) {
        try {
            loadDlog(analysis, new FileInputStream(dlogFile), withProvenance);
        } catch (Exception e) {
            Messages.error("SouffleRuntime: failed to load souffle analysis '%s'", analysis);
            Messages.fatal(e);
        }
    }
}
