package com.neuromancer42.tea.commons.analyses;

import com.neuromancer42.tea.commons.analyses.annotations.*;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.core.analysis.Analysis;
import com.neuromancer42.tea.core.analysis.Trgt;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.lang.reflect.Field;
import java.util.*;


public class AnalysisUtil {
    private AnalysisUtil() {}

    public static Trgt.RelInfo parseRelInfo(String relInfoStr) {
        String relName = relInfoStr.substring(0, relInfoStr.indexOf("("));
        String[] relDoms = relInfoStr.substring(relInfoStr.indexOf("(")+1, relInfoStr.indexOf(")")).split(",");
        String relDesc = relInfoStr.substring(relInfoStr.indexOf(")")+2);
        return Trgt.RelInfo.newBuilder()
                .setName(relName)
                .addAllDom(List.of(relDoms))
                .setDescription(relDesc)
                .build();
    }

    public static <T> Map<String, Trgt.DomInfo> parseConsumeDomInfo(Class<T> clazz) {
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        Map<String, Trgt.DomInfo> domInfoMap = new LinkedHashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ConsumeDom.class)) {
                ConsumeDom domAnnot = f.getAnnotation(ConsumeDom.class);
                f.setAccessible(true);
                String domName = getDomName(f);
                Trgt.DomInfo domInfo = Trgt.DomInfo.newBuilder()
                        .setName(domName)
                        .setDescription(domAnnot.description())
                        .build();
                domInfoMap.put(domName, domInfo);
            }
        }
        return domInfoMap;
    }

    public static <T> Map<String, Trgt.DomInfo> parseProduceDomInfo(Class<T> clazz) {
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        Map<String, Trgt.DomInfo> domInfoMap = new LinkedHashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ProduceDom.class)) {
                ProduceDom domAnnot = f.getAnnotation(ProduceDom.class);
                f.setAccessible(true);
                String domName = getDomName(f);
                Trgt.DomInfo domInfo = Trgt.DomInfo.newBuilder()
                        .setName(domName)
                        .setDescription(domAnnot.description())
                        .build();
                domInfoMap.put(domName, domInfo);
            }
        }
        return domInfoMap;
    }

    public static <T> Map<String, Trgt.RelInfo> parseConsumeRelInfo(Class<T> clazz) {
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        Map<String, Trgt.RelInfo> relInfoMap = new LinkedHashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ConsumeRel.class)) {
                ConsumeRel relAnnot = f.getAnnotation(ConsumeRel.class);
                String relName = getRelName(f);
                Trgt.RelInfo relInfo = Trgt.RelInfo.newBuilder()
                        .setName(relName)
                        .addAllDom(List.of(relAnnot.doms()))
                        .setDescription(relAnnot.description())
                        .build();
                relInfoMap.put(relName, relInfo);
            }
        }
        return relInfoMap;
    }

    public static <T> Map<String, Trgt.RelInfo> parseProduceRelInfo(Class<T> clazz) {
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        Map<String, Trgt.RelInfo> relInfoMap = new LinkedHashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ProduceRel.class)) {
                ProduceRel relAnnot = f.getAnnotation(ProduceRel.class);
                String relName = getRelName(f);
                Trgt.RelInfo relInfo = Trgt.RelInfo.newBuilder()
                        .setName(relName)
                        .addAllDom(List.of(relAnnot.doms()))
                        .setDescription(relAnnot.description())
                        .build();
                relInfoMap.put(relName, relInfo);
            }
        }
        return relInfoMap;
    }

    public static <T> Analysis.AnalysisInfo parseAnalysisInfo(Class<T> clazz) {
        if (!clazz.isAnnotationPresent(TeAAnalysis.class)) {
            Messages.error("AnalysisUtil: class '%s' is not annotated with @TeAAnalysis, ignored", clazz.getName());
            return null;
        }
        if (!AbstractAnalysis.class.isAssignableFrom(clazz)) {
            Messages.error("AnalysisUtil: class '%s' does not extends AbstractAnalysis, ignored", clazz.getName());
            return null;
        }

        String name = getAnalysisName(clazz);
        Analysis.AnalysisInfo.Builder infoBuilder = Analysis.AnalysisInfo.newBuilder();
        infoBuilder.setName(name);
        infoBuilder.addAllConsumingDom(parseConsumeDomInfo(clazz).values());
        infoBuilder.addAllProducingDom(parseProduceDomInfo(clazz).values());
        infoBuilder.addAllConsumingRel(parseConsumeRelInfo(clazz).values());
        infoBuilder.addAllProducingRel(parseProduceRelInfo(clazz).values());
        return infoBuilder.build();
    }

    private static <T> String getAnalysisName(Class<T> clazz) {
        TeAAnalysis analysisAnnot = clazz.getAnnotation(TeAAnalysis.class);
        String name = analysisAnnot.name();
        if (name.isBlank()) {
            Messages.warn("AnalysisUtil: anonymous analysis %s, using class name as analysis name", clazz.getName());
            name = clazz.getSimpleName();
        }
        return name;
    }

    private static String getDomName(Field f) {
        String domName = null;
        if (f.isAnnotationPresent(ConsumeDom.class)){
            domName = f.getAnnotation(ConsumeDom.class).name();
        } else if (f.isAnnotationPresent(ProduceDom.class)) {
            domName = f.getAnnotation(ProduceDom.class).name();
        } else {
            assert false;
        }
        if (domName.isBlank()) {
            domName = f.getName();
            if (domName.startsWith("dom")) {
                domName = domName.substring(3);
            }
        }
        return domName;
    }

    private static String getRelName(Field f) {
        String relName = null;
        if (f.isAnnotationPresent(ConsumeRel.class)){
            relName = f.getAnnotation(ConsumeRel.class).name();
        } else if (f.isAnnotationPresent(ProduceRel.class)) {
            relName = f.getAnnotation(ProduceRel.class).name();
        } else {
            assert false;
        }
        if (relName.isBlank()) {
            relName = f.getName();
            if (relName.startsWith("rel")) {
                relName = relName.substring(3);
            }
        }
        return relName;
    }

    private static <T> Map<String, ProgramDom> consumeDoms(T analysis, Map<String, String> domLocMap) throws IllegalAccessException {
        Class<?> clazz = analysis.getClass();
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        String analysisName = getAnalysisName(clazz);
        Map<String, ProgramDom> domMap = new LinkedHashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ConsumeDom.class)) {
                String domName = getDomName(f);
                String domLoc = domLocMap.get(domName);
                if (domLoc == null) {
                    Messages.fatal("AnalysisUtil: dom '%s' required by analysis '%s' not found", domName, analysisName);
                    assert false;
                }
                ProgramDom dom = new ProgramDom(domName);
                dom.load(domLoc);
                domMap.put(domName, dom);
                f.setAccessible(true);
                f.set(analysis, dom);
            }
        }
        return domMap;
    }

    private static <T> Map<String, ProgramRel> consumeRels(T analysis, Map<String, ProgramDom> domMap, Map<String, String> relLocMap) throws IllegalAccessException {
        Class<?> clazz = analysis.getClass();
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        String analysisName = getAnalysisName(clazz);
        Map<String, ProgramRel> relMap = new LinkedHashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ConsumeRel.class)) {
                String relName = getRelName(f);
                String relLoc = relLocMap.get(relName);
                if (relLoc == null) {
                    Messages.fatal("AnalysisUtil: rel '%s' required by analysis '%s' not found", relName, analysisName);
                    assert false;
                }
                String[] domNames = f.getAnnotation(ConsumeRel.class).doms();
                ProgramDom[] doms = new ProgramDom[domNames.length];
                for (int i = 0; i < doms.length; ++i) {
                    doms[i] = domMap.get(domNames[i]);
                    if (doms[i] == null) {
                        Messages.fatal("AnalysisUtil: dom '%s' required by rel '%s' of analysis '%s' not found", domNames[i], relName, analysisName);
                        assert false;
                    }
                }
                ProgramRel rel = new ProgramRel(relName, doms);
                rel.attach(relLoc);
                relMap.put(relName, rel);
                f.setAccessible(true);
                f.set(analysis, rel);
            }
        }
        return relMap;
    }

    private static <T> Map<String, ProgramDom> newDoms(T analysis) throws IllegalAccessException {
        Class<?> clazz = analysis.getClass();
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        Map<String, ProgramDom> domMap = new LinkedHashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ProduceDom.class)) {
                String domName = getDomName(f);
                ProgramDom dom = new ProgramDom(domName);
                domMap.put(domName, dom);
                f.setAccessible(true);
                f.set(analysis, dom);
            }
        }
        return domMap;
    }

    private static <T> Map<String, ProgramRel> newRels(T analysis, Map<String, ProgramDom> domMap) throws IllegalAccessException {
        Class<?> clazz = analysis.getClass();
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        String analysisName = getAnalysisName(clazz);
        Map<String, ProgramRel> relMap = new LinkedHashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ProduceRel.class)) {
                String relName = getRelName(f);
                String[] domNames = f.getAnnotation(ProduceRel.class).doms();
                ProgramDom[] doms = new ProgramDom[domNames.length];
                for (int i = 0; i < doms.length; ++i) {
                    doms[i] = domMap.get(domNames[i]);
                    if (doms[i] == null) {
                        Messages.fatal("AnalysisUtil: dom '%s' required by rel '%s' of analysis '%s' not found", domNames[i], relName, analysisName);
                    }
                }
                ProgramRel rel = new ProgramRel(relName, doms);
                relMap.put(relName, rel);
                f.setAccessible(true);
                f.set(analysis, rel);
            }
        }
        return relMap;
    }

    private static <T> Map<String, String> produceDoms(T analysis) throws IllegalAccessException {
        Class<?> clazz = analysis.getClass();
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        Map<String, String> domLocMap = new LinkedHashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ProduceDom.class)) {
                String domName = getDomName(f);
                f.setAccessible(true);
                String domLoc = ((ProgramDom) f.get(analysis)).getLocation();
                domLocMap.put(domName, domLoc);
            }
        }
        return domLocMap;
    }

    private static <T> Map<String, String> produceRels(T analysis) throws IllegalAccessException {
        Class<?> clazz = analysis.getClass();
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        Map<String, String> relLocMap = new LinkedHashMap<>();
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ProduceRel.class)) {
                String relName = getRelName(f);
                f.setAccessible(true);
                String relLoc = ((ProgramRel) f.get(analysis)).getLocation();
                relLocMap.put(relName, relLoc);
            }
        }
        return relLocMap;
    }

    private static <T> void openDoms(T analysis) throws IllegalAccessException {
        Class<?> clazz = analysis.getClass();
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ProduceDom.class)) {
                f.setAccessible(true);
                ((ProgramDom) f.get(analysis)).init();
            }
        }
    }

    private static <T> void saveDoms(T analysis, String outDir) throws IllegalAccessException {
        Class<?> clazz = analysis.getClass();
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ProduceDom.class)) {
                f.setAccessible(true);
                ((ProgramDom) f.get(analysis)).save(outDir);
            }
        }
    }

    private static <T> void openRels(T analysis) throws IllegalAccessException {
        Class<?> clazz = analysis.getClass();
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ProduceRel.class)) {
                f.setAccessible(true);
                ((ProgramRel) f.get(analysis)).init();
            }
        }
    }

    private static <T> void saveRels(T analysis, String outDir) throws IllegalAccessException {
        Class<?> clazz = analysis.getClass();
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ProduceRel.class)) {
                f.setAccessible(true);
                ProgramRel rel = (ProgramRel) f.get(analysis);
                rel.save(outDir);
                rel.close();
            }
        }
    }

    private static <T> void loadRels(T analysis) throws IllegalAccessException {
        Class<?> clazz = analysis.getClass();
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ConsumeRel.class)) {
                f.setAccessible(true);
                ((ProgramRel) f.get(analysis)).load();
            }
        }
    }

    private static <T> void closeRels(T analysis) throws IllegalAccessException {
        Class<?> clazz = analysis.getClass();
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(ConsumeRel.class)) {
                f.setAccessible(true);
                ((ProgramRel) f.get(analysis)).close();
            }
        }
    }

    public static <T extends AbstractAnalysis> Pair<Map<String, String>, Map<String, String>> runAnalysis(T analysis, Map<String, String> inputDomLocMap, Map<String, String> inputRelLocMap) {
        Class<?> clazz = analysis.getClass();
        assert clazz.isAnnotationPresent(TeAAnalysis.class);
        String analysisName = getAnalysisName(clazz);
        try {
            Map<String, ProgramDom> inputDomMap = consumeDoms(analysis, inputDomLocMap);

            Map<String, ProgramDom> domMap = new LinkedHashMap<>(inputDomMap);

            Map<String, ProgramRel> inputRelMap = consumeRels(analysis, domMap, inputRelLocMap);

            Map<String, ProgramDom> outputDomMap = newDoms(analysis);
            domMap.putAll(outputDomMap);

            Map<String, ProgramRel> outputRelMap = newRels(analysis, domMap);

            loadRels(analysis);
            openDoms(analysis);
            analysis.domPhase();
            saveDoms(analysis, analysis.getOutDir());

            openRels(analysis);
            analysis.relPhase();
            closeRels(analysis);
            saveRels(analysis, analysis.getOutDir());

            Map<String, String> outputDomLocMap = produceDoms(analysis);
            Map<String, String> outputRelLocMap = produceRels(analysis);

            return new ImmutablePair<>(outputDomLocMap, outputRelLocMap);
        } catch (IllegalAccessException e) {
            Messages.error("AnalysisUtil: doms and rels should be marked public!");
            Messages.fatal(e);
            assert false;
        }
        return null;
    }

    public static <T extends AbstractAnalysis> Analysis.RunResults runAnalysis(T analysis, Analysis.RunRequest request) {
        String analysisName = getAnalysisName(analysis.getClass());
        assert analysisName.equals(request.getAnalysisName());
        Map<String, String> inputDomMap = new LinkedHashMap<>();
        for (Trgt.DomTrgt inputDom : request.getDomInputList()) {
            inputDomMap.put(inputDom.getInfo().getName(), inputDom.getLocation());
        }
        Map<String, String> inputRelMap = new LinkedHashMap<>();
        for (Trgt.RelTrgt inputRel : request.getRelInputList()) {
            inputRelMap.put(inputRel.getInfo().getName(), inputRel.getLocation());
        }

        Pair<Map<String, String>, Map<String, String>> output = runAnalysis(analysis, inputDomMap, inputRelMap);

        Analysis.RunResults.Builder resultBuilder = Analysis.RunResults.newBuilder();
        if (output == null) {
            resultBuilder.setMsg(Constants.MSG_FAIL);
        } else {
            resultBuilder.setMsg(Constants.MSG_SUCC);
            for (var domEntry : parseProduceDomInfo(analysis.getClass()).entrySet()) {
                String domName = domEntry.getKey();
                Trgt.DomInfo domInfo = domEntry.getValue();
                String loc = output.getLeft().get(domName);
                assert loc != null;
                resultBuilder.addDomOutput(Trgt.DomTrgt.newBuilder()
                        .setInfo(domInfo)
                        .setLocation(loc));
            }
            for (var relEntry : parseProduceRelInfo(analysis.getClass()).entrySet()) {
                String relName = relEntry.getKey();
                Trgt.RelInfo relInfo = relEntry.getValue();
                String loc = output.getRight().get(relName);
                assert loc != null;
                resultBuilder.addRelOutput(Trgt.RelTrgt.newBuilder()
                        .setInfo(relInfo)
                        .setLocation(loc));
            }
        }
        return resultBuilder.build();
    }
}
