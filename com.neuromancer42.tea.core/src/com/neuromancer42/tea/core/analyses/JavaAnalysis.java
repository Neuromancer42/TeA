package com.neuromancer42.tea.core.analyses;

import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.ITask;
import com.neuromancer42.tea.core.util.tuple.object.Pair;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Generic implementation of a Java task (a program analysis
 * expressed in Java).
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public abstract class JavaAnalysis implements ITask {
    private static final String UNDEFINED_RUN = "ERRROR: Analysis '%s' must override method 'run()'";
    protected String name;
    protected Map<String, Pair<TrgtInfo, Consumer<Object>>> consumerMap = new HashMap<>();
    protected Map<String, Pair<TrgtInfo, Supplier<Object>>> producerMap = new HashMap<>();
    protected Object[] controls;
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

    protected abstract void setConsumerMap();
    protected abstract void setProducerMap();
}
