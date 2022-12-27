package com.neuromancer42.tea.absdomain.memmodel;

import com.neuromancer42.tea.commons.analyses.AbstractAnalysis;
import com.neuromancer42.tea.commons.analyses.annotations.*;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.absdomain.memmodel.object.DummyPtrObj;
import com.neuromancer42.tea.absdomain.memmodel.object.FuncObj;
import com.neuromancer42.tea.absdomain.memmodel.object.IMemObj;
import com.neuromancer42.tea.absdomain.memmodel.object.StackObj;

import java.nio.file.Path;
import java.util.*;

@TeAAnalysis(name = "c_mem_model")
public class CMemoryModel extends AbstractAnalysis {
    public static final String name = "c_mem_model";
    private final Path workPath;

    @ConsumeDom(description = "registers")
    public ProgramDom domV;
    @ConsumeDom(description = "fields")
    public ProgramDom domF;
    @ConsumeDom(description = "functions")
    public ProgramDom domM;
    @ConsumeDom(description = "allocation instructions")
    public ProgramDom domA;
    @ConsumeDom(description = "types")
    public ProgramDom domT;
    @ConsumeDom(description = "constants")
    public ProgramDom domC;


    @ConsumeRel(name = "funcRef", doms = {"M", "V"}, description = "function name is also a funcptr variable")
    public ProgramRel relFuncRef;
    @ConsumeRel(doms = {"V", "A", "T"})
    public ProgramRel relGlobalAlloca;
    @ConsumeRel(doms = {"V", "A", "T"})
    public ProgramRel relAlloca;
    @ConsumeRel(doms = {"T", "F", "T"})
    public ProgramRel relStructFldType;
    @ConsumeRel(doms = {"T", "T", "C"})
    public ProgramRel relArrContentType;

    @ProduceDom(description = "abstract heap objects")
    public ProgramDom domH;

    @ProduceDom(description = "index and size representation of arrays")
    public ProgramDom domSZ;

    @ProduceRel(doms = {"V", "H"})
    public ProgramRel relGlobalAllocMem;
    @ProduceRel(doms = {"V", "H"})
    public ProgramRel relAllocMem;
    @ProduceRel(doms = {"H", "H"})
    public ProgramRel relHeapAllocPtr;
    @ProduceRel(doms = {"H", "SZ", "H"}) // TODO: mark array length
    public ProgramRel relHeapAllocArr;
    @ProduceRel(doms = {"H", "SZ"})
    public ProgramRel relHeapArraySize;
    @ProduceRel(doms = {"H", "F", "H"})
    public ProgramRel relHeapAllocFld;
    @ProduceRel(name = "funcPtr", doms = {"H", "M"}, description = "mark heap object of a function")
    public ProgramRel relFuncPtr;

    private final Map<String, Map<String, String>> fldTypeMap = new LinkedHashMap<>();
    private final Map<String, String> arrTypeContentMap = new LinkedHashMap<>();
    private Map<String, String> arrTypeSizeMap = new LinkedHashMap<>();
    private final Map<String, IMemObj> vRegObjMap = new LinkedHashMap<>();
    private final Set<IMemObj> stackObjs = new LinkedHashSet<>();
    private final Map<IMemObj, IMemObj> ptrStoreMap = new LinkedHashMap<>();
    private final Map<IMemObj, Map<String, IMemObj>> arrayStoreMap = new LinkedHashMap<>();
    private final Map<IMemObj, String> arraySizeMap = new LinkedHashMap<>();
    private final Map<IMemObj, Map<String, IMemObj>> fieldStoreMap = new LinkedHashMap<>();

    public CMemoryModel(Path path) {
        // TODO: read sensitivity-settings from request
        workPath = path;
    }

