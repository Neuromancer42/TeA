package com.neuromancer42.tea.program.cdt;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.analyses.TrgtInfo;
import com.neuromancer42.tea.core.bddbddb.RelSign;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.util.tuple.object.Pair;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.core.runtime.CoreException;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CParser extends JavaAnalysis {

    private final DomM domM = new DomM();
    private final DomP domP = new DomP();
//    private DomIU domIU;
//    private DomITV domITV;
//    private DomOP domOP;
//    private DomITVP domITVP;

    private final ProgramRel relMPentry = new ProgramRel();


    public CParser() {
        this.name = "CParser";
    }

    @Override
    protected void setConsumerMap() {
        // generate basic facts, consume nothing
    }

    @Override
    protected void setProducerMap() {
        // TODO: move this into builder method
        domM.setName("M");
        domP.setName("P");
        // generate basic facts
        ProgramDom[] producedDoms = new ProgramDom[]{domM, domP};
        for (ProgramDom dom : producedDoms) {
            TrgtInfo domInfo = new TrgtInfo(dom.getClass(), name, null);
            producerMap.put(dom.getName(), new Pair<>(domInfo, () -> dom));
        }
        // TODO: move this into builder method
        relMPentry.setName("MPentry");
        relMPentry.setSign("M0,P0", "M0_P0");
        relMPentry.setDoms(new ProgramDom[]{domM, domP});
        TrgtInfo relInfo = new TrgtInfo(relMPentry.getClass(), name, relMPentry.getSign());
        // TODO: Rel.save erases bdd; do some refactering
        producerMap.put(relMPentry.getName(), new Pair<>(relInfo, () -> { relMPentry.load(); return relMPentry; } ));
    }


    public void run() {
        String fileName = System.getProperty("chord.source.path");
        if (fileName == null) {
            Messages.fatal("CParser: no source file is given. Use -Dchord.source.path=<path> to set.");
        }
        File sourceFile = new File(fileName);
        if (!sourceFile.isFile()) {
            Messages.fatal("CParser: the referenced path %s is not a source file", sourceFile.toString());
        }
        FileContent fileContent = FileContent.createForExternalFileLocation(fileName);
        Map<String, String> definedSymbols = new HashMap<>();
        String[] includePaths = new String[0];
        IScannerInfo scannerInfo = new ScannerInfo(definedSymbols, includePaths);
        IParserLogService log = new DefaultLogService();
        IncludeFileContentProvider emptyIncludes = IncludeFileContentProvider.getEmptyFilesProvider();
        int opts = 8; //TODO: read documents?

        IASTTranslationUnit translationUnit = null;
        try {
            translationUnit = GCCLanguage.getDefault().getASTTranslationUnit(fileContent, scannerInfo, emptyIncludes, null, opts, log);
        } catch (CoreException e) {
            Messages.error("CParser: failed to crete parser for file %s, exit.", fileName);
            Messages.fatal(e);
        }

        // TODO: move this into function template
        openDomains();
        collectDomains(translationUnit, 1);
        saveDomains();

        openRelations();
        collectRelations(translationUnit, 1);
        saveRelations();
    }

    private void openDomains() {
        domM.init();
        domP.init();
    }
    private void saveDomains() {
        domM.save();
        domP.save();
    }

    private void openRelations() {
        relMPentry.zero();
    }
    private void saveRelations() {
        relMPentry.save();
    }

    private void collectDomains(IASTNode node, int index) {
        IASTNode[] children = node.getChildren();
        //Messages.log("CParser: collecting domains in node %s", node);
        if (node instanceof IASTFunctionDefinition) {
            var func = (IASTFunctionDefinition) node;
            Messages.log("CParser: found function <%s>, entry point: %s", func.getRawSignature(), func.getBody().toString());
            domM.add(func);
            domP.add(func.getBody());
        }
        for (IASTNode child : children) {
            collectDomains(child, index + 1);
        }
    }

    private void collectRelations(IASTNode node, int index) {
        IASTNode[] children = node.getChildren();
        //Messages.log("CParser: collection relations in node %s", node);
        if (node instanceof IASTFunctionDefinition) {
            var func = (IASTFunctionDefinition) node;
            assert (domM.contains(func));
            assert (domP.contains(func.getBody()));
            relMPentry.add(func, func.getBody());
            Messages.log("CParser: add relation MPentry(%s,%s).", func, func.getBody());
        }
        for (IASTNode child : children) {
            collectRelations(child, index + 1);
        }
    }
}
