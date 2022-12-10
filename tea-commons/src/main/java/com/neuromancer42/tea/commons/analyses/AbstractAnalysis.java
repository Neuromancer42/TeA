package com.neuromancer42.tea.commons.analyses;

public abstract class AbstractAnalysis {
    protected abstract void domPhase();
    protected abstract void relPhase();
    protected abstract String getOutDir();
}
