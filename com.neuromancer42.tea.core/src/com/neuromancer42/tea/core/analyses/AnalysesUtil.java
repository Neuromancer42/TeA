package com.neuromancer42.tea.core.analyses;

import com.neuromancer42.tea.core.bddbddb.RelSign;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.Trgt;
import org.osgi.framework.BundleContext;

import java.util.Dictionary;

public final class AnalysesUtil {

    // register an analysis instance to Osgi Runtime
    public static void registerAnalysis(BundleContext context, JavaAnalysis task) {
        Dictionary<String, Object> props = task.genAnalysisProperties();
        context.registerService(JavaAnalysis.class, task, props);
    }

    public static <T> Trgt<ProgramDom<T>> createDomTrgt(String location, String domName, Class<T> domType) {
        if (location == null) {
            Messages.error("JavaAnalysis: analysis name must be specified first");
        }
        DomInfo domInfo = new DomInfo(location, domType);
        return new Trgt<>(domName, domInfo);
    }

    public static <T> Trgt<ProgramDom<T>> createInitializedDomTrgt(String location, ProgramDom<T> dom) {
        Trgt<ProgramDom<T>> domTrgt = createDomTrgt(location, dom.getName(), dom.getContentType());
        domTrgt.accept(dom);
        return domTrgt;
    }

    public static Trgt<ProgramRel> createRelTrgt(String location, String relName, RelSign relSign) {
        if (location == null) {
            Messages.error("JavaAnalysis: analysis name must be specified first");
        }
        RelInfo relInfo = new RelInfo(location, relSign);
        return new Trgt<>(relName, relInfo);
    }

    public static Trgt<ProgramRel> createRelTrgt(String location, String relName, String ... rawDomNames) {
        RelSign relSign = ProgramRel.genDefaultRelSign(rawDomNames);
        return createRelTrgt(location, relName, relSign);
    }

    public static Trgt<ProgramRel> createInitializedRelTrgt(String location, ProgramRel rel) {
        Trgt<ProgramRel> relTrgt = createRelTrgt(location, rel.getName(), rel.getSign());
        relTrgt.accept(rel);
        return relTrgt;
    }

}
