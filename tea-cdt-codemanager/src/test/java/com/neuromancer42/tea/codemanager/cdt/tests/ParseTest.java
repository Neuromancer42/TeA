package com.neuromancer42.tea.codemanager.cdt.tests;

import com.neuromancer42.tea.codemanager.cdt.CDTCManager;
import com.neuromancer42.tea.codemanager.cdt.CFGBuilder;
import com.neuromancer42.tea.commons.configs.Messages;
import org.checkerframework.checker.units.qual.C;
import org.junit.jupiter.api.*;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ParseTest {
    private static String cfilename = "simple.c";

    private static Path workDirPath = Paths.get("test-out").resolve("test-cdt");
    private static Path srcPath;

    private static String dotName = "simple.dot";


    @BeforeAll
    public static void setup() throws IOException {
        Files.createDirectories(workDirPath);

        InputStream srcIn = CDTCManager.class.getClassLoader().getResourceAsStream(cfilename);
        System.err.println("Writing " + cfilename);
        srcPath = workDirPath.resolve(cfilename);
        Files.copy(srcIn, srcPath, StandardCopyOption.REPLACE_EXISTING);
        srcIn.close();


    }

    @Test
    @Order(1)
    @DisplayName("CDT C manager created  correctly")
    public void newManagerTest() throws IOException {
        System.err.println("Opening " + srcPath);
        CDTCManager cdtcManager = new CDTCManager(workDirPath, srcPath.toString(), new HashMap<>(), new ArrayList<>());
    }

    @Test
    @Order(2)
    @DisplayName("CDT C manager builds CFG correctly")
    public void cfgBuilderTest() throws IOException {
        CDTCManager cmanager = new CDTCManager(workDirPath, srcPath.toString(), new HashMap<>(), new ArrayList<>());
        CFGBuilder cfgBuilder = new CFGBuilder(cmanager.getTranslationUnit());
        cfgBuilder.build();
        try {
            BufferedWriter bw = Files.newBufferedWriter(workDirPath.resolve(dotName), StandardCharsets.UTF_8);
            PrintWriter pw = new PrintWriter(bw);
            cfgBuilder.dumpDot(pw);
        } catch (IOException e) {
            Messages.error("CParser: failed to dump DOT file %s", dotName);
            Messages.fatal(e);
        }
    }

    @Test
    @Order(3)
    @DisplayName("CDT C manager generate relations correctly")
    public void runAnalysisTest() throws IOException {
        CDTCManager cmanager = new CDTCManager(workDirPath, srcPath.toString(), new HashMap<>(), new ArrayList<>());
        cmanager.run();
    }
}
