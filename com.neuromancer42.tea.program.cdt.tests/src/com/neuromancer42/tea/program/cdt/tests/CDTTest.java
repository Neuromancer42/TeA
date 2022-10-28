package com.neuromancer42.tea.program.cdt.tests;

import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.program.cdt.parser.CParser;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.core.dom.ast.gnu.c.GCCLanguage;
import org.eclipse.cdt.core.parser.*;
import org.eclipse.cdt.internal.core.dom.parser.c.*;
import org.eclipse.cdt.internal.core.dom.rewrite.astwriter.ASTWriter;
import org.eclipse.core.internal.resources.Workspace;
import org.eclipse.core.runtime.CoreException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
        //dumpToSource(translationUnit, "example_new.h");
    }

}
