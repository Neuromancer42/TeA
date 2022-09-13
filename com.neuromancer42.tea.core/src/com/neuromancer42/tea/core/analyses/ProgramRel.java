package com.neuromancer42.tea.core.analyses;

import com.neuromancer42.tea.core.bddbddb.Dom;
import com.neuromancer42.tea.core.bddbddb.RelSign;
import com.neuromancer42.tea.core.project.Config;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.ITask;

import com.neuromancer42.tea.core.bddbddb.Rel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * Generic implementation of a program relation (a specialized kind of Java task).
 * <p>
 * A program relation is a relation over one or more program domains.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 * @author Saswat Anand
 *
 * It encapsultates a Rel with a state machine and shields unused methods
 *
 * @author Yifan Chen
 */
public class ProgramRel implements ITask {
    private static final String SKIP_TUPLE =
        "WARN: Skipping a tuple from relation '%s' as element '%s' was not found in domain '%s'.";
    protected Object[] consumes;
    protected final Rel rel;

    public Iterable<Object[]> getValTuples() {
        return rel.getAryNValTuples();
    }

    public Iterable<int[]> getIntTuples() { return rel.getAryNIntTuples(); }

    public Dom<?>[] getDoms() {
        return rel.getDoms();
    }

    public RelSign getSign() {
        return rel.getSign();
    }

    /**
     * Using a state-machine to control file cache
     */
    private enum Status {
        UnInit, UnSync, Sync, Detach
    }

    private Status status = Status.UnInit;

    public ProgramRel(String relName, Dom<?>[] doms, String[] domNames, String domOrder) {
        rel = new Rel();
        rel.setName(relName);
        rel.setSign(domNames, domOrder);
        rel.setDoms(doms);
    }

    public ProgramRel(String relName, Dom<?>[] doms, RelSign relSign) {
        rel = new Rel();
        rel.setName(relName);
        rel.setSign(relSign);
        rel.setDoms(doms);
    }

    public ProgramRel(String relName, Dom<?>[] doms) {
        String[] rawDomNames = new String[doms.length];
        for (int i = 0; i < doms.length; ++i) {
            rawDomNames[i] = doms[i].getName();
        }
        RelSign defaultRelSign = genDefaultRelSign(rawDomNames);

        rel = new Rel();
        rel.setName(relName);
        rel.setSign(defaultRelSign);
        rel.setDoms(doms);
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

    /**
     * Sets the name of this analysis.
     *
     * @param name A name unique across all analyses included
     *             in a Chord project.
     */
    @Override
    public void setName(String name) {
        rel.setName(name);
    }

    /**
     * Provides the name of this analysis.
     *
     * @return The name of this analysis.
     */
    @Override
    public String getName() {
        return rel.getName();
    }

    @Override
    public void run() {
        init();
        fill();
        save();
        close();
    }
    public void init() {
        if (status != Status.UnInit) {
            Messages.warn("ProgramRel %s: Overriding initialized rel", getName());
        }
        rel.zero();
        status = Status.UnSync;
    }

    public void save() {
        switch (status) {
            case UnSync:
                Messages.debug("SAVING rel " + rel.getName() + " size: " + rel.size());
                rel.save(Config.v().bddbddbWorkDirName);
                status = Status.Sync;
                break;
            case UnInit:
                Messages.fatal("Rel %s: saving uninitialized rel", getName());
        }
    }

    public void close() {
        switch (status) {
            case UnInit:
                Messages.fatal("Rel %s: closing uninitialized rel", getName());
                break;
            case UnSync:
                Messages.warn("Rel %s: discarding unsaved rel", getName());
                rel.close();
                status = Status.UnInit;
                break;
            case Sync:
                rel.close();
                status = Status.Detach;
                break;
            case Detach:
                Messages.warn("Rel %s: already detached", getName());
        }
    }

    public void load() {
        if (status == Status.Sync) {
            Messages.warn("Rel %s: loading already sync-ed rel", getName());
        }
        if (status == Status.UnSync) {
            Messages.warn("ProgramRel %s: Overriding unsync-ed rel", getName());
        }
        rel.load(Config.v().bddbddbWorkDirName);
        status = Status.Sync;
    }

    public void fill()
	{
		throw new RuntimeException("implement");
	}

    public void print() {
        if (status == Status.UnInit) {
            Messages.fatal("Rel %s: printing uninitialized rel", getName());
        }
        if (status == Status.Detach) {
            Messages.fatal("Rel %s: printing detached rel", getName());
        }
        rel.print(Config.v().outDirName);
    }
    public String toString() {
        return rel.getName();
    }

    public void skip(Object elem, ProgramDom<?> dom) {
        Messages.log(SKIP_TUPLE, getClass().getName(), elem, dom.getClass().getName());
    }

    public int size() {
        return rel.size();
    }

    public void add(Object... vals) {
        rel.add(vals);
        status = Status.UnSync;
    }

    public void remove(Object... vals) {
        rel.remove(vals);
        status = Status.UnSync;
    }

    public boolean contains(Object... vals) {
        return rel.contains(vals);
    }

    public void add(int[] idxs) {
        rel.add(idxs);
        status = Status.UnSync;
    }

    public void remove(int[] idxs) {
        rel.remove(idxs);
        status = Status.UnSync;
    }

    public boolean contains(int[] idxs) {
        return rel.contains(idxs);
    }
}
