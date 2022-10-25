package com.neuromancer42.tea.program.cdt.tests;

import com.neuromancer42.tea.program.cdt.parser.CInstrument;
import com.neuromancer42.tea.program.cdt.parser.CParser;
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
        CInstrument cInstr = new CInstrument(cParser.copyTranslationUnit());
        for (int i = 0; i < cParser.domI.size(); i++) {
            cInstr.instrumentBeforeInvoke(cParser.domI.get(i));
        }
        for (int i = 0; i < cParser.domM.size(); i++) {
            Assertions.assertNotEquals(-1, cInstr.instrumentEnterMethod(cParser.domM.get(i)));
        }

        Path newFilePath = Path.of(filename + ".instrumented");
        Files.write(newFilePath, List.of(new ASTWriter().write(cInstr.instrumented())), StandardCharsets.UTF_8);
        System.out.println("Dump insturmneted c source file to " + newFilePath);
    }
}
