package com.neuromancer42.tea.program.cdt.tests;

import com.neuromancer42.tea.program.cdt.parser.CParser;
import com.neuromancer42.tea.program.cdt.parser.evaluation.IEval;
import org.eclipse.cdt.core.dom.ast.IFunction;
import org.eclipse.cdt.internal.core.dom.rewrite.astwriter.ASTWriter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;

public class CInstrumentTest {
    @Test
    @DisplayName("CInstrument generate instrumented c source file correctly")
    public void test() throws IOException {
        URL fileURL = this.getClass().getResource("/resources/aplusb.c");
        assert fileURL != null;
        String filename = System.getProperty("sourcefile2", fileURL.toString());
        String[] includes = System.getProperty("includepath2", "").split(";");
        CParser cParser = new CParser();
        cParser.run(filename, new HashMap<>(), includes);
        Assertions.assertNotEquals(cParser.domM.size(), 0);
        CParser.CInstrument cInstr = cParser.getInstrument();
        for (int i = 0; i < cParser.domI.size(); i++) {
            IEval invk = cParser.domI.get(i);
            cInstr.instrumentBeforeInvoke(invk);
        }
        for (int i = 0; i < cParser.domM.size(); i++) {
            IFunction meth = cParser.domM.get(i);
            if (meth.isExtern()) continue;
            Assertions.assertNotEquals(-1, cInstr.instrumentEnterMethod(meth));
        }

        Path newFilePath = Path.of(filename + ".instrumented");
        Files.write(newFilePath, List.of(new ASTWriter().write(cInstr.instrumented())), StandardCharsets.UTF_8);
        System.out.println("Dump insturmneted c source file to " + newFilePath);
    }
}
