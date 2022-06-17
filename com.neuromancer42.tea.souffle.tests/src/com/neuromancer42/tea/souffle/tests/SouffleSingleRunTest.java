package com.neuromancer42.tea.souffle.tests;

import com.neuromancer42.tea.core.project.Config;
import com.neuromancer42.tea.souffle.SouffleAnalysis;
import com.neuromancer42.tea.souffle.SouffleRuntime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class SouffleSingleRunTest {
    @Test
    @DisplayName("SouffleSolver processes dlog file correctly")
    public void singleRunTest() throws IOException {
        Config.init();
        SouffleRuntime.init();
        URL dlogURL = this.getClass().getResource("/resources/simple.dl");
        String dlogName = System.getProperty("dlog", dlogURL.toString());
        System.err.println("Opening " + dlogName);
        SouffleAnalysis analysis = new SouffleAnalysis("simple1", dlogName);
        List<String> inputLines = new ArrayList<>();
        inputLines.add("1\t2");
        inputLines.add("2\t3");
        Files.write(analysis.factDir.resolve("PP.facts"), inputLines, StandardCharsets.UTF_8);
        analysis.run();
        List<String> outputLines = Files.readAllLines(analysis.outDir.resolve("PPP.csv"));
        Assertions.assertEquals(outputLines.size(), 1);
        Assertions.assertEquals(outputLines.get(0), "1\t3");
    }
}
