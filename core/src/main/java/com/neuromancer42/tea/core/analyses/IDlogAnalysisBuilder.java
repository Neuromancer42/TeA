package com.neuromancer42.tea.core.analyses;

import java.io.File;
import java.io.InputStream;

public interface IDlogAnalysisBuilder {
    JavaAnalysis createAnalysisFromFile(String name, String analysis, File dlogFile);

    JavaAnalysis createAnalysisFromStream(String name, String analysis, InputStream dlogStream);
}
