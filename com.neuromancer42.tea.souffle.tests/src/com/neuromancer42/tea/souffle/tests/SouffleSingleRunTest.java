package com.neuromancer42.tea.souffle.tests;

import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.provenance.Provenance;
import com.neuromancer42.tea.souffle.SouffleAnalysis;
import com.neuromancer42.tea.souffle.SouffleRuntime;
import org.junit.jupiter.api.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class SouffleSingleRunTest {

    private static SouffleAnalysis analysis;

    @BeforeAll
    public static void setup() throws IOException {
        String dlogName = System.getProperty("dlog");
        Messages.log("Opening " + dlogName);
        analysis = SouffleRuntime.g().createSouffleAnalysisFromFile("simple", "simple1", new File(dlogName));
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
        prov.dump(analysis.getAnalysisDir().toString());
        Assertions.assertEquals(prov.getClauses().size(), 1);
    }
}
