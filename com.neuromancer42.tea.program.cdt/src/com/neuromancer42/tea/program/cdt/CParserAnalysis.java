package com.neuromancer42.tea.program.cdt;

import com.neuromancer42.tea.core.analyses.AnalysesUtil;
import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.project.Messages;

public class CParserAnalysis extends JavaAnalysis {
    private final CParser cParser;

    public CParserAnalysis() {
        String fileName = System.getProperty("chord.source.path");
        if (fileName == null) {
            Messages.fatal("CParser: no source file is given. Use -Dchord.source.path=<path> to set.");
        }
        this.cParser = new CParser(fileName);
        this.name = "CParser";
        registerProducer(AnalysesUtil.createInitializedDomTrgt(cParser.domM, name));
        registerProducer(AnalysesUtil.createInitializedDomTrgt(cParser.domP, name));
        registerProducer(AnalysesUtil.createInitializedRelTrgt(cParser.relMPentry, name));
    }

    @Override
    public void run() {
        cParser.run();
    }
}
