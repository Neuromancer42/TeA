package com.neuromancer42.tea.absdomain.tests;

import com.neuromancer42.tea.commons.analyses.AnalysisUtil;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.absdomain.memmodel.CMemoryModel;
import com.neuromancer42.tea.commons.configs.Constants;
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

    private static void setRel(String dir, Map<String, String> relLocMap, ProgramRel rel, Object[] ... tuples) {
        rel.init();
        for (Object[] tuple : tuples) {
            rel.add(tuple);
        }
        rel.save(dir);
        rel.close();
        relLocMap.put(rel.getName(), rel.getLocation());
    }

    private static void setDom(String dir, Map<String, String> domLocMap, ProgramDom dom, String ... elems) {
        dom.init();
        for (String elem : elems)
            dom.add(elem);
        dom.save(dir);
        domLocMap.put(dom.getName(), dom.getLocation());
    }
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
        setDom(dir, domLocMap, domV, fooName);

        ProgramDom domM = new ProgramDom("M");
        setDom(dir, domLocMap, domM, foo);

        ProgramDom domP = new ProgramDom("P");
        setDom(dir, domLocMap, domP);

        ProgramDom domT = new ProgramDom("T");
        setDom(dir, domLocMap, domT);

        ProgramDom domF = new ProgramDom("F");
        setDom(dir, domLocMap, domF);

        ProgramDom domA = new ProgramDom("A");
        setDom(dir, domLocMap, domA);

        ProgramDom domC = new ProgramDom("C");
        setDom(dir, domLocMap, domC, Constants.NULL);

        ProgramDom domI = new ProgramDom("I");
        setDom(dir, domLocMap, domI);

        ProgramDom domZ = new ProgramDom("Z");
        setDom(dir, domLocMap, domZ);

        Map<String, String> relLocMap = new LinkedHashMap<>();
        ProgramRel relFuncRef = new ProgramRel("funcRef", domM, domV);
        setRel(dir, relLocMap, relFuncRef, new Object[]{foo, fooName});

        ProgramRel relGlobalAlloca = new ProgramRel("GlobalAlloca", domV, domA, domT);
        setRel(dir, relLocMap, relGlobalAlloca);

        ProgramRel relAlloca = new ProgramRel("Alloca", domV, domA, domT);
        setRel(dir, relLocMap, relAlloca);

        ProgramRel relMP = new ProgramRel("MP", domM, domP);
        setRel(dir, relLocMap, relMP);

        ProgramRel relPalloca = new ProgramRel("Palloca", domP, domV);
        setRel(dir, relLocMap, relPalloca);

        ProgramRel relStructFldType = new ProgramRel("StructFldType", domT, domF, domT);
        setRel(dir, relLocMap, relStructFldType);

        ProgramRel relArrContentType = new ProgramRel("ArrContentType", domT, domT, domC);
        setRel(dir, relLocMap, relArrContentType);

        ProgramRel relMI = new ProgramRel("MI", domM, domI);
        setRel(dir, relLocMap, relMI);

        ProgramRel relStaticCall = new ProgramRel("StaticCall", domI, domM);
        setRel(dir, relLocMap, relStaticCall);

        ProgramRel relIinvkRet = new ProgramRel("IinvkRet", domI, domV);
        setRel(dir, relLocMap, relIinvkRet);

        ProgramRel relIinvkArg = new ProgramRel("IinvkArg", domI, domZ, domV);
        setRel(dir, relLocMap, relIinvkArg);

        CMemoryModel cmem = new CMemoryModel(path);
        Pair<Map<String, String>, Map<String, String>> output = AnalysisUtil.runAnalysis(cmem, domLocMap, relLocMap);

        Assertions.assertNotNull(output);
        ProgramDom domH = new ProgramDom("H");
        assert output.getLeft().containsKey("H");
        domH.load(output.getLeft().get("H"));
        Assertions.assertEquals(2, domH.size());
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
        setDom(dir, domLocMap, domV, aPtr);

        ProgramDom domM = new ProgramDom("M");
        setDom(dir, domLocMap, domM);

        ProgramDom domP = new ProgramDom("P");
        setDom(dir, domLocMap, domP);

        ProgramDom domT = new ProgramDom("T");
        setDom(dir, domLocMap, domT, baseType, fType);

        ProgramDom domF = new ProgramDom("F");
        setDom(dir, domLocMap, domF, field);

        ProgramDom domA = new ProgramDom("A");
        setDom(dir, domLocMap, domA, a);

        ProgramDom domC = new ProgramDom("C");
        setDom(dir, domLocMap, domC, Constants.NULL);

        ProgramDom domI = new ProgramDom("I");
        setDom(dir, domLocMap, domI);

        ProgramDom domZ = new ProgramDom("Z");
        setDom(dir, domLocMap, domZ);

        Map<String, String> relLocMap = new LinkedHashMap<>();
        ProgramRel relFuncRef = new ProgramRel("funcRef", domM, domV);
        setRel(dir, relLocMap, relFuncRef);

        ProgramRel relGlobalAlloca = new ProgramRel("GlobalAlloca", domV, domA, domT);
        setRel(dir, relLocMap, relGlobalAlloca);

        ProgramRel relAlloca = new ProgramRel("Alloca", domV, domA, domT);
        setRel(dir, relLocMap, relAlloca, new Object[]{aPtr, a, baseType});

        ProgramRel relMP = new ProgramRel("MP", domM, domP);
        setRel(dir, relLocMap, relMP);

        ProgramRel relPalloca = new ProgramRel("Palloca", domP, domV);
        setRel(dir, relLocMap, relPalloca);

        ProgramRel relStructFldType = new ProgramRel("StructFldType", domT, domF, domT);
        setRel(dir, relLocMap, relStructFldType, new Object[]{baseType, field, fType});

        ProgramRel relArrContentType = new ProgramRel("ArrContentType", domT, domT, domC);
        setRel(dir, relLocMap, relArrContentType);

        ProgramRel relMI = new ProgramRel("MI", domM, domI);
        setRel(dir, relLocMap, relMI);

        ProgramRel relStaticCall = new ProgramRel("StaticCall", domI, domM);
        setRel(dir, relLocMap, relStaticCall);

        ProgramRel relIinvkRet = new ProgramRel("IinvkRet", domI, domV);
        setRel(dir, relLocMap, relIinvkRet);

        ProgramRel relIinvkArg = new ProgramRel("IinvkArg", domI, domZ, domV);
        setRel(dir, relLocMap, relIinvkArg);

        CMemoryModel cmem = new CMemoryModel(path);
        Pair<Map<String, String>, Map<String, String>> output = AnalysisUtil.runAnalysis(cmem, domLocMap, relLocMap);

        Assertions.assertNotNull(output);
        ProgramDom domH = new ProgramDom("H");
        assert output.getLeft().containsKey("H");
        domH.load(output.getLeft().get("H"));
        Assertions.assertEquals(3, domH.size());
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
        setDom(dir, domLocMap, domV, xPtr);

        ProgramDom domM = new ProgramDom("M");
        setDom(dir, domLocMap, domM, "M", dir);

        ProgramDom domP = new ProgramDom("P");
        setDom(dir, domLocMap, domP);

        ProgramDom domT = new ProgramDom("T");
        setDom(dir, domLocMap, domT, arrType, cType);

        ProgramDom domF = new ProgramDom("F");
        setDom(dir, domLocMap, domF);

        ProgramDom domA = new ProgramDom("A");
        setDom(dir, domLocMap, domA, x);

        ProgramDom domC = new ProgramDom("C");
        setDom(dir, domLocMap, domC, arrSize, Constants.NULL);

        ProgramDom domI = new ProgramDom("I");
        setDom(dir, domLocMap, domI);

        ProgramDom domZ = new ProgramDom("Z");
        setDom(dir, domLocMap, domZ);

        Map<String, String> relLocMap = new LinkedHashMap<>();
        ProgramRel relFuncRef = new ProgramRel("funcRef", domM, domV);
        setRel(dir, relLocMap, relFuncRef);

        ProgramRel relGlobalAlloca = new ProgramRel("GlobalAlloca", domV, domA, domT);
        setRel(dir, relLocMap, relGlobalAlloca);

        ProgramRel relAlloca = new ProgramRel("Alloca", domV, domA, domT);
        setRel(dir, relLocMap, relAlloca, new Object[]{xPtr, x, arrType});

        ProgramRel relMP = new ProgramRel("MP", domM, domP);
        setRel(dir, relLocMap, relMP);

        ProgramRel relPalloca = new ProgramRel("Palloca", domP, domV);
        setRel(dir, relLocMap, relPalloca);

        ProgramRel relStructFldType = new ProgramRel("StructFldType", domT, domF, domT);
        setRel(dir, relLocMap, relStructFldType);

        ProgramRel relArrContentType = new ProgramRel("ArrContentType", domT, domT, domC);
        setRel(dir, relLocMap, relArrContentType, new Object[]{arrType, cType, arrSize});

        ProgramRel relMI = new ProgramRel("MI", domM, domI);
        setRel(dir, relLocMap, relMI);

        ProgramRel relStaticCall = new ProgramRel("StaticCall", domI, domM);
        setRel(dir, relLocMap, relStaticCall);

        ProgramRel relIinvkRet = new ProgramRel("IinvkRet", domI, domV);
        setRel(dir, relLocMap, relIinvkRet);

        ProgramRel relIinvkArg = new ProgramRel("IinvkArg", domI, domZ, domV);
        setRel(dir, relLocMap, relIinvkArg);

        CMemoryModel cmem = new CMemoryModel(path);
        Pair<Map<String, String>, Map<String, String>> output = AnalysisUtil.runAnalysis(cmem, domLocMap, relLocMap);

        Assertions.assertNotNull(output);
        ProgramDom domH = new ProgramDom("H");
        assert output.getLeft().containsKey("H");
        domH.load(output.getLeft().get("H"));
        Assertions.assertTrue(2 <= domH.size());
    }
}
