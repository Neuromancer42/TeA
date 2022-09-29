package com.neuromancer42.tea.core.analyses;

import com.neuromancer42.tea.core.bddbddb.RelSign;
import com.neuromancer42.tea.core.project.ITask;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.Trgt;

import java.util.*;

/**
 * Generic implementation of a Java task (a program analysis
 * expressed in Java).
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public abstract class JavaAnalysis implements ITask {
    protected String name;

    public Map<String, Trgt<?>> getConsumerMap() {
        return consumerMap;
    }

    public Map<String, Trgt<?>> getProducerMap() {
        return producerMap;
    }

    private final Map<String, Trgt<?>> consumerMap = new HashMap<>();
    private final Map<String, Trgt<?>> producerMap = new HashMap<>();
    //private final List<Object> controls = new ArrayList<>();
    @Override
    public void setName(String name) {
        assert (name != null);
        assert (this.name == null);
        this.name = name;
    }
    @Override
    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }

    protected final void registerConsumer(Trgt<?> trgt) {
        consumerMap.put(trgt.getName(), trgt);
    }

    protected final void registerProducer(Trgt<?> trgt) {
        producerMap.put(trgt.getName(), trgt);
    }

    protected final <T> T consume(String trgtName) {
        Trgt<?> trgt = consumerMap.get(trgtName);
        if (trgt == null) {
            Messages.fatal("JavaAnalysis %s: consuming trgt %s does not exist", name, trgtName);
            assert false;
        }
        return (T) trgt.get();
    }

    protected final <T> void produce(String trgtName, T obj) {
        Trgt<T> trgt = (Trgt<T>) producerMap.get(trgtName);
        if (trgt == null) {
            Messages.fatal("JavaAnalysis %s: producing trgt %s does not exist", name, trgtName);
            assert false;
        }
        trgt.accept(obj);
    }

    protected final <T> Trgt<T> createConsumer(String trgtName, Class<T> trgtType) {
        Trgt<T> trgt = Trgt.createTrgt(trgtName, trgtType, name);
        registerConsumer(trgt);
        return trgt;
    }

    protected final <T> Trgt<T> createProducer(String trgtName, Class<T> trgtType) {
        Trgt<T> trgt = Trgt.createTrgt(trgtName, trgtType, name);
        registerProducer(trgt);
        return trgt;
    }

    protected final <T> Trgt<ProgramDom<T>> createDomConsumer(String domName, Class<T> domType) {
        Trgt<ProgramDom<T>> domTrgt = createDomTrgt(domName, domType);
        registerConsumer(domTrgt);
        return domTrgt;
    }

    protected final <T> Trgt<ProgramDom<T>> createDomProducer(String domName, Class<T> domType) {
        Trgt<ProgramDom<T>> domTrgt = createDomTrgt(domName, domType);
        registerProducer(domTrgt);
        return domTrgt;
    }

    private <T> Trgt<ProgramDom<T>> createDomTrgt(String domName, Class<T> domType) {
        if (name == null) {
            Messages.error("JavaAnalysis: analysis name must be specified first");
        }
        DomInfo domInfo = new DomInfo(name, domType);
        return new Trgt<>(domName, domInfo);
    }

    protected final Trgt<ProgramRel> createRelConsumer(String relName, String ... rawDomNames) {
        Trgt<ProgramRel> relTrgt = createRelTrgt(relName, rawDomNames);
        registerConsumer(relTrgt);
        return relTrgt;
    }

    protected final Trgt<ProgramRel> createRelConsumer(String relName, RelSign relSign) {
        Trgt<ProgramRel> relTrgt = createRelTrgt(relName, relSign);
        registerConsumer(relTrgt);
        return relTrgt;
    }

    protected final Trgt<ProgramRel> createRelProducer(String relName, String ... rawDomNames) {
        Trgt<ProgramRel> relTrgt = createRelTrgt(relName, rawDomNames);
        registerProducer(relTrgt);
        return relTrgt;
    }

    protected final Trgt<ProgramRel> createRelProducer(String relName, RelSign relSign) {
        Trgt<ProgramRel> relTrgt = createRelTrgt(relName, relSign);
        registerProducer(relTrgt);
        return relTrgt;
    }

    private Trgt<ProgramRel> createRelTrgt(String relName, RelSign relSign) {
        if (name == null) {
            Messages.error("JavaAnalysis: analysis name must be specified first");
        }
        RelInfo relInfo = new RelInfo(name, relSign);
        return new Trgt<>(relName, relInfo);
    }

    private Trgt<ProgramRel> createRelTrgt(String relName, String ... rawDomNames) {
        RelSign relSign = ProgramRel.genDefaultRelSign(rawDomNames);
        return createRelTrgt(relName, relSign);
    }

    protected final void produceDom(ProgramDom<?> dom) {
        produce(dom.getName(), dom);
    }

    protected final void produceRel(ProgramRel rel) {
        produce(rel.getName(), rel);
    }
}