    private void init(Map<String, ProgramDom> inputDoms, Map<String, ProgramRel> inputRels) {
        domV = inputDoms.get("V");
        domM = inputDoms.get("M");
        domF = inputDoms.get("F");
        domA = inputDoms.get("A");
        domT = inputDoms.get("T");
        domC = inputDoms.get("C");
        relFuncRef = inputRels.get("funcRef");
        relFuncRef.load();
        relGlobalAlloca = inputRels.get("GlobalAlloca");
        relGlobalAlloca.load();
        relAlloca = inputRels.get("Alloca");
        relAlloca.load();
        relStructFldType = inputRels.get("StructFldType");
        relArrContentType = inputRels.get("ArrContentType");

        domH = new ProgramDom("H");
        domSZ = new ProgramDom("SZ");
        relGlobalAllocMem = new ProgramRel("GlobalAlloc", domV, domH);
        relAllocMem = new ProgramRel("Alloc", domV, domH);
        relHeapAllocPtr = new ProgramRel("HeapAllocPtr", domH, domH);
        relHeapAllocArr = new ProgramRel("HeapAllocArr", domH, domSZ, domH);
        relHeapArraySize = new ProgramRel("HeapArraySize", domH, domSZ);
        relHeapAllocFld = new ProgramRel("HeapAllocFld", domH, domF, domH);
        relFuncPtr = new ProgramRel("funcPtr", domH, domM);
    }

    private void openRels() {
        relGlobalAllocMem.init();
        relAllocMem.init();
        relHeapAllocPtr.init();
        relHeapAllocArr.init();
        relHeapArraySize.init();
        relHeapAllocFld.init();
        relFuncPtr.init();
    }

    private void closeRels() {
        relFuncRef.close();
        relGlobalAlloca.close();
        relAlloca.close();

        relGlobalAllocMem.save(getOutDir());
        relGlobalAllocMem.close();
        relAllocMem.save(getOutDir());
        relAllocMem.close();
        relHeapAllocPtr.save(getOutDir());
        relHeapAllocPtr.close();
        relHeapAllocArr.save(getOutDir());
        relHeapAllocArr.close();
        relHeapArraySize.save(getOutDir());
        relHeapArraySize.close();
        relHeapAllocFld.save(getOutDir());
        relHeapAllocFld.close();
        relFuncPtr.save(getOutDir());
        relFuncPtr.close();
    }


    public void run(Map<String, ProgramDom> inputDoms, Map<String, ProgramRel> inputRels) {
        init(inputDoms, inputRels);
        domH.init();
        domPhase();
        domH.save(getOutDir());
        openRels();
        relPhase();
        closeRels();
    }

    @Override
    protected void domPhase() {
        for (Object[] tuple : relStructFldType.getValTuples()) {
            String baseType = (String) tuple[0];
            String field = (String) tuple[1];
            String fieldType = (String) tuple[2];
            fldTypeMap.computeIfAbsent(baseType, f -> new LinkedHashMap<>()).put(field, fieldType);
        }

        for (Object[] tuple : relArrContentType.getValTuples()) {
            String baseType = (String) tuple[0];
            String fieldType = (String) tuple[1];
            String size = (String) tuple[2];
            arrTypeContentMap.put(baseType, fieldType);
            arrTypeSizeMap.put(baseType, size);
        }

        for (Object[] tuple : relFuncRef.getValTuples()) {
            String m = (String) tuple[0];
            String v = (String) tuple[1];
            IMemObj fObj = createFuncObj(m);
            vRegObjMap.put(v, fObj);
        }
        for (Object[] tuple : relGlobalAlloca.getValTuples()) {
            String v = (String) tuple[0];
            String variable = (String) tuple[1];
            String type = (String) tuple[2];
            IMemObj vObj = createStackObj(variable, type);
            Messages.debug("CMemoryModel: alloc global %s <- {%s}", v, vObj.toString());
            vRegObjMap.put(v, vObj);
        }
        for (Object[] tuple : relAlloca.getValTuples()) {
            String v = (String) tuple[0];
            String variable = (String) tuple[1];
            String type = (String) tuple[2];
            IMemObj vObj = createStackObj(variable, type);
            Messages.debug("CMemoryMode: alloc local %s <- {%s}", v, vObj.toString());
            vRegObjMap.put(v, vObj);
        }

        for (IMemObj obj : vRegObjMap.values()) {
            domH.add(obj.toString());
        }
        for (IMemObj obj : stackObjs) {
            domH.add(obj.toString());
        }
        for (String size : arraySizeMap.values()) {
            domSZ.add(size);
        }
        for (Map<String, IMemObj> posMap: arrayStoreMap.values()) {
            for (String pos : posMap.keySet())
                domSZ.add(pos);
        }
    }

