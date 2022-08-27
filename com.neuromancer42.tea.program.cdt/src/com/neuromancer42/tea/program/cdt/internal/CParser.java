package com.neuromancer42.tea.program.cdt.internal;

import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.program.cdt.internal.cfg.IntraCFG;
import com.neuromancer42.tea.program.cdt.internal.cfg.IntraCFGBuilder;
import org.eclipse.cdt.codan.core.model.cfg.IBasicBlock;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.core.runtime.CoreException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class CParser {

    public final ProgramDom<IASTFunctionDefinition> domM;
    public final ProgramDom<IASTStatement> domP;
    // TODO: change domP's domain type from IASTStatement to abstract SequencePoints
    public final ProgramDom<IBasicBlock> domS;
    public final ProgramDom<IASTDeclarator> domV;
    public final ProgramDom<IASTExpression> domE;

    public final ProgramRel relMPentry;
    public final ProgramRel relMPexit;
    public final ProgramRel relPPdirect;
    public final ProgramRel relPPtrue;
    public final ProgramRel relPPfalse;
    public final ProgramRel relMV;
    public final ProgramRel relGlobalV;
    public final ProgramRel relPret;
    public final ProgramRel relPskip;
    public final ProgramRel relPexpr;
    public final ProgramRel relPVinit;
    private final String fileName;

    public CParser(String fileName) {
        this.fileName = fileName;
        domM = ProgramDom.createDom("M", IASTFunctionDefinition.class);
        domP = ProgramDom.createDom("P", IASTStatement.class);
        domV = ProgramDom.createDom("V", IASTDeclarator.class);
        domE = ProgramDom.createDom("E", IASTExpression.class);
        domS = ProgramDom.createDom("S", IBasicBlock.class);
        // control flow relations
        relMPentry = ProgramRel.createRel("MPentry", new ProgramDom[]{domM, domP});
        relMPexit = ProgramRel.createRel("MPexit", new ProgramDom[]{domM, domP});
        relPPdirect = ProgramRel.createRel("PPdirect", new ProgramDom[]{domP, domP});
        relPPtrue = ProgramRel.createRel("PPtrue", new ProgramDom[]{domP, domP, domE});
        relPPfalse = ProgramRel.createRel("PPfalse", new ProgramDom[]{domP, domP, domE});
        // variable records
        relMV = ProgramRel.createRel("MV", new ProgramDom[]{domM, domV});
        relGlobalV = ProgramRel.createRel("GlobalV", new ProgramDom[]{domV});
        // statements
        relPret = ProgramRel.createRel("Pret", new ProgramDom[]{domP, domE});
        relPskip = ProgramRel.createRel("Pskip", new ProgramDom[]{domP});
        relPexpr = ProgramRel.createRel("Pexpr", new ProgramDom[]{domP, domE});
        relPVinit = ProgramRel.createRel("PVinit", new ProgramDom[]{domP, domV, domE});
    }


    public void run() {
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
            assert false;
        }

        IntraCFGBuilder builder = new IntraCFGBuilder(translationUnit);
        Map<IASTFunctionDefinition, IntraCFG> methCFGMap = new HashMap<>();
        for (var decl: translationUnit.getDeclarations()) {
            if (decl instanceof IASTFunctionDefinition) {
                var fDef = (IASTFunctionDefinition) decl;
                var cfg = builder.build(fDef);
                methCFGMap.put(fDef, cfg);
            }
        }

        String dotName = sourceFile.getName() + ".dot";
        try {
            BufferedWriter bw = Files.newBufferedWriter(Path.of(dotName), StandardCharsets.UTF_8);
            PrintWriter pw = new PrintWriter(bw);
            pw.println("digraph \"" + sourceFile.getName() + "\" {");
            for (IASTFunctionDefinition meth : methCFGMap.keySet()) {
                IntraCFG cfg = methCFGMap.get(meth);
                cfg.dumpDotString(pw);
            }
            pw.println("}");
            pw.flush();
        } catch (IOException e) {
            Messages.error("CParser: failed to dump DOT file %s", dotName);
            Messages.fatal(e);
        }
        // TODO: move this into function template
        openDomains();
        for (var meth : methCFGMap.keySet()) {
            domM.add(meth);
        }
        saveDomains();

        openRelations();
        saveRelations();
    }

    private void openDomains() {
        domM.init();
        domP.init();
        domV.init();
        domE.init();
        domS.init();
    }
    private void saveDomains() {
        domM.save();
        domP.save();
        domV.save();
        domE.save();
        domS.save();
    }

    private void openRelations() {
        relMPentry.init();
        relMPexit.init();
        relPPdirect.init();
        relPPtrue.init();
        relPPfalse.init();
        relMV.init();
        relGlobalV.init();
        relPret.init();
        relPskip.init();
        relPexpr.init();
    }
    private void saveRelations() {
        relMPentry.save();
        relMPentry.close();
        relMPexit.save();
        relMPexit.close();
        relPPdirect.save();
        relPPdirect.close();
        relPPtrue.save();
        relPPtrue.close();
        relPPfalse.save();
        relPPfalse.close();
        relMV.save();
        relMV.close();
        relGlobalV.save();
        relGlobalV.close();
        relPret.save();
        relPret.close();
        relPskip.save();
        relPskip.close();
        relPexpr.save();
        relPexpr.close();
    }

}
