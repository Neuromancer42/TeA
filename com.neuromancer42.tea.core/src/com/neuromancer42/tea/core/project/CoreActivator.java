package com.neuromancer42.tea.core.project;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.util.StringUtil;
import org.osgi.framework.*;

import java.util.Collection;

public class CoreActivator implements BundleActivator {
    @Override
    public void start(BundleContext bundleContext) throws Exception {
        Config.init();
        OsgiProject.init(bundleContext);
        synchronized (OsgiProject.g()) {
            try {
                Collection<ServiceReference<JavaAnalysis>> taskRefs = bundleContext.getServiceReferences(JavaAnalysis.class, null);
                for (ServiceReference<JavaAnalysis> taskRef : taskRefs) {
                    OsgiProject.g().receiveTask(taskRef);
                }
            } catch (InvalidSyntaxException e) {
                // Impossible to happen
                Messages.fatal(e);
                assert false;
            }
            bundleContext.addServiceListener(OsgiProject.g(), "(" + Constants.OBJECTCLASS + "=" + JavaAnalysis.class.getName() + ")");
            Messages.debug("Core: ServiceListener of JavaAnalysis created");
        }
        bundleContext.registerService(OsgiProject.class, OsgiProject.g(), null);
        Messages.log("Core: initialized");
    }

    @Override
    public void stop(BundleContext bundleContext) throws Exception {
        Messages.log("Core: registered tasks [%s]", StringUtil.join(OsgiProject.g().getTasks(), ","));
        Messages.log("Core: finished");
    }
}
