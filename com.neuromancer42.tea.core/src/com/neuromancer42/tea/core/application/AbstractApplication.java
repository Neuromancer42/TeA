package com.neuromancer42.tea.core.application;

import com.neuromancer42.tea.core.analyses.IAnalysisBuilder;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.OsgiProject;
import com.neuromancer42.tea.core.util.StringUtil;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractApplication implements BundleActivator {
    @Override
    public void start(BundleContext bundleContext) throws Exception {
        ServiceTracker<IAnalysisBuilder, IAnalysisBuilder> analysisBuilderTracker = new AnalysisBuilderTracker(bundleContext);
        analysisBuilderTracker.open();
        ServiceTracker<OsgiProject, OsgiProject> coreTracker = new ProjectServiceTracker(bundleContext);
        coreTracker.open();
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
    }

    protected abstract String getName();
    protected abstract String[] requiredAnalyses();
    protected abstract void runApplication(BundleContext context, OsgiProject project);

    private void quitApplication(BundleContext bundleContext) {
//        System.exit(0);
        CompletableFuture.runAsync(() -> {
            try {
                bundleContext.getBundle(0).stop();
            } catch (BundleException e) {
                e.printStackTrace();
            }
        });
    }

    private class ProjectServiceTracker extends ServiceTracker<OsgiProject, OsgiProject> {
        public ProjectServiceTracker(BundleContext bundleContext) {
            super(bundleContext, OsgiProject.class, null);
        }

        @Override
        public OsgiProject addingService(ServiceReference<OsgiProject> reference) {
            OsgiProject project = context.getService(reference);
            CompletableFuture.runAsync(() -> {
                Messages.log("APP %s: Application started", getName());
                runApplication(context, project);
                Messages.log("APP %s: Application completed", getName());
                if (System.getProperty("tea.debug", "false").equals("false")) {
                    quitApplication(context);
                }
            });
            return project;
        }
    }

    private class AnalysisBuilderTracker extends ServiceTracker<IAnalysisBuilder, IAnalysisBuilder> {
        public AnalysisBuilderTracker(BundleContext bundleContext) {
            super(bundleContext, IAnalysisBuilder.class, null);
        }

        @Override
        public IAnalysisBuilder addingService(ServiceReference<IAnalysisBuilder> reference) {
            IAnalysisBuilder analysisBuilder = context.getService(reference);
            Messages.log("APP %s: ask builder %s for required analyses", getName(), analysisBuilder.getName());
            CompletableFuture.runAsync(() -> analysisBuilder.buildAnalyses(requiredAnalyses()));
            return analysisBuilder;
        }
    }
}
