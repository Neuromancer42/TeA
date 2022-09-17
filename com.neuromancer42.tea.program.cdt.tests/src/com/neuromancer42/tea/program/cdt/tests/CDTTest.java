package com.neuromancer42.tea.program.cdt.tests;

import com.neuromancer42.tea.core.project.Messages;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.gnu.cpp.GPPLanguage;
import org.eclipse.cdt.core.model.ITranslationUnit;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTFunctionCallExpression;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTIdExpression;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTName;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTTranslationUnit;
import org.eclipse.cdt.internal.core.dom.rewrite.astwriter.ASTWriter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.net.URL;

public class CDTTest {
    @Test
    @DisplayName("CDT core process C header correctly")
    public void test() {
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
            translationUnit = GPPLanguage.getDefault().getASTTranslationUnit(fileContent, info, emptyIncludes, null, opts, log);
        } catch (Exception e) {
            Assertions.fail(e);
        }
        IASTPreprocessorIncludeStatement[] includes = translationUnit.getIncludeDirectives();
        Assertions.assertNotNull(includes);
        for (IASTPreprocessorIncludeStatement include : includes) {
            System.out.println("include - " + include.getName());
        }

        translationUnit = translationUnit.copy();

        List<IASTBinaryExpression> binExprs = new ArrayList<>();
        printTree(translationUnit, 1, binExprs);
        for (var binExpr: binExprs) {
            instrPeek(binExpr);
        }
        printTree(translationUnit, 1, binExprs);
        //dumpToSource(translationUnit, "example_new.h");
    }

    private void dumpToSource(IASTTranslationUnit translationUnit, String filename) {
        // TODO
        throw new UnsupportedOperationException();
    }

    private void instrPeek(IASTBinaryExpression binExpr) {
        IASTExpression expr = binExpr.getOperand2();
        IASTFunctionCallExpression callExpr = new CPPASTFunctionCallExpression();
        callExpr.setParent(binExpr);
        IASTInitializerClause[] args = new IASTInitializerClause[]{expr.copy()};
        callExpr.setArguments(args);
        char[] name = new char[]{'p', 'e', 'e', 'k'};
        IASTName funcName = new CPPASTName(name);
        IASTExpression funcNameExpr = new CPPASTIdExpression(funcName);
        callExpr.setFunctionNameExpression(funcNameExpr);
        binExpr.setOperand2(callExpr);
        //throw new UnsupportedOperationException();
    }

    private static void printTree(IASTNode node, int index, List<IASTBinaryExpression> binExprs) {
        IASTNode[] children = node.getChildren();

        boolean printContents = !(node instanceof CPPASTTranslationUnit);

        if (node instanceof IASTBinaryExpression) {
            binExprs.add((IASTBinaryExpression) node);
        }
        String offset = "";
        try {
            offset = node.getSyntax() != null ? " (offset: " + node.getFileLocation().getNodeOffset() + "," + node.getFileLocation().getNodeLength() + ")" : "";
            printContents = node.getFileLocation().getNodeLength() < 30;
        } catch (ExpansionOverlapsBoundaryException e) {
            Assertions.fail(e);
        } catch (UnsupportedOperationException e) {
            offset = " UnsupportedOperationException";
        }

        if(!node.getRawSignature().isEmpty()) System.out.println(String.format("%1$" + index * 2 + "s", "-") + node.getClass().getSimpleName() + offset + " -> " + (printContents ? node.getRawSignature().replaceAll("\n", " \\ ") : node.getRawSignature().subSequence(0, 5)));
        else{
            if(node instanceof CPPASTFunctionCallExpression)
                System.out.println(String.format("%1$" + index * 2 + "s", "-") + node.getClass().getSimpleName() + " (new node) -> "
                        + ((CPPASTIdExpression)((CPPASTFunctionCallExpression) node).getFunctionNameExpression()).getName().toString()
                        + "(args)");
            if(node instanceof CPPASTIdExpression)
                System.out.println(String.format("%1$" + index * 2 + "s", "-") + node.getClass().getSimpleName() + " (new node) -> "
                        + ((CPPASTIdExpression) node).getName().toString());
            if(node instanceof CPPASTName)
                System.out.println(String.format("%1$" + index * 2 + "s", "-") + node.getClass().getSimpleName() + " (new node) -> "
                        + new String(((CPPASTName) node).toCharArray()));
        }
        for (IASTNode iastNode : children)
            printTree(iastNode, index + 1, binExprs);
    }
}
