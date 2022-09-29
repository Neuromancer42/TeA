package com.neuromancer42.tea.program.cdt.parser;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.project.Messages;

public class CParserAnalysis extends JavaAnalysis {
    private final CParser cParser;

    public CParserAnalysis() {
        this.name = "CParser";
        this.cParser = new CParser();
        for (ProgramDom<?> dom : cParser.generatedDoms)
            createDomProducer(dom.getName(), dom.getContentType());
        for (ProgramRel rel : cParser.generatedRels)
            createRelProducer(rel.getName(), rel.getSign());
    }

    @Override
    public void run() {
        String fileName = System.getProperty("chord.source.path");
        if (fileName == null) {
            Messages.fatal("CParser: no source file is given. Use -Dchord.source.path=<path> to set.");
        }
        cParser.run(fileName);
        for (ProgramDom<?> dom : cParser.generatedDoms)
            produceDom(dom);
        for (ProgramRel rel : cParser.generatedRels)
            produceRel(rel);
    }
}
