package com.neuromancer42.tea.applications.callgraph;

import com.neuromancer42.tea.core.application.AbstractApplication;
import com.neuromancer42.tea.core.project.OsgiProject;
import org.osgi.framework.BundleContext;

public class CallGraphAnalysis extends AbstractApplication {
    @Override
    protected String getName() {
        return "CallGraph";
    }

    @Override
    protected String[] requiredAnalyses() {
        return new String[]{"ciPointerAnalysis"};
    }

    @Override
    protected void runApplication(BundleContext context, OsgiProject project) {
        project.requireTasks("ciPointerAnalysis");
        project.run("ciPointerAnalysis");
    }
}
