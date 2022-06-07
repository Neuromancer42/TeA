package com.neuromancer42.tea.core.project;

import java.util.Map;
import java.util.Set;

/**
 * A Chord project comprising a set of tasks and a set of targets
 * produced/consumed by those tasks.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public abstract class Project {
    protected static Project project = null;

    public static void setProject(Project project) {
        Project.project = project;
    }

    public static Project g() {
        return project;
    }
    // build the project (process all java/dlog tasks)
    public abstract void build();
    // fetch available tasks
    public abstract Set<String> getTasks();
    // run specified tasks
    public abstract void run(String[] taskNames);
    // fetch available targets generated after running
    public abstract Map<String, Object> getTrgts();
    // print specified relations
    public abstract void printRels(String[] relNames);
    // print the project (all tasks and trgts and dependencies b/w them)
    public abstract void print();
    protected void abort() {
        System.err.println("Found errors (see above). Exiting ...");
        System.exit(1);
    }
}
