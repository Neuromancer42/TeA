package com.neuromancer42.tea.program.cdt.tests;

import com.neuromancer42.tea.core.project.Messages;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.gnu.cpp.GPPLanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.cdt.internal.core.dom.parser.cpp.CPPASTTranslationUnit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        List<IASTBinaryExpression> binExprs = new ArrayList<>();
        printTree(translationUnit, 1, binExprs);
//        for (var binExpr: binExprs) {
//            instrPeek(binExpr.getOperand2());
//        }
//        dumpToSource(translationUnit, "example_new.h");
    }

    private void dumpToSource(IASTTranslationUnit translationUnit, String filename) {
        // TODO
        throw new UnsupportedOperationException();
    }

    private void instrPeek(IASTExpression expr) {
        // TODO
        throw new UnsupportedOperationException();
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
            offset = "UnsupportedOperationException";
        }

        System.out.println(String.format("%1$" + index * 2 + "s", "-") + node.getClass().getSimpleName() + offset + " -> " + (printContents ? node.getRawSignature().replaceAll("\n", " \\ ") : node.getRawSignature().subSequence(0, 5)));

        for (IASTNode iastNode : children)
            printTree(iastNode, index + 1, binExprs);
    }
}
