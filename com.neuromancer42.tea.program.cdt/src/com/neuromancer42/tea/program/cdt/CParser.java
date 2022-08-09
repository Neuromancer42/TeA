package com.neuromancer42.tea.program.cdt;

import com.neuromancer42.tea.core.analyses.*;
import com.neuromancer42.tea.core.bddbddb.Dom;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.Trgt;
import org.eclipse.cdt.core.dom.ast.IASTFunctionDefinition;
import org.eclipse.cdt.core.dom.ast.IASTNode;
import org.eclipse.cdt.core.dom.ast.IASTStatement;
import org.eclipse.cdt.core.dom.ast.IASTTranslationUnit;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.core.runtime.CoreException;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class CParser extends JavaAnalysis {

    private final Trgt<ProgramDom<IASTFunctionDefinition>> domM;
    private final Trgt<ProgramDom<IASTStatement>> domP;
//    private DomIU domIU;
//    private DomITV domITV;
//    private DomOP domOP;
//    private DomITVP domITVP;

    private final Trgt<ProgramRel> relMPentry;

    public CParser() {
        name = "CParser";
        domM = AnalysesUtil.createInitializedDomTrgt("M", IASTFunctionDefinition.class, name);
        registerProducer(domM);
        domP = AnalysesUtil.createInitializedDomTrgt("P", IASTStatement.class, name);
        registerProducer(domP);
        relMPentry = AnalysesUtil.createInitializedRelTrgt("MPentry", new ProgramDom<?>[]{domM.g(), domP.g()}, name);
        registerProducer(relMPentry);
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
        domM.g().init();
        domP.g().init();
    }
    private void saveDomains() {
        domM.g().save();
        domP.g().save();
    }

    private void openRelations() {
        relMPentry.g().init();
    }
    private void saveRelations() {
        relMPentry.g().save();
        relMPentry.g().close();
    }

    private void collectDomains(IASTNode node, int index) {
        IASTNode[] children = node.getChildren();
        //Messages.log("CParser: collecting domains in node %s", node);
        if (node instanceof IASTFunctionDefinition) {
            var func = (IASTFunctionDefinition) node;
            Messages.log("CParser: found function <%s>, entry point: %s", func.getRawSignature(), func.getBody().toString());
            domM.g().add(func);
            domP.g().add(func.getBody());
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
            assert (domM.g().contains(func));
            assert (domP.g().contains(func.getBody()));
            relMPentry.g().add(func, func.getBody());
            Messages.log("CParser: add relation MPentry(%s,%s).", func, func.getBody());
        }
        for (IASTNode child : children) {
            collectRelations(child, index + 1);
        }
    }
}
