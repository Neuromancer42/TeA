package com.neuromancer42.tea.codemanager.cdt.tests;

import com.neuromancer42.tea.codemanager.cdt.CDTCManager;
import com.neuromancer42.tea.codemanager.cdt.CFGBuilder;
import com.neuromancer42.tea.commons.analyses.AnalysisUtil;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Messages;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.cdt.internal.core.dom.rewrite.astwriter.ASTWriter;
import org.junit.jupiter.api.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class ParseTest {
    private static final String simpleName = "simple.c";
    private static Path simplePath;
    private static final String simpleDotName = "simple.dot";
    private static final String arrName = "array.c";
    private static Path arrPath;
    private static final String funcArrName = "funcptr_arraystruct.c";
    private static Path funcArrPath;
    private static final String inclHeaderName = "incl.h";
    private static Path inclHeaderPath;
    private static final String inclSrcName = "incl.c";
    private static Path inclSrcPath;
    private static final String typedefName = "typedef.c";
    private static Path typedefPath;
    private static final String mallocName = "malloc.c";
    private static Path mallocPath;
    private static final String typesName = "types.c";
    private static Path typesPath;
    private static final String vlaName = "vla.c";
    private static Path vlaPath;
    private static final String sizeofName = "sizeof.c";
    private static Path sizeofPath;
    private static final String misc0Name = "misc0.c";
    private static Path misc0Path;

    private static final Path rootDir = Paths.get("test-out").resolve("test-parse");


    @BeforeAll
    public static void setup() throws IOException {
        Path inclDir = Files.createDirectories(rootDir.resolve("include"));
        Path srcDir = Files.createDirectories(rootDir.resolve("source"));

        InputStream simpleIn = CDTCManager.class.getClassLoader().getResourceAsStream(simpleName);
        System.err.println("Writing " + simpleName);
        simplePath = srcDir.resolve(simpleName);
        assert simpleIn != null;
        Files.copy(simpleIn, simplePath, StandardCopyOption.REPLACE_EXISTING);
        simpleIn.close();

        InputStream arrIn = CDTCManager.class.getClassLoader().getResourceAsStream(arrName);
        System.err.println("Writing " + arrName);
        arrPath = srcDir.resolve(arrName);
        assert arrIn != null;
        Files.copy(arrIn, arrPath, StandardCopyOption.REPLACE_EXISTING);
        arrIn.close();

        InputStream funcArrIn = CDTCManager.class.getClassLoader().getResourceAsStream(funcArrName);
        System.err.println("Writing " + funcArrName);
        funcArrPath = srcDir.resolve(funcArrName);
        assert funcArrIn != null;
        Files.copy(funcArrIn, funcArrPath, StandardCopyOption.REPLACE_EXISTING);
        funcArrIn.close();

        InputStream inclHeaderIn = CDTCManager.class.getClassLoader().getResourceAsStream(inclHeaderName);
        System.err.println("Writing " + inclHeaderName);
        inclHeaderPath = inclDir.resolve(inclHeaderName);
        assert inclHeaderIn != null;
        Files.copy(inclHeaderIn, inclHeaderPath, StandardCopyOption.REPLACE_EXISTING);
        inclHeaderIn.close();
        InputStream inclSrcIn = CDTCManager.class.getClassLoader().getResourceAsStream(inclSrcName);
        System.err.println("Writing " + inclSrcName);
        inclSrcPath = srcDir.resolve(inclSrcName);
        assert inclSrcIn != null;
        Files.copy(inclSrcIn, inclSrcPath, StandardCopyOption.REPLACE_EXISTING);
        inclSrcIn.close();

        InputStream typedefIn = CDTCManager.class.getClassLoader().getResourceAsStream(typedefName);
        System.err.println("Writing " + typedefName);
        typedefPath = srcDir.resolve(typedefName);
        assert typedefIn != null;
        Files.copy(typedefIn, typedefPath, StandardCopyOption.REPLACE_EXISTING);
        typedefIn.close();

        InputStream mallocIn = CDTCManager.class.getClassLoader().getResourceAsStream(mallocName);
        System.err.println("Writing " + mallocName);
        mallocPath = srcDir.resolve(mallocName);
        assert mallocIn != null;
        Files.copy(mallocIn, mallocPath, StandardCopyOption.REPLACE_EXISTING);
        mallocIn.close();

        InputStream typesIn = CDTCManager.class.getClassLoader().getResourceAsStream(typesName);
        System.err.println("Writing " + typesName);
        typesPath = srcDir.resolve(typesName);
        assert typesIn != null;
        Files.copy(typesIn, typesPath, StandardCopyOption.REPLACE_EXISTING);
        typesIn.close();
        CDTCManager.setDummySysroot(rootDir);

        InputStream vlaIn = CDTCManager.class.getClassLoader().getResourceAsStream(vlaName);
        System.err.println("Writing " + vlaName);
        vlaPath = srcDir.resolve(vlaName);
        assert vlaIn != null;
        Files.copy(vlaIn, vlaPath, StandardCopyOption.REPLACE_EXISTING);
        vlaIn.close();

        InputStream sizeofIn = CDTCManager.class.getClassLoader().getResourceAsStream(sizeofName);
        System.err.println("Writing " + sizeofName);
        sizeofPath = srcDir.resolve(sizeofName);
        assert sizeofIn != null;
        Files.copy(sizeofIn, sizeofPath, StandardCopyOption.REPLACE_EXISTING);
        sizeofIn.close();

        InputStream misc0In = CDTCManager.class.getClassLoader().getResourceAsStream(misc0Name);
        System.err.println("Writing " + misc0Name);
        misc0Path = srcDir.resolve(misc0Name);
        assert misc0In != null;
        Files.copy(misc0In, misc0Path, StandardCopyOption.REPLACE_EXISTING);
        misc0In.close();
    }

    @Test
    @Order(1)
    @DisplayName("CDT C manager created  correctly")
    public void newManagerTest() throws IOException {
        Path workDir = Files.createDirectories(rootDir.resolve("test-newmanager"));
        System.err.println("Opening " + simplePath);
        CDTCManager cdtcManager = new CDTCManager(workDir, simplePath.toString(), "clang");
    }

    @Test
    @Order(2)
    @DisplayName("CDT C manager builds CFG correctly")
    public void cfgBuilderTest() throws IOException {
        Path workDir = Files.createDirectories(rootDir.resolve("test-cfgbuilder"));
        CDTCManager cmanager = new CDTCManager(workDir, simplePath.toString(), "clang");
        CFGBuilder cfgBuilder = new CFGBuilder(cmanager.getTranslationUnit());
        cfgBuilder.build();
        try {
            BufferedWriter bw = Files.newBufferedWriter(workDir.resolve(simpleDotName), StandardCharsets.UTF_8);
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
        Path workDir = Files.createDirectories(rootDir.resolve("test-simple"));
        CDTCManager cmanager = new CDTCManager(workDir, simplePath.toString(), "clang");
        cmanager.run();
    }

    @Test
    @Order(4)
    @DisplayName("CDT C manager run correctly in reflection mode")
    public void reflectAnalysisTest() throws IOException {
        Path workDir = Files.createDirectories(rootDir.resolve("test-reflect"));
        CDTCManager cmanager = new CDTCManager(workDir, simplePath.toString(), "clang");
        Pair<Map<String, String>, Map<String, String>> output = AnalysisUtil.runAnalysis(cmanager, new HashMap<>(), new HashMap<>());
        Assertions.assertNotNull(output);
        Object[] domNames = cmanager.getProducedDoms().stream().map(ProgramDom::getName).sorted().toArray();
        Assertions.assertArrayEquals(domNames, output.getLeft().keySet().stream().sorted().toArray());
        Object[] relNames = cmanager.getProducedRels().stream().map(ProgramRel::getName).sorted().toArray();
        Assertions.assertArrayEquals(relNames, output.getRight().keySet().stream().sorted().toArray());
    }

    @Test
    @Order(5)
    @DisplayName("CDT C manager parses array correctly")
    public void parseArrayTest() throws IOException {
        Path workDir = Files.createDirectories(rootDir.resolve("test-array"));
        CDTCManager cmanager = new CDTCManager(workDir, arrPath.toString(), "clang");
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
    @Order(5)
    @DisplayName("CDT C manager parses array of funcptrs  correctly")
    public void parseFuncArrayStructTest() throws IOException {
        Path workDir = Files.createDirectories(rootDir.resolve("test-funcarr"));
        CDTCManager cmanager = new CDTCManager(workDir, funcArrPath.toString(), "clang");
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

    @Test
    @Order(6)
    @DisplayName("CDT handles includes correctly")
    public void parseInclude() throws IOException {
        Path workDir = Files.createDirectories(rootDir.resolve("test-include"));
        CDTCManager cmanager = new CDTCManager(workDir, inclSrcPath.toString(), String.format("clang %s -I%s -o incl", inclSrcPath.toAbsolutePath(), inclHeaderPath.getParent().toAbsolutePath()));
        cmanager.run();
        System.err.println(new ASTWriter().write(cmanager.getTranslationUnit()));
    }

    @Test
    @Order(7)
    @DisplayName("CDT handles typedefs correctly")
    public void parseTypedef() throws IOException {
        Path workDir = Files.createDirectories(rootDir.resolve("test-typedef"));
        CDTCManager cmanager = new CDTCManager(workDir, typedefPath.toString(), "clang");
        cmanager.run();
        for (ProgramDom dom : cmanager.getProducedDoms()) {
            if (dom.getName().equals("T")) {
                Assertions.assertNotEquals(0, dom.size());
                for (String tStr : dom) {
                    Messages.log(tStr);
                }
            }
        }
    }

    @Test
    @Order(8)
    @DisplayName("CDT handles mallocs correctly")
    public void parseMalloc() throws IOException {
        Path workDir = Files.createDirectories(rootDir.resolve("test-malloc"));
        CDTCManager cmanager = new CDTCManager(workDir, mallocPath.toString(), "clang");
        cmanager.run();
        for (ProgramDom dom : cmanager.getProducedDoms()) {
            if (dom.getName().equals("A")) {
                Assertions.assertNotEquals(0, dom.size());
                for (String tStr : dom) {
                    Messages.log(tStr);
                }
            }
        }
    }

    @Test
    @Order(9)
    @DisplayName("CDT handles types correctly")
    public void parseTypes() throws IOException {
        Path workDir = Files.createDirectories(rootDir.resolve("test-types"));
        CDTCManager cmanager = new CDTCManager(workDir, typesPath.toString(), "clang");
        cmanager.run();
        for (ProgramDom dom : cmanager.getProducedDoms()) {
            if (dom.getName().equals("T")) {
                Assertions.assertNotEquals(0, dom.size());
                for (String t : dom) {
                    Messages.log(t);
                }
            }
        }
        for (ProgramRel rel : cmanager.getProducedRels()) {
            if (rel.getName().equals("Alloca") || rel.getName().equals("TypeWidth")) {
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

    @Test
    @Order(10)
    @DisplayName("CDT handles VLA correctly")
    public void parseVla() throws IOException {
        Path workDir = Files.createDirectories(rootDir.resolve("test-vla"));
        CDTCManager cmanager = new CDTCManager(workDir, vlaPath.toString(), "clang");
        cmanager.run();
        for (ProgramDom dom : cmanager.getProducedDoms()) {
            if (dom.getName().equals("T")) {
                for (String a : dom) {
                    Messages.log(a);
                }
            }
        }
        for (ProgramRel rel : cmanager.getProducedRels()) {
            if (rel.getName().equals("ArrContentType")) {
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

    @Test
    @Order(11)
    @DisplayName("CDT handles sizeof correctly")
    public void parseSizeof() throws IOException {
        Path workDir = Files.createDirectories(rootDir.resolve("test-sizeof"));
        CDTCManager cmanager = new CDTCManager(workDir, sizeofPath.toString(), "clang");
        cmanager.run();
        for (ProgramDom dom : cmanager.getProducedDoms()) {
            if (dom.getName().equals("T")) {
                for (String a : dom) {
                    Messages.log(a);
                }
            }
        }
        for (ProgramRel rel : cmanager.getProducedRels()) {
            if (rel.getName().equals("Econst") || rel.getName().equals("TypeWidth")) {
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

    @Test
    @Order(12)
    @DisplayName("CDT handles misc0 correctly")
    public void parseMisc0() throws IOException {
        Path workDir = Files.createDirectories(rootDir.resolve("test-misc0"));
        CDTCManager cmanager = new CDTCManager(workDir, misc0Path.toString(), "clang");
        cmanager.run();
    }
}
