package com.neuromancer42.tea.program.cdt.tests;

import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.program.cdt.parser.CInstrument;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.cdt.internal.core.dom.parser.c.*;
import org.eclipse.cdt.internal.core.dom.rewrite.astwriter.ASTWriter;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Paths;
import java.util.*;
import java.net.URL;

public class CDTTest {
    @Test
    @DisplayName("CDT core process C header correctly")
    public void test() throws CoreException {
        Workspace workspace = new Workspace();
//        IStatus status = workspace.open(null);
//        Messages.debug(status.getMessage());
//        IProjectDescription projDescr = workspace.newProjectDescription("CDTtest");
//        projDescr.setLocation(Path.fromOSString(Paths.get(".").resolve("test_proj").toAbsolutePath().toString()));
//        Messages.debug(projDescr.getLocationURI().toString());
//        IProject proj = workspace.getRoot().getProject("test_proj");
        URL fileURL = this.getClass().getResource("/resources/example.h");
        assert fileURL != null;
        String filename = System.getProperty("headerfile", fileURL.toString());
        Messages.log("Opening " + filename);
        FileContent fileContent = FileContent.createForExternalFileLocation(filename);
        Map<String, String> definedSymbols = new HashMap<>();
        String[] includePaths = new String[0];
        IScannerInfo info = new ScannerInfo(definedSymbols, includePaths);
        IParserLogService log = new DefaultLogService();
        IncludeFileContentProvider emptyIncludes = IncludeFileContentProvider.getEmptyFilesProvider();
        int opts = 8;
        IASTTranslationUnit translationUnit = null;
        try {
            translationUnit = GCCLanguage.getDefault().getASTTranslationUnit(fileContent, info, emptyIncludes, null, opts, log);
        } catch (Exception e) {
            Assertions.fail(e);
        }
        IASTPreprocessorIncludeStatement[] includes = translationUnit.getIncludeDirectives();
        Assertions.assertNotNull(includes);
        for (IASTPreprocessorIncludeStatement include : includes) {
            System.out.println("include - " + include.getName());
        }

        CASTTranslationUnit newTU = (CASTTranslationUnit) translationUnit.copy();
        CInstrument instr = new CInstrument(newTU);
        visitTree(newTU, 0, instr, null);
        CASTTranslationUnit instrTU = instr.instrumented();
        System.out.println(new ASTWriter().write(instrTU));
        //dumpToSource(translationUnit, "example_new.h");
    }

    private static IVariable visitTree(IASTNode node, int index, CInstrument instr, IVariable prevDeclVar) {
        IASTNode[] children = node.getChildren();

        if (node instanceof IASTBinaryExpression ) {
            if (prevDeclVar != null) {
                Messages.log("Peek var %s in rhs of %s", prevDeclVar, (new ASTWriter()).write(node));
                instr.instrumentPeekVar(prevDeclVar, ((IASTBinaryExpression) node).getOperand2());
            }
        }
        if (node instanceof CASTEqualsInitializer) {
            if (prevDeclVar != null) {
                Messages.log("Peek var %s in expr of %s", prevDeclVar, (new ASTWriter()).write(node));
                IASTExpression origExpr = (IASTExpression) ((CASTEqualsInitializer) node).getInitializerClause();
                instr.instrumentPeekVar(prevDeclVar, origExpr);
            }
        }

        if(node instanceof CASTFunctionCallExpression)
            System.out.println(String.format("%1$" + index * 2 + "s", "-") + node.getClass().getSimpleName() + " (new node) -> "
                    + ((CASTIdExpression)((CASTFunctionCallExpression) node).getFunctionNameExpression()).getName().toString()
                    + "(args)");
        if(node instanceof CASTIdExpression)
            System.out.println(String.format("%1$" + index * 2 + "s", "-") + node.getClass().getSimpleName() + " (new node) -> "
                    + ((CASTIdExpression) node).getName().toString());
        if(node instanceof CASTName)
            System.out.println(String.format("%1$" + index * 2 + "s", "-") + node.getClass().getSimpleName() + " (new node) -> "
                    + new String(((CASTName) node).toCharArray()));

        for (IASTNode iastNode : children)
            prevDeclVar = visitTree(iastNode, index + 1, instr, prevDeclVar);

        if (node instanceof CASTDeclarator) {
            Messages.log("Declaration: %s", (new ASTWriter()).write(node));
            IBinding b = ((CASTDeclarator) node).getName().resolveBinding();
            if (b instanceof IVariable)
                prevDeclVar = (IVariable) b;
        }

        return prevDeclVar;
    }
}
