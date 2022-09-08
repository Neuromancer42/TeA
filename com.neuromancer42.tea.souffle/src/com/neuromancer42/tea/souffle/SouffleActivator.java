package com.neuromancer42.tea.souffle;

import com.neuromancer42.tea.core.project.Config;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.util.Timer;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import java.util.Date;

public class SouffleActivator implements BundleActivator {
    @Override
    public void start(BundleContext bundleContext) throws Exception {
        Timer timer = new Timer("Souffle");
        Messages.log("ENTER: Souffle Runtime Initialization started at " + (new Date()));
        timer.init();
        SouffleRuntime.init();
        timer.done();
        Messages.log("LEAVE: Souffle Runtime Initialization finished");
        Timer.printTimer(timer);
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        StringBuffer msg = new StringBuffer();
        msg.append("loaded libraries:");
        for (String analysis : SouffleRuntime.g().getLoadedLibraries()) {
            msg.append(' ').append(analysis);
        }
        Messages.log("SouffleRuntime: " + msg);
        // Unload all souffle analyses?
    }
}
