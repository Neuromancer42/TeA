package com.neuromancer42.tea.core.analyses;

import com.neuromancer42.tea.core.bddbddb.RelSign;
import com.neuromancer42.tea.core.project.Trgt;
import org.osgi.framework.BundleContext;

import java.util.Dictionary;

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

    public static <T> Trgt<ProgramDom<T>> createInitializedDomTrgt(ProgramDom<T> dom, String location) {
        Trgt<ProgramDom<T>> domTrgt = createDomTrgt(dom.getName(), dom.getContentType(), location);
        domTrgt.accept(dom);
        return domTrgt;
    }

    public static Trgt<ProgramRel> createRelTrgt(String relName, RelSign relSign, String location) {
        RelInfo relInfo = new RelInfo(location, relSign);
        return new Trgt<>(relName, relInfo);
    }

    public static Trgt<ProgramRel> createInitializedRelTrgt(ProgramRel rel, String location) {
        Trgt<ProgramRel> relTrgt = createRelTrgt(rel.getName(), rel.getSign(), location);
        relTrgt.accept(rel);
        return relTrgt;
    }

}
