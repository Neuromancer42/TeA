package com.neuromancer42.tea.commons.analyses.tests;

import com.neuromancer42.tea.commons.analyses.AnalysisUtil;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.core.analysis.Analysis;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class AnnotationTest {
    private static final String workdir = "test-out";
    private static final Map<String, String> relLocMap = new LinkedHashMap<>();
    private static final Map<String, String> domLocMap = new LinkedHashMap<>();


    @BeforeAll
    public static void setup() throws IOException {
        Files.createDirectories(Paths.get(workdir));
        ProgramDom domI = new ProgramDom("I");
        domI.init();
        domI.add("1");
        domI.add("2");
        domI.save(workdir);
        domLocMap.put("I", workdir);

        ProgramRel relP = new ProgramRel("P", domI);
        relP.init();;
        relP.add("1");
        relP.save(workdir);
        relP.close();
        relLocMap.put("P", relP.getLocation());
    }

    @Test
    @Order(1)
    @DisplayName("annotated analysis info parsed correctly")
    public void parseAnnotationTest() {
        Analysis.AnalysisInfo analysisInfo = AnalysisUtil.parseAnalysisInfo(PhonyAnalysis.class);
        Assertions.assertNotNull(analysisInfo);
        Assertions.assertEquals("phony", analysisInfo.getName());
        Assertions.assertEquals(1, analysisInfo.getConsumingDomCount());
        Assertions.assertEquals(1, analysisInfo.getConsumingRelCount());
        Assertions.assertEquals(1, analysisInfo.getProducingDomCount());
        Assertions.assertEquals(1, analysisInfo.getProducingRelCount());
    }

    @Test
    @Order(2)
    @DisplayName("annotated analysis run correctly")
    public void runAnnotatedAnalysisTest() {

        PhonyAnalysis analysis = new PhonyAnalysis(workdir);
        Pair<Map<String, String>, Map<String, String>> output = AnalysisUtil.runAnalysis(analysis, domLocMap, relLocMap);
        Assertions.assertNotNull(output);
    }
}
