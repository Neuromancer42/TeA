package com.neuromancer42.tea.libdai;

import com.neuromancer42.tea.core.project.Config;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.util.Timer;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import java.util.Date;

public class DAIActivator implements BundleActivator {
    @Override
    public void start(BundleContext bundleContext) throws Exception {
        Timer timer = new Timer("libdai");
        Messages.log("ENTER: LibDAI Runtime Initialization started at " + (new Date()));
        timer.init();
        DAIRuntime.init();
        timer.done();
        Messages.log("LEAVE: LibDAI Runtime Initialization finished");
        Timer.printTimer(timer);
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {

    }
}
