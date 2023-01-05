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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class InstrumentTest {
    private static Path workDirPath = Paths.get("test-out").resolve("test-cdt");

    private static final String execFilename = "exec.c";
    private static Path execSrcPath;

    @BeforeAll
    public static void setup() throws IOException {
        Files.createDirectories(workDirPath);

        InputStream execIn = CDTCManager.class.getClassLoader().getResourceAsStream(execFilename);
        System.err.println("Writing " + execFilename);
        execSrcPath = workDirPath.resolve(execFilename);
        assert execIn != null;
        Files.copy(execIn, execSrcPath, StandardCopyOption.REPLACE_EXISTING);
        execIn.close();
    }

    @Test
    public void execTest() {
        CDTCManager cmanager = new CDTCManager(workDirPath, execSrcPath.toString(), new HashMap<>(), new ArrayList<>());
        cmanager.getInstrument().compile();
        List<String> output = cmanager.getInstrument().runInstrumentedAndPeek("1");
        Assertions.assertEquals(1, output.size());
        Messages.log(output.get(0));
    }

    @Test
    public void execCrashTest() {
        CDTCManager cmanager = new CDTCManager(workDirPath, execSrcPath.toString(), new HashMap<>(), new ArrayList<>());
        cmanager.getInstrument().compile();
        List<String> output = cmanager.getInstrument().runInstrumentedAndPeek();
        Assertions.assertEquals(1, output.size());
        Messages.log(output.get(0));
    }
}
