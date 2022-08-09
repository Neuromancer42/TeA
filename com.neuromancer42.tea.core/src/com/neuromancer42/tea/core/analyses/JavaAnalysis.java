package com.neuromancer42.tea.core.analyses;

import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.ITask;
import com.neuromancer42.tea.core.project.Trgt;

import java.util.*;

/**
 * Generic implementation of a Java task (a program analysis
 * expressed in Java).
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public abstract class JavaAnalysis implements ITask {
    private static final String UNDEFINED_RUN = "ERRROR: Analysis '%s' must override method 'run()'";
    protected String name;

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
    public void run() {
        Messages.fatal(UNDEFINED_RUN, name);
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

    public Dictionary<String, Object> genAnalysisProperties() {
        Dictionary<String, Object> props = new Hashtable<>();
        props.put("name", this.name);
        props.put("input", this.consumerMap);
        props.put("output", this.producerMap);
        return props;
    }
}
