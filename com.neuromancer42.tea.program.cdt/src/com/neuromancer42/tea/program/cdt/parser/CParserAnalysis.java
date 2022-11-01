package com.neuromancer42.tea.program.cdt.parser;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.project.Messages;

import java.io.File;
import java.util.*;

public class CParserAnalysis extends JavaAnalysis {
    private final CParser cParser;
    public static final String analysisName = "CParser";

    public CParserAnalysis() {
        this.name = analysisName;
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
        String[] flags = System.getProperty("chord.source.flags", "").split(" ");
        List<String> includePaths = new ArrayList<>();
        Map<String, String> definedSymbols = new LinkedHashMap<>();
        for (String flag : flags) {
            if (flag.startsWith("-I")) {
                for (String includePath : flag.substring(2).split(File.pathSeparator))  {
                    Messages.log("CParser: add include path %s", includePath);
                    includePaths.add(includePath);
                }
            } else if (flag.startsWith("-D")) {
                String[] pair = flag.substring(2).split("=");
                String symbol = pair[0];
                String value = "";
                if (pair.length > 1)
                    value = pair[1];
                Messages.log("CParser: add defined symbol %s=%s", symbol, value);
                definedSymbols.put(symbol, value);
            }
        }
        cParser.run(fileName, definedSymbols, includePaths.toArray(new String[]{}));
        for (ProgramDom<?> dom : cParser.generatedDoms)
            produceDom(dom);
        for (ProgramRel rel : cParser.generatedRels)
            produceRel(rel);
    }

    public CParser getParser() {
        return cParser;
    }
}
