package com.neuromancer42.tea.core.analyses;

import com.neuromancer42.tea.core.bddbddb.RelSign;
import com.neuromancer42.tea.core.project.Messages;
import org.osgi.framework.BundleContext;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public final class AnalysesUtil {
    private static Dictionary<String, Object> genAnalysisProperties(JavaAnalysis task) {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("name", task.name);
        props.put("input", task.consumerMap);
        props.put("output", task.producerMap);
        return props;
    }

    // register an analysis instance to Osgi Runtime
    public static void registerAnalysis(BundleContext context, JavaAnalysis task) {
        task.setConsumerMap();
        task.setProducerMap();
        Dictionary<String, Object> props = genAnalysisProperties(task);
        context.registerService(JavaAnalysis.class, task, props);
    }

    // generate default RelSign
    // example: Souffle relation: Reach(a1:A,b:B,a2:A) ==> RelSign: [A0,B0,A1]:"A0xB0xA1"
    public static RelSign genDefaultRelSign(String[] rawDomNames) {
        assert(rawDomNames.length > 0);
        String[] domNames = new String[rawDomNames.length];
        Map<String, Integer> domCount = new HashMap<>();
        for (int i = 0; i < rawDomNames.length; ++i) {
            String rawDomName = rawDomNames[i];
            boolean allLetters = true;
            for (int j = 0; j < rawDomName.length(); ++j) {
                char c = rawDomName.charAt(j);
                if ((c < 'a' || c > 'z') && (c < 'A' || c > 'Z')) {
                    allLetters = false;
                    break;
                }
            }
            if (!allLetters) {
                Messages.fatal("AnalysesUtil: raw domain name (%s) should be restricted to english letters only", rawDomName);
            } else {
                int idx = domCount.getOrDefault(rawDomName, 0);
                domNames[i] = rawDomName + idx;
                domCount.put(rawDomName, idx + 1);
            }
        }
        StringBuilder domOrder = new StringBuilder(domNames[0]);
        for (int i = 1; i < domNames.length; ++i) {
            domOrder.append('x').append(domNames[i]);
        }
        return new RelSign(domNames, domOrder.toString());
    }

    public static <T> ProgramDom<T> createProgramDom(String name) {
        ProgramDom<T> dom = new ProgramDom<>();
        dom.setName(name);
        return dom;
    }

    public static ProgramRel createProgramRel(String name, ProgramDom<?>[] doms, String[] domNames, String domOrder) {
        ProgramRel rel = new ProgramRel();
        rel.setName(name);
        rel.setSign(domNames, domOrder);
        rel.setDoms(doms);
        return rel;
    }

    public static ProgramRel createProgramRel(String name, ProgramDom<?>[] doms) {
        ProgramRel rel = new ProgramRel();
        rel.setName(name);
        String[] rawDomNames = new String[doms.length];
        for (int i = 0; i < doms.length; ++i) {
            rawDomNames[i] = doms[i].getName();
        }
        RelSign defaultRelSign = genDefaultRelSign(rawDomNames);
        rel.setSign(defaultRelSign);
        rel.setDoms(doms);
        return rel;
    }
}
