package com.neuromancer42.tea.absdomain.tests;

import com.neuromancer42.tea.commons.analyses.AnalysisUtil;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.absdomain.memmodel.CMemoryModel;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public class CMemModelTest {
    private static Path workDirPath = Paths.get("test-out");

    @Test
    @DisplayName("CMemModel processes func-ptrs coccretly")
    public void funcPtrTest() throws IOException {
        Path path = workDirPath.resolve("test-struct");
        Files.createDirectories(path);
        String dir = path.toAbsolutePath().toString();

        Map<String, String> domLocMap = new LinkedHashMap<>();

        String foo = "int foo()";
        String fooName = "@foo";

        ProgramDom domV = new ProgramDom("V");
        domV.init();
        domV.add(fooName);
        domV.save(dir);
        domLocMap.put("V", dir);

        ProgramDom domM = new ProgramDom("M");
        domM.init();
        domM.add(foo);
        domM.save(dir);
        domLocMap.put("M", dir);

        ProgramDom domT = new ProgramDom("T");
        domT.init();
        domT.save(dir);
        domLocMap.put("T", dir);

        ProgramDom domF = new ProgramDom("F");
        domF.init();
        domF.save(dir);
        domLocMap.put("F", dir);

        ProgramDom domA = new ProgramDom("A");
        domA.init();
        domA.save(dir);
        domLocMap.put("A", dir);

        ProgramDom domC = new ProgramDom("C");
        domC.init();
        domC.save(dir);
        domLocMap.put("C", dir);

        Map<String, String> relLocMap = new LinkedHashMap<>();
        ProgramRel relFuncRef = new ProgramRel("funcRef", domM, domV);
        relFuncRef.init();
        relFuncRef.add(foo, fooName);
        relFuncRef.save(dir);
        relFuncRef.close();
        relLocMap.put("funcRef", dir);

        ProgramRel relGlobalAlloca = new ProgramRel("GlobalAlloca", domV, domA, domT);
        relGlobalAlloca.init();
        relGlobalAlloca.save(dir);
        relGlobalAlloca.close();
        relLocMap.put("GlobalAlloca", dir);

        ProgramRel relAlloca = new ProgramRel("Alloca", domV, domA, domT);
        relAlloca.init();
        relAlloca.save(dir);
        relAlloca.close();
        relLocMap.put("Alloca", dir);

        ProgramRel relStructFldType = new ProgramRel("StructFldType", domT, domF, domT);
        relStructFldType.init();
        relStructFldType.save(dir);
        relStructFldType.close();
        relLocMap.put("StructFldType", dir);

        ProgramRel relArrContentType = new ProgramRel("ArrContentType", domT, domT, domC);
        relArrContentType.init();
        relArrContentType.save(dir);
        relArrContentType.close();
        relLocMap.put("ArrContentType", dir);

        CMemoryModel cmem = new CMemoryModel(path);
        Pair<Map<String, String>, Map<String, String>> output = AnalysisUtil.runAnalysis(cmem, domLocMap, relLocMap);

        Assertions.assertNotNull(output);
        ProgramDom domH = new ProgramDom("H");
        assert output.getLeft().containsKey("H");
        domH.load(output.getLeft().get("H"));
        Assertions.assertEquals(1, domH.size());
    }

    @Test
    @DisplayName("CMemModel processes struct types coccretly")
    public void structTypeTest() throws IOException {
        Path path = workDirPath.resolve("test-struct");
        Files.createDirectories(path);
        String dir = path.toAbsolutePath().toString();

        Map<String, String> domLocMap = new LinkedHashMap<>();

        String a = "a";
        String aPtr = "%a.addr";
        String baseType = "struct A { int a; }";
        String field = "a";
        String fType = "int";

        ProgramDom domV = new ProgramDom("V");
        domV.init();
        domV.add(aPtr);
        domV.save(dir);
        domLocMap.put("V", dir);

        ProgramDom domM = new ProgramDom("M");
        domM.init();
        domM.save(dir);
        domLocMap.put("M", dir);

        ProgramDom domT = new ProgramDom("T");
        domT.init();
        domT.add(baseType);
        domT.add(fType);
        domT.save(dir);
        domLocMap.put("T", dir);

        ProgramDom domF = new ProgramDom("F");
        domF.init();
        domF.add(field);
        domF.save(dir);
        domLocMap.put("F", dir);

        ProgramDom domA = new ProgramDom("A");
        domA.init();
        domA.add(a);
        domA.save(dir);
        domLocMap.put("A", dir);

        ProgramDom domC = new ProgramDom("C");
        domC.init();
        domC.save(dir);
        domLocMap.put("C", dir);

        Map<String, String> relLocMap = new LinkedHashMap<>();
        ProgramRel relFuncRef = new ProgramRel("funcRef", domM, domV);
        relFuncRef.init();
        relFuncRef.save(dir);
        relFuncRef.close();
        relLocMap.put("funcRef", dir);

        ProgramRel relGlobalAlloca = new ProgramRel("GlobalAlloca", domV, domA, domT);
        relGlobalAlloca.init();
        relGlobalAlloca.save(dir);
        relGlobalAlloca.close();
        relLocMap.put("GlobalAlloca", dir);

        ProgramRel relAlloca = new ProgramRel("Alloca", domV, domA, domT);
        relAlloca.init();;
        relAlloca.add(aPtr, a, baseType);
        relAlloca.save(dir);
        relAlloca.close();
        relLocMap.put("Alloca", dir);

        ProgramRel relStructFldType = new ProgramRel("StructFldType", domT, domF, domT);
        relStructFldType.init();
        relStructFldType.add(baseType, field, fType);
        relStructFldType.save(dir);
        relStructFldType.close();
        relLocMap.put("StructFldType", dir);

        ProgramRel relArrContentType = new ProgramRel("ArrContentType", domT, domT, domC);
        relArrContentType.init();
        relArrContentType.save(dir);
        relArrContentType.close();
        relLocMap.put("ArrContentType", dir);

        CMemoryModel cmem = new CMemoryModel(path);
        Pair<Map<String, String>, Map<String, String>> output = AnalysisUtil.runAnalysis(cmem, domLocMap, relLocMap);

        Assertions.assertNotNull(output);
        ProgramDom domH = new ProgramDom("H");
        assert output.getLeft().containsKey("H");
        domH.load(output.getLeft().get("H"));
        Assertions.assertEquals(2, domH.size());
    }

    @Test
    @DisplayName("CMemModel processes array types coccretly")
    public void arrayTypeTest() throws IOException {
        Path path = workDirPath.resolve("test-struct");
        Files.createDirectories(path);
        String dir = path.toAbsolutePath().toString();

        Map<String, String> domLocMap = new LinkedHashMap<>();

        String x = "x";
        String xPtr = "%x.addr";
        String arrType = "int[10]";
        String cType = "int";
        String arrSize = "10";

        ProgramDom domV = new ProgramDom("V");
        domV.init();
        domV.add(xPtr);
        domV.save(dir);
        domLocMap.put("V", dir);

        ProgramDom domM = new ProgramDom("M");
        domM.init();
        domM.save(dir);
        domLocMap.put("M", dir);

        ProgramDom domT = new ProgramDom("T");
        domT.init();
        domT.add(arrType);
        domT.add(cType);
        domT.save(dir);
        domLocMap.put("T", dir);

        ProgramDom domF = new ProgramDom("F");
        domF.init();
        domF.save(dir);
        domLocMap.put("F", dir);

        ProgramDom domA = new ProgramDom("A");
        domA.init();
        domA.add(x);
        domA.save(dir);
        domLocMap.put("A", dir);

        ProgramDom domC = new ProgramDom("C");
        domC.init();
        domC.add(arrSize);
        domC.save(dir);
        domLocMap.put("C", dir);

        Map<String, String> relLocMap = new LinkedHashMap<>();
        ProgramRel relFuncRef = new ProgramRel("funcRef", domM, domV);
        relFuncRef.init();
        relFuncRef.save(dir);
        relFuncRef.close();
        relLocMap.put("funcRef", dir);

        ProgramRel relGlobalAlloca = new ProgramRel("GlobalAlloca", domV, domA, domT);
        relGlobalAlloca.init();
        relGlobalAlloca.save(dir);
        relGlobalAlloca.close();
        relLocMap.put("GlobalAlloca", dir);

        ProgramRel relAlloca = new ProgramRel("Alloca", domV, domA, domT);
        relAlloca.init();;
        relAlloca.add(xPtr, x, arrType);
        relAlloca.save(dir);
        relAlloca.close();
        relLocMap.put("Alloca", dir);

        ProgramRel relStructFldType = new ProgramRel("StructFldType", domT, domF, domT);
        relStructFldType.init();
        relStructFldType.save(dir);
        relStructFldType.close();
        relLocMap.put("StructFldType", dir);

        ProgramRel relArrContentType = new ProgramRel("ArrContentType", domT, domT, domC);
        relArrContentType.init();
        relArrContentType.add(arrType, cType, arrSize);
        relArrContentType.save(dir);
        relArrContentType.close();
        relLocMap.put("ArrContentType", dir);

        CMemoryModel cmem = new CMemoryModel(path);
        Pair<Map<String, String>, Map<String, String>> output = AnalysisUtil.runAnalysis(cmem, domLocMap, relLocMap);

        Assertions.assertNotNull(output);
        ProgramDom domH = new ProgramDom("H");
        assert output.getLeft().containsKey("H");
        domH.load(output.getLeft().get("H"));
        Assertions.assertTrue(2 <= domH.size());
    }
}
