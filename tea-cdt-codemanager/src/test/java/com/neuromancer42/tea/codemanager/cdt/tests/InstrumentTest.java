package com.neuromancer42.tea.codemanager.cdt.tests;

import com.neuromancer42.tea.codemanager.cdt.CDTCManager;
import com.neuromancer42.tea.commons.configs.Messages;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;

public class InstrumentTest {
    private static final Path rootDir = Paths.get("test-out").resolve("test-instr");

    private static final String execFilename = "exec.c";
    private static Path execSrcPath;

    @BeforeAll
    public static void setup() throws IOException {
        Path srcDir = Files.createDirectories(rootDir.resolve("source"));

        InputStream execIn = CDTCManager.class.getClassLoader().getResourceAsStream(execFilename);
        System.err.println("Writing " + execFilename);
        execSrcPath = srcDir.resolve(execFilename);
        assert execIn != null;
        Files.copy(execIn, execSrcPath, StandardCopyOption.REPLACE_EXISTING);
        execIn.close();

        CDTCManager.setDummySysroot(rootDir);
    }

    @Test
    public void execTest() throws IOException {
        Path workDirPath = Files.createDirectories(rootDir.resolve("test-exec"));
        CDTCManager cmanager = new CDTCManager(workDirPath, execSrcPath.toString(), "clang exec.c -o exec");
        cmanager.setInstrument();
        cmanager.getInstrument().compile();
        List<String> output = cmanager.getInstrument().runInstrumentedAndPeek("1");
        Assertions.assertEquals(1, output.size());
        Messages.log(output.get(0));
    }

    @Test
    public void execCrashTest() throws IOException {
        Path workDirPath = Files.createDirectories(rootDir.resolve("test-crash"));
        CDTCManager cmanager = new CDTCManager(workDirPath, execSrcPath.toString(), "clang exec.c -o exec_crash");
        cmanager.setInstrument();
        cmanager.getInstrument().compile();
        List<String> output = cmanager.getInstrument().runInstrumentedAndPeek();
        Assertions.assertEquals(1, output.size());
        Messages.log(output.get(0));
    }
}
