package com.neuromancer42.tea.commons.bddbddb;

import com.neuromancer42.tea.commons.configs.Messages;

import java.util.Arrays;
import java.util.HashMap;
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
public class ProgramRel {
    private static final String SKIP_TUPLE =
        "WARN: Skipping a tuple from relation '%s' as element '%s' was not found in domain '%s'.";
    protected Object[] consumes;
    protected final Rel rel;

    public Iterable<Object[]> getValTuples() {
        if (status == Status.UnInit) {
            Messages.fatal("ProgramRel %s: iterating uninitialized rel", getName());
        }
        if (status == Status.Detach) {
            Messages.fatal("ProgramRel %s: iterating detached rel", getName());
        }
        return rel.getAryNValTuples();
    }

    public Iterable<int[]> getIntTuples() {
        if (status == Status.UnInit) {
            Messages.fatal("ProgramRel %s: iterating uninitialized rel", getName());
        }
        if (status == Status.Detach) {
            Messages.fatal("ProgramRel %s: iterating detached rel", getName());
        }
        return rel.getAryNIntTuples();
    }

    public Object[] getValTuple(int[] intTuple) {
        Dom<?>[] doms = rel.getDoms();
        if (intTuple.length != doms.length) {
            Messages.fatal("ProgramRel %s: arity mismatch for tuple (%s)", getName(), Arrays.toString(intTuple));
        }

        Object[] valTuple = new Object[intTuple.length];
        if (!contains(intTuple)) {
            Messages.error("ProgramRel %s: tuple (%s) does not exist in this rel", getName(), Arrays.toString(intTuple));
        }
        for (int i = 0; i < intTuple.length; ++i) {
            valTuple[i] = doms[i].get(intTuple[i]);
        }
        return valTuple;
    }

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

    private String cacheLocation;

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

    public ProgramRel(String relName, RelSign relSign, Dom<?> ... doms) {
        this(relName, doms, relSign);
    }

    public ProgramRel(String relName, Dom<?> ... doms) {
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
                Messages.fatal("ProgramRel: raw domain name (%s) should be restricted to english letters only", rawDomName);
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
    public void setName(String name) {
        rel.setName(name);
    }

    /**
     * Provides the name of this analysis.
     *
     * @return The name of this analysis.
     */
    public String getName() {
        return rel.getName();
    }

    public void init() {
        if (status != Status.UnInit) {
            Messages.warn("ProgramRel %s: Overriding initialized rel", getName());
        }
        rel.zero();
        status = Status.UnSync;
    }

    public void save(String location) {
        switch (status) {
            case UnSync:
                Messages.log("SAVING rel " + rel.getName() + " size: " + rel.size());
                rel.save(location);
                cacheLocation = location;
                status = Status.Sync;
                break;
            case UnInit:
                Messages.fatal("ProgramRel %s: saving uninitialized rel", getName());
        }
    }

    public void close() {
        switch (status) {
            case UnInit:
                Messages.fatal("ProgramRel %s: closing uninitialized rel", getName());
                break;
            case UnSync:
                Messages.warn("ProgramRel %s: discarding unsaved rel", getName());
                rel.close();
                status = Status.UnInit;
                break;
            case Sync:
                rel.close();
                status = Status.Detach;
                break;
            case Detach:
                Messages.warn("ProgramRel %s: already detached", getName());
        }
    }

    public void load() {
        if (status != Status.Detach && status != Status.Sync) {
            Messages.fatal("ProgramRel %s: not saved before re-loading");
        }
        if (status == Status.Sync) {
            Messages.warn("ProgramRel %s: loading already sync-ed rel", getName());
        }
        assert cacheLocation != null;
        rel.load(cacheLocation);
        status = Status.Sync;
    }

    public void attach(String location) {
        if (status != Status.UnInit) {
            Messages.fatal("ProgramRel %s: resetting in-memory relation from disk", getName());
        }
        rel.zero();
        this.cacheLocation = location;
        status = Status.Detach;
    }

    public void print(String location) {
        if (status == Status.UnInit) {
            Messages.fatal("ProgramRel %s: printing uninitialized rel", getName());
        }
        if (status == Status.Detach) {
            Messages.fatal("ProgramRel %s: printing detached rel", getName());
        }
        rel.print(location);
    }

    public String toString() {
        return rel.getName();
    }

    public int size() {
        if (status == Status.UnInit) {
            Messages.fatal("ProgramRel %s: querying uninitialized rel", getName());
        }
        if (status == Status.Detach) {
            Messages.fatal("ProgramRel %s: querying detached rel", getName());
        }
        return rel.size();
    }

    public void add(Object... vals) {
        if (status == Status.UnInit) {
            Messages.fatal("ProgramRel %s: modifying uninitialized rel", getName());
        }
        if (status == Status.Detach) {
            Messages.fatal("ProgramRel %s: modifying detached rel", getName());
        }
        rel.add(vals);
        status = Status.UnSync;
    }

    public void remove(Object... vals) {
        if (status == Status.UnInit) {
            Messages.fatal("ProgramRel %s: modifying uninitialized rel", getName());
        }
        if (status == Status.Detach) {
            Messages.fatal("ProgramRel %s: modifying detached rel", getName());
        }
        rel.remove(vals);
        status = Status.UnSync;
    }

    public boolean contains(Object... vals) {
        if (status == Status.UnInit) {
            Messages.fatal("ProgramRel %s: querying uninitialized rel", getName());
        }
        if (status == Status.Detach) {
            Messages.fatal("ProgramRel %s: querying detached rel", getName());
        }
        return rel.contains(vals);
    }

    public void add(int[] idxs) {
        if (status == Status.UnInit) {
            Messages.fatal("ProgramRel %s: modifying uninitialized rel", getName());
        }
        if (status == Status.Detach) {
            Messages.fatal("ProgramRel %s: modifying detached rel", getName());
        }
        rel.add(idxs);
        status = Status.UnSync;
    }

    public void remove(int[] idxs) {
        if (status == Status.UnInit) {
            Messages.fatal("ProgramRel %s: modifying uninitialized rel", getName());
        }
        if (status == Status.Detach) {
            Messages.fatal("ProgramRel %s: modifying detached rel", getName());
        }
        rel.remove(idxs);
        status = Status.UnSync;
    }

    public boolean contains(int[] idxs) {
        if (status == Status.UnInit) {
            Messages.fatal("ProgramRel %s: querying uninitialized rel", getName());
        }
        if (status == Status.Detach) {
            Messages.fatal("ProgramRel %s: querying detached rel", getName());
        }
        return rel.contains(idxs);
    }

    public String getLocation() {
        return cacheLocation;
    }

}
