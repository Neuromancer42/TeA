package com.neuromancer42.tea.program.cdt.tests;

import com.neuromancer42.tea.program.cdt.parser.CParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.HashMap;

public class CParserTest {
    @Test
    @DisplayName("CParser generate rels and doms correctly")
    public void test() {
        URL fileURL = this.getClass().getResource("/resources/aplusb.c");
        assert fileURL != null;
        String filename = System.getProperty("sourcefile0", fileURL.toString());
        String[] includes = System.getProperty("includepath0", "").split(";");
        CParser cParser = new CParser();
        cParser.run(filename, new HashMap<>(), includes);
        Assertions.assertNotEquals(cParser.domM.size(), 0);
    }
}
