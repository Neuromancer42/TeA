package com.neuromancer42.tea.codemanager.cdt.tests;

import com.neuromancer42.tea.codemanager.cdt.CDTCManager;
import com.neuromancer42.tea.codemanager.cdt.CFGBuilder;
import com.neuromancer42.tea.commons.analyses.AnalysisUtil;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Messages;
import org.apache.commons.lang3.tuple.Pair;
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
import java.util.Map;

public class ParseTest {
    private static final String simpleFilename = "simple.c";
    private static Path simpleSrcPath;
    private static final String simpleDotName = "simple.dot";
    private static final String arrFilename = "array.c";
    private static Path arrSrcPath;
    private static final String funcArrFilename = "funcptr_arraystruct.c";
    private static Path funcArrSrcPath;

    private static Path workDirPath = Paths.get("test-out").resolve("test-cdt");


    @BeforeAll
    public static void setup() throws IOException {
        Files.createDirectories(workDirPath);

        InputStream simpleIn = CDTCManager.class.getClassLoader().getResourceAsStream(simpleFilename);
        System.err.println("Writing " + simpleFilename);
        simpleSrcPath = workDirPath.resolve(simpleFilename);
        Files.copy(simpleIn, simpleSrcPath, StandardCopyOption.REPLACE_EXISTING);
        simpleIn.close();

        InputStream arrIn = CDTCManager.class.getClassLoader().getResourceAsStream(arrFilename);
        System.err.println("Writing " + arrFilename);
        arrSrcPath = workDirPath.resolve(arrFilename);
        Files.copy(arrIn, arrSrcPath, StandardCopyOption.REPLACE_EXISTING);
        arrIn.close();

        InputStream funcArrIn = CDTCManager.class.getClassLoader().getResourceAsStream(funcArrFilename);
        System.err.println("Writing " + funcArrFilename);
        funcArrSrcPath = workDirPath.resolve(funcArrFilename);
        Files.copy(funcArrIn, funcArrSrcPath, StandardCopyOption.REPLACE_EXISTING);
        funcArrIn.close();
    }

    @Test
    @Order(1)
    @DisplayName("CDT C manager created  correctly")
    public void newManagerTest() throws IOException {
        System.err.println("Opening " + simpleSrcPath);
        CDTCManager cdtcManager = new CDTCManager(workDirPath, simpleSrcPath.toString(), new HashMap<>(), new ArrayList<>());
    }

    @Test
    @Order(2)
    @DisplayName("CDT C manager builds CFG correctly")
    public void cfgBuilderTest() throws IOException {
        CDTCManager cmanager = new CDTCManager(workDirPath, simpleSrcPath.toString(), new HashMap<>(), new ArrayList<>());
        CFGBuilder cfgBuilder = new CFGBuilder(cmanager.getTranslationUnit());
        cfgBuilder.build();
        try {
            BufferedWriter bw = Files.newBufferedWriter(workDirPath.resolve(simpleDotName), StandardCharsets.UTF_8);
            PrintWriter pw = new PrintWriter(bw);
            cfgBuilder.dumpDot(pw);
        } catch (IOException e) {
            Messages.error("CParser: failed to dump DOT file %s", simpleDotName);
            Messages.fatal(e);
        }
    }

    @Test
    @Order(3)
    @DisplayName("CDT C manager generate relations correctly")
    public void runAnalysisTest() throws IOException {
        CDTCManager cmanager = new CDTCManager(workDirPath, simpleSrcPath.toString(), new HashMap<>(), new ArrayList<>());
        cmanager.run();
    }

    @Test
    @Order(4)
    @DisplayName("CDT C manager run correclty in reflection mode")
    public void reflectAnalysisTest() throws IOException {
        CDTCManager cmanager = new CDTCManager(workDirPath, simpleSrcPath.toString(), new HashMap<>(), new ArrayList<>());
        Pair<Map<String, String>, Map<String, String>> output = AnalysisUtil.runAnalysis(cmanager, new HashMap<>(), new HashMap<>());
        Assertions.assertNotNull(output);
        Object[] domNames = cmanager.getProducedDoms().stream().map(ProgramDom::getName).sorted().toArray();
        Assertions.assertArrayEquals(domNames, output.getLeft().keySet().stream().sorted().toArray());
        Object[] relNames = cmanager.getProducedRels().stream().map(ProgramRel::getName).sorted().toArray();
        Assertions.assertArrayEquals(relNames, output.getRight().keySet().stream().sorted().toArray());
    }

    @Test
    public void parseArrayTest() throws IOException {
        CDTCManager cmanager = new CDTCManager(workDirPath, arrSrcPath.toString(), new HashMap<>(), new ArrayList<>());
        cmanager.run();
        for (ProgramRel rel: cmanager.getProducedRels()) {
            if (rel.getName().equals("ArrContentType")) {
                rel.load();
                Assertions.assertNotEquals(0, rel.size());
                for (Object[] tuple : rel.getValTuples()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("ArrContentType(");
                    for (int i = 0; i < tuple.length; ++i) {
                        if (i > 0) {
                            sb.append(",");
                        }
                        sb.append((String) tuple[i]);
                    }
                    sb.append(")");
                    Messages.log(sb.toString());
                }
                rel.close();
            }
        }
    }

    @Test
    public void parseFuncArrayStructTest() throws IOException {
        CDTCManager cmanager = new CDTCManager(workDirPath, funcArrSrcPath.toString(), new HashMap<>(), new ArrayList<>());
        cmanager.run();
        for (ProgramRel rel: cmanager.getProducedRels()) {
            if (rel.getName().equals("LoadArr") || rel.getName().equals("LoadFld")) {
                rel.load();
                Assertions.assertNotEquals(0, rel.size());
                for (Object[] tuple : rel.getValTuples()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(rel.getName()).append("(");
                    for (int i = 0; i < tuple.length; ++i) {
                        if (i > 0) {
                            sb.append(",");
                        }
                        sb.append((String) tuple[i]);
                    }
                    sb.append(")");
                    Messages.log(sb.toString().replaceAll("%", "%%"));
                }
                rel.close();
            }
        }
    }
}
