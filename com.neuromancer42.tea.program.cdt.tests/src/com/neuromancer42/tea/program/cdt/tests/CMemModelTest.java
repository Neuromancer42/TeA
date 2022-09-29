package com.neuromancer42.tea.program.cdt.tests;

import com.neuromancer42.tea.core.project.*;
import org.junit.jupiter.api.*;

import java.util.Set;


public class CMemModelTest {
    @BeforeAll
    public static void registerAnalyses() {
        System.setProperty("chord.source.path", System.getProperty("sourcefile"));
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

