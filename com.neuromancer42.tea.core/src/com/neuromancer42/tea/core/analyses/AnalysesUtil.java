package com.neuromancer42.tea.core.analyses;

import com.neuromancer42.tea.core.bddbddb.RelSign;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.Trgt;
import org.osgi.framework.BundleContext;

import java.util.Dictionary;
import java.util.HashMap;
import java.util.Map;

public final class AnalysesUtil {

    // register an analysis instance to Osgi Runtime
    public static void registerAnalysis(BundleContext context, JavaAnalysis task) {
        Dictionary<String, Object> props = task.genAnalysisProperties();
        context.registerService(JavaAnalysis.class, task, props);
    }

    public static <T> Trgt<ProgramDom<T>> createDomTrgt(String domName, Class<T> domType, String location) {
        DomInfo domInfo = new DomInfo(location, domType);
        return new Trgt<>(domName, domInfo);
    }

    public static <T> Trgt<ProgramDom<T>> createInitializedDomTrgt(String domName, Class<T> domType, String location) {
        Trgt<ProgramDom<T>> domTrgt = createDomTrgt(domName, domType, location);
        ProgramDom<T> dom = new ProgramDom<>();
        dom.setName(domName);
        domTrgt.accept(dom);
        return domTrgt;
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

    public static Trgt<ProgramRel> createRelTrgt(String relName, RelSign relSign, String location) {
        RelInfo relInfo = new RelInfo(location, relSign);
        return new Trgt<>(relName, relInfo);
    }

    public static Trgt<ProgramRel> createInitializedRelTrgt(String relName, ProgramDom<?>[] doms, String[] domNames, String domOrder, String location) {
        RelSign relSign = new RelSign(domNames, domOrder);
        Trgt<ProgramRel> relTrgt = createRelTrgt(relName, relSign, location);

        ProgramRel rel = new ProgramRel();
        rel.setName(relName);
        rel.setSign(domNames, domOrder);
        rel.setDoms(doms);

        relTrgt.accept(rel);
        return relTrgt;
    }

    public static Trgt<ProgramRel> createInitializedRelTrgt(String relName, ProgramDom<?>[] doms, String location) {
        String[] rawDomNames = new String[doms.length];
        for (int i = 0; i < doms.length; ++i) {
            rawDomNames[i] = doms[i].getName();
        }
        RelSign defaultRelSign = AnalysesUtil.genDefaultRelSign(rawDomNames);
        Trgt<ProgramRel> relTrgt = createRelTrgt(relName, defaultRelSign, location);

        ProgramRel rel = new ProgramRel();
        rel.setName(relName);
        rel.setSign(defaultRelSign);
        rel.setDoms(doms);

        relTrgt.accept(rel);
        return relTrgt;
    }
}
