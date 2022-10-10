package com.neuromancer42.tea.libdai;

import com.neuromancer42.tea.core.inference.ICausalDriverFactory;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.util.Timer;
import org.osgi.framework.BundleActivator;
import org.osgi.framework.BundleContext;

import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.concurrent.CompletableFuture;

public class DAIActivator implements BundleActivator {
    @Override
    public void start(BundleContext bundleContext) throws Exception {
        CompletableFuture.runAsync( () -> {
            Timer timer = new Timer("libdai");
            Messages.log("ENTER: LibDAI Runtime Initialization started at " + (new Date()));
            timer.init();
            DAIRuntime.init();
            DAIDriverFactory driverFactory = DAIDriverFactory.g();
            Dictionary<String, Object> props = new Hashtable<>();
            props.put("name", driverFactory.getName());
            props.put("algorithms", driverFactory.getAlgorithms());
            bundleContext.registerService(ICausalDriverFactory.class, driverFactory, props);
            timer.done();
            Messages.log("LEAVE: LibDAI Runtime Initialization finished");
            Timer.printTimer(timer);
        });
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {

    }
}
