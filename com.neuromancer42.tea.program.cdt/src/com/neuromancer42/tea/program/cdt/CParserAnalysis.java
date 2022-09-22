package com.neuromancer42.tea.program.cdt;

import com.neuromancer42.tea.core.analyses.AnalysesUtil;
import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.program.cdt.internal.CParser;

public class CParserAnalysis extends JavaAnalysis {
    private final CParser cParser;

    public CParserAnalysis() {
        this.name = "CParser";
        String fileName = System.getProperty("chord.source.path");
        if (fileName == null) {
            Messages.fatal("CParser: no source file is given. Use -Dchord.source.path=<path> to set.");
        }
        this.cParser = new CParser(fileName);
        for (ProgramDom<?> dom : cParser.generatedDoms)
            registerProducer(AnalysesUtil.createInitializedDomTrgt(name, dom));
        for (ProgramRel rel : cParser.generatedRels)
            registerProducer(AnalysesUtil.createInitializedRelTrgt(name, rel));
    }

    @Override
    public void run() {
        cParser.run();
    }
}
