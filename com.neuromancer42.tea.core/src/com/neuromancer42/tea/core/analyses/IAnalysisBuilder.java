package com.neuromancer42.tea.core.analyses;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;

public interface IAnalysisBuilder {
    String getName();
    void buildAnalyses(String ... analysisNames);
    JavaAnalysis getAnalysis(String analysisName);
    String[] availableAnalyses();
}
