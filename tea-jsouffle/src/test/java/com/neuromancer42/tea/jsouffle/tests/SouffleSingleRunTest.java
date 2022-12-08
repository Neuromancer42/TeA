package com.neuromancer42.tea.jsouffle.tests;

import com.neuromancer42.tea.commons.provenance.Provenance;
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
    private static Path workDirPath = Paths.get("test-out").resolve("test-jsouffle");


    @BeforeAll
    public static void setup() throws IOException {
        SouffleRuntime.init(workDirPath);

        InputStream dlogIn = SouffleSingleRunTest.class.getClassLoader().getResourceAsStream(dlogName);
        System.err.println("Writing " + dlogName);
        Path dlogPath = workDirPath.resolve(dlogName);
        Files.copy(dlogIn, dlogPath, StandardCopyOption.REPLACE_EXISTING);
        dlogIn.close();


        System.err.println("Opening " + dlogName);
        analysis = SouffleRuntime.g().createSouffleAnalysisFromFile("simple", "simple1", dlogPath.toFile());
        List<String> inputLines = new ArrayList<>();
        inputLines.add("1\t2");
        inputLines.add("2\t3");
        Files.write(analysis.getFactDir().resolve("PP.facts"), inputLines, StandardCharsets.UTF_8);
    }

    @Test
    @Order(1)
    @DisplayName("SouffleSolver processes dlog file correctly")
    public void singleRunTest() throws IOException {
        analysis.activate();
        analysis.close();
        List<String> outputLines = Files.readAllLines(analysis.getOutDir().resolve("PPP.csv"));
        Assertions.assertEquals(outputLines.size(), 1);
        Assertions.assertEquals(outputLines.get(0), "1\t3");
    }

    @Test
    @Order(2)
    @DisplayName("SouffleSolver generates provenance correctly")
    public void provenanceTest() {
        Provenance prov = analysis.getProvenance();
        prov.dump(workDirPath.toString());
        Assertions.assertEquals(prov.getClauses().size(), 1);
    }
}
