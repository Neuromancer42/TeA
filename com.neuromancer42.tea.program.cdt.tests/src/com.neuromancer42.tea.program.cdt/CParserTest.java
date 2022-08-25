package com.neuromancer42.tea.program.cdt;

import com.neuromancer42.tea.program.cdt.internal.CParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;

public class CParserTest {
    @Test
    @DisplayName("CParser generate rels and doms correctly")
    public void test() {
        URL fileURL = this.getClass().getResource("/resources/aplusb.c");
        String filename = System.getProperty("sourcefile", fileURL.toString());
        CParser cParser = new CParser(filename);
        cParser.run();
        Assertions.assertNotEquals(cParser.domM.size(), 0);
    }
}
