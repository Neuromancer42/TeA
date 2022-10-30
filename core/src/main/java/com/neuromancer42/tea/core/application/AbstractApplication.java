package com.neuromancer42.tea.core.application;

import com.neuromancer42.tea.core.analyses.IAnalysisBuilder;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.OsgiProject;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractApplication {

    protected abstract String getName();
    protected abstract String[] requiredAnalyses();
    protected abstract void runApplication(OsgiProject project);

    private void quitApplication() {
        System.exit(0);
    }

    private class ProjectServiceTracker  {

        public OsgiProject addingService(OsgiProject project) {
            CompletableFuture.runAsync(() -> {
                Messages.log("APP %s: Application started", getName());
                runApplication(project);
                Messages.log("APP %s: Application completed", getName());
                if (System.getProperty("tea.debug", "false").equals("false")) {
                    quitApplication();
                }
            });
            return project;
        }
    }

    private class AnalysisBuilderTracker  {
        public IAnalysisBuilder addingService(IAnalysisBuilder analysisBuilder) {
            Messages.log("APP %s: ask builder %s for required analyses", getName(), analysisBuilder.getName());
            CompletableFuture.runAsync(() -> analysisBuilder.buildAnalyses(requiredAnalyses()));
            return analysisBuilder;
        }
    }
}
