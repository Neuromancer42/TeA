package com.neuromancer42.tea.core.project;

/**
 * Specification of an analysis.
 * 
 * @author Mayur Naik (mhn@cs.stanford.edu)
 */
public interface ITask {
    /**
     * Sets the name of this analysis.
     * 
     * @param    name    A name unique across all analyses included
     * in a Chord project.
     */
    void setName(String name);
    /**
     * Provides the name of this analysis.
     * 
     * @return    The name of this analysis.
     */
    String getName();
    /**
     * Executes this analysis in a "classic" project.
     *
     * This method must usually not be called directly.
     * The correct way to call it is to call
     * {@link com.neuromancer42.tea.core.project.Project#run(String[] taskNames)}, providing
     * this analysis either by its name.
     */
    void run();
}
