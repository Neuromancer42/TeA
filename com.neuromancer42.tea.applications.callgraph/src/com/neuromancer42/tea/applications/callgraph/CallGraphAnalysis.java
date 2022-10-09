package com.neuromancer42.tea.applications.callgraph;

import com.neuromancer42.tea.core.application.AbstractApplication;
import com.neuromancer42.tea.core.project.OsgiProject;

public class CallGraphAnalysis extends AbstractApplication {
    @Override
    protected String getName() {
        return "CallGraph";
    }

    @Override
    protected void runAnalyses(OsgiProject project) {
        project.requireTasks("ciPointerAnalysis");
        project.run("ciPointerAnalysis");
    }
}
