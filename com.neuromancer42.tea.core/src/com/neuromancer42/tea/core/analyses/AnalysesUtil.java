package com.neuromancer42.tea.core.analyses;

import com.neuromancer42.tea.core.util.tuple.object.Pair;
import org.osgi.framework.BundleContext;

import java.util.Dictionary;
import java.util.Hashtable;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class AnalysesUtil {
    private static Dictionary<String, Object> getAnalysisProps(JavaAnalysis task) {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("name", task.name);
        props.put("input", task.consumerMap);
        props.put("output", task.producerMap);
        return props;
    }

    public static void registerAnalysis(BundleContext context, JavaAnalysis task) {
        task.setConsumerMap();
        task.setProducerMap();
        Dictionary<String, Object> props = getAnalysisProps(task);
        context.registerService(JavaAnalysis.class, task, props);
    }
}
