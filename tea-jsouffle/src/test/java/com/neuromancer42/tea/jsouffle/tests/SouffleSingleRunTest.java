package com.neuromancer42.tea.jsouffle.tests;

import com.neuromancer42.tea.jsouffle.SouffleAnalysis;
import com.neuromancer42.tea.jsouffle.SouffleRuntime;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SouffleSingleRunTest {

    private static SouffleAnalysis analysis;
    private static String dlogName = "simple.dl";
    private static Path workDirPath = Paths.get("test-out");
    private static Path buildPath = Paths.get("test-out").resolve("build");


    @BeforeAll
    public static void setup() throws IOException {
        SouffleRuntime.init(buildPath, workDirPath.resolve("test-cache"));

        InputStream dlogIn = SouffleSingleRunTest.class.getClassLoader().getResourceAsStream(dlogName);
        System.err.println("Writing " + dlogName);
        Path dlogPath = workDirPath.resolve(dlogName);
        Files.copy(dlogIn, dlogPath, StandardCopyOption.REPLACE_EXISTING);
        dlogIn.close();


        System.err.println("Opening " + dlogName);
        analysis = SouffleRuntime.g().createSouffleAnalysisFromFile("simple", "simple1", dlogPath.toFile());
    }

    private static void dumpInput(SouffleAnalysis.Instance instance) throws IOException {
        List<String> inputLines = new ArrayList<>();
        inputLines.add("1\t2");
        inputLines.add("2\t3");
        inputLines.add("3\t4");
        Files.write(instance.getFactDir().resolve("PP.facts"), inputLines, StandardCharsets.UTF_8);
    }

    @Test
    @Order(1)
    @DisplayName("SouffleSolver processes dlog file correctly")
    public void singleRunTest() throws IOException {
        SouffleAnalysis.Instance instance = analysis.createInstance("test1-single-run", SouffleRuntime.g().getCachePath().resolve("test1-single-run"));
        dumpInput(instance);
        instance.activate();
        List<String> outputLines = Files.readAllLines(instance.getOutDir().resolve("PPP.csv"));
        Assertions.assertEquals(outputLines.size(), 2);
        Assertions.assertTrue(outputLines.contains("1\t3"));
        Assertions.assertTrue(outputLines.contains("2\t4"));
    }

    @Test
    @Order(2)
    @DisplayName("SouffleSolver generates provenance correctly")
    public void provenanceTest() throws IOException {
        System.out.println("Proving all outputs");
        SouffleAnalysis.Instance instance = analysis.createInstance("test2-prove-all", SouffleRuntime.g().getCachePath().resolve("test2-prove-all"));
        dumpInput(instance);
        Files.deleteIfExists(instance.getProofDir().resolve("targets.list"));
        instance.activate();
        instance.activateProver(instance.getProofDir());
        List<String> proofLines = Files.readAllLines(instance.getProofDir().resolve("cons_all.txt"));
        Assertions.assertEquals(proofLines.size(), 3);
        for (String line : proofLines) {
            System.out.println(line);
        }
    }

    @Test
    @Order(3)
    @DisplayName("SouffleSolver generates provenance on demand")
    public void provenanceOnTargetTest() throws IOException {
        System.out.println("Proving target PPP(1,3) only");
        SouffleAnalysis.Instance instance = analysis.createInstance("test2-prove-all", SouffleRuntime.g().getCachePath().resolve("test2-prove-all"));
        dumpInput(instance);
        instance.activate();
        Files.write(instance.getProofDir().resolve("targets.list"), List.of("PPP\t1\t3"), StandardCharsets.UTF_8);
        instance.activateProver(instance.getProofDir());
        List<String> proofLines = Files.readAllLines(instance.getProofDir().resolve("cons_all.txt"));
        Assertions.assertEquals(proofLines.size(), 1);
        for (String line : proofLines) {
            System.out.println(line);
        }
    }
}
