package com.neuromancer42.tea.souffle;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;

public class SouffleAnalysis extends JavaAnalysis {
    private boolean isBuilt = false;
    private String filename;
    public SouffleAnalysis() {

    }

    private static class SouffleRuntime {

    }
    // TODO: each analysis consists of one (or more) souffle file, runtime should
    // TODO: 0/ runtime(): in base class of SouffleAnalysis, (static methods) compile swig/SwigInterface.h and load it by System.load()
    // TODO: 1/ setup() compile and build datalog files
    // TODO:    1.1/ read <name>.dl from bundle and copy to <tmpDIR>
    // TODO:    1.2/ use souffle command to translate <name>.dl to <name>.cpp (provenance option only here)
    // TODO:    1.3/ use g++ command to <name>.o --> <name>.dylib/so/dll
    // TODO:    1.4/ System.load()
    // TODO: 2/ register() provide handlers and register them to framework
    // TODO: 3/ provenance() repeat step 1.1~1.3 with provenance option
    // TODO:    3+/ swig/SwigInterface.h has c++ implementation of provenance generation, but it does nothing without provenance option
}
