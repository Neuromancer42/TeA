package com.neuromancer42.tea.core.application;

import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.OsgiProject;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.ServiceReference;
import org.osgi.util.tracker.ServiceTracker;

import java.util.concurrent.CompletableFuture;

public abstract class AbstractApplication implements BundleActivator {
    protected BundleContext context = null;

    @Override
    public void start(BundleContext bundleContext) throws Exception {
        context = bundleContext;
        ServiceTracker<OsgiProject, OsgiProject> coreTracker = new ServiceTracker<>(context, OsgiProject.class, null) {
            @Override
            public OsgiProject addingService(ServiceReference<OsgiProject> reference) {
                OsgiProject project = context.getService(reference);
                CompletableFuture.runAsync(() -> {
                    Messages.log("APP %s: Application started", getName());
                    runAnalyses(project);
                    Messages.log("APP %s: Application completed", getName());
                    if (System.getProperty("tea.debug", "false").equals("false")) {
                        quitApplication();
                    }
                });
                return project;
            }
        };
        coreTracker.open();
    }

    protected abstract String getName();

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
    }

    protected abstract void runAnalyses(OsgiProject project);

    private void quitApplication() {
//        System.exit(0);
        CompletableFuture.runAsync(() -> {
            try {
                context.getBundle(0).stop();
            } catch (BundleException e) {
                e.printStackTrace();
            }
        });
    }
}
