package com.neuromancer42.tea.core.project;

import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

public class CoreActivator implements BundleActivator {
    @Override
    public void start(BundleContext bundleContext) throws Exception {
        Config.init();
        Messages.log("Core: initialized");
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
    }
}
