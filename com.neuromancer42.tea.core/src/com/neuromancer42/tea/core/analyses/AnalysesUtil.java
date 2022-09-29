package com.neuromancer42.tea.core.analyses;

import org.osgi.framework.BundleContext;

import java.util.Dictionary;
import java.util.Hashtable;

public final class AnalysesUtil {

    // register an analysis instance to Osgi Runtime
    public static void registerAnalysis(BundleContext context, JavaAnalysis task) {
        Dictionary<String, Object> props = genAnalysisProperties(task);
        context.registerService(JavaAnalysis.class, task, props);
    }

    public static Dictionary<String, Object> genAnalysisProperties(JavaAnalysis task) {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("name", task.getName());
        props.put("input", task.getConsumerMap());
        props.put("output", task.getProducerMap());
        return props;
    }
}
