package com.neuromancer42.tea.program.cdt.tests;

import com.neuromancer42.tea.core.analyses.AnalysesUtil;
import com.neuromancer42.tea.core.project.*;
import com.neuromancer42.tea.program.cdt.CMemoryModel;
import com.neuromancer42.tea.program.cdt.CParserAnalysis;
import org.junit.jupiter.api.*;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkUtil;

import java.util.Set;

public class CMemModelTest {
    static BundleContext context = FrameworkUtil.getBundle(CMemModelTest.class).getBundleContext();

    @BeforeAll
    public static void registerAnalyses() {
        System.setProperty("chord.source.path", System.getProperty("sourcefile"));
        Messages.log("Registering CParser");
        CParserAnalysis cparser = new CParserAnalysis();
        AnalysesUtil.registerAnalysis(context, cparser);

        Messages.log("Registering CMemModel");
        CMemoryModel cMemModel = new CMemoryModel();
        AnalysesUtil.registerAnalysis(context, cMemModel);

        OsgiProject.init();
    }

    @Test
    @DisplayName("Run CMemModel analysis")
    public void runAnalyses() {
        Set<String> tasks = Project.g().getTasks();
        Messages.log("Found %d tasks", tasks.size());
        Assertions.assertTrue(tasks.contains("CParser"));
        Assertions.assertTrue(tasks.contains("CMemModel"));
        String[] taskSet = new String[1];
        taskSet[0] = "CMemModel";
        Project.g().run(taskSet);
        Project.g().printRels(new String[]{"HeapAllocPtr", "HeapAllocArr", "HeapAllocFld"});
    }
}