    @Override
    protected void relPhase() {
        for (Object[] tuple : relFuncRef.getValTuples()) {
            String m = (String) tuple[0];
            String v = (String) tuple[1];
            IMemObj fObj = vRegObjMap.get(v);
            relGlobalAllocMem.add(v, fObj.toString());
            relFuncPtr.add(fObj.toString(), m);
        }
        for (Object[] tuple : relGlobalAlloca.getValTuples()) {
            String v = (String) tuple[0];
            IMemObj vObj = vRegObjMap.get(v);
            relGlobalAllocMem.add(v, vObj.toString());
        }
        for (Object[] tuple : relAlloca.getValTuples()) {
            String v = (String) tuple[0];
            IMemObj vObj = vRegObjMap.get(v);
            relAllocMem.add(v, vObj.toString());
        }
        for (var entry : ptrStoreMap.entrySet()) {
            IMemObj pointerObj = entry.getKey();
            IMemObj contentObj = entry.getValue();
            relHeapAllocPtr.add(pointerObj.toString(), contentObj.toString());
        }
        for (var entry : arrayStoreMap.entrySet()) {
            IMemObj arrObj = entry.getKey();
            for (var contentEntry: entry.getValue().entrySet()) {
                String pos = contentEntry.getKey();
                IMemObj contentPtrObj = contentEntry.getValue();
                relHeapAllocArr.add(arrObj.toString(), pos, contentPtrObj.toString());
            }
        }
        for (var entry : arraySizeMap.entrySet()) {
            IMemObj arrObj = entry.getKey();
            String size = entry.getValue();
            relHeapArraySize.add(arrObj.toString(), size);
        }
        for (var entry : fieldStoreMap.entrySet()) {
            IMemObj baseObj = entry.getKey();
            for (var fieldEntry : entry.getValue().entrySet()) {
                String field = fieldEntry.getKey();
                IMemObj fieldPtrObj = fieldEntry.getValue();
                relHeapAllocFld.add(baseObj.toString(), field, fieldPtrObj.toString());
            }
        }
    }

    @Override
    protected String getOutDir() {
        return workPath.toAbsolutePath().toString();
    }

    private IMemObj createFuncObj(String m) {
        IMemObj funcObj = new FuncObj(m);
        Messages.debug("CMemoryModel: create mem object for function [%s]", funcObj.toString());
        return funcObj;
    }

    private IMemObj createStackObj(String accessPath, String type) {
        IMemObj obj = new StackObj(accessPath, type);
        stackObjs.add(obj);
        Messages.debug("CMemoryModel: create stack object {%s}", obj.toString());
        if (arrTypeContentMap.containsKey(type)) {
            String contentType = arrTypeContentMap.get(type);
            String sizeStr = arrTypeSizeMap.get(type);
            arraySizeMap.put(obj, sizeStr);

            Map<String, IMemObj> posMap = new LinkedHashMap<>();
            String posStr;
            if (!sizeStr.equals("unknown")) {
                posStr = "0~" + (Integer.parseInt(sizeStr) - 1);
            } else {
                posStr = "0~unknown";
            }
            String contentPath = accessPath + "[" + posStr + "]";
            IMemObj contentObj = createStackObj(contentPath, contentType);
            posMap.put(posStr, contentObj);

            if (posStr.startsWith("0")) {
                ptrStoreMap.put(obj, contentObj);
                Messages.debug("CMemoryModel: array base {%s} points to first element {%s}", obj.toString(), contentObj.toString());
            }

            arrayStoreMap.put(obj, posMap);
        } else if (fldTypeMap.containsKey(type)) {
            Map<String, String> fieldMap = fldTypeMap.get(type);
            for (String field : fieldMap.keySet()) {
                String fieldPath = accessPath + "." + field;
                String fieldType = fieldMap.get(field);
                IMemObj fieldObj = createStackObj(fieldPath, fieldType);
                fieldStoreMap.computeIfAbsent(obj, o -> new LinkedHashMap<>()).put(field, fieldObj);
            }
        }
        return obj;
    }
}
