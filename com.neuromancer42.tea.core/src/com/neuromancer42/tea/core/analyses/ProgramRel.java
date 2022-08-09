package com.neuromancer42.tea.core.analyses;

import com.neuromancer42.tea.core.project.Config;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.ITask;

import com.neuromancer42.tea.core.bddbddb.Rel;


/**
 * Generic implementation of a program relation (a specialized kind of Java task).
 * <p>
 * A program relation is a relation over one or more program domains.
 *
 * @author Mayur Naik (mhn@cs.stanford.edu)
 * @author Saswat Anand
 */
public class ProgramRel extends Rel implements ITask {
    private static final String SKIP_TUPLE =
        "WARN: Skipping a tuple from relation '%s' as element '%s' was not found in domain '%s'.";
    protected Object[] consumes;
    @Override
    public void run() {
        init();
        fill();
        save();
        close();
    }
    public void init() {
        zero();
    }

    public void save() {
        if (Config.v().verbose >= 1)
            System.out.println("SAVING rel " + name + " size: " + size());
        super.save(Config.v().bddbddbWorkDirName);
    }

    public void load() {
        super.load(Config.v().bddbddbWorkDirName);
    }

    public void fill()
	{
		throw new RuntimeException("implement");
	}

    public void print() {
        super.print(Config.v().outDirName);
    }
    public String toString() {
        return name;
    }
    public void skip(Object elem, ProgramDom<?> dom) {
        Messages.log(SKIP_TUPLE, getClass().getName(), elem, dom.getClass().getName());
    }
}
