package com.neuromancer42.tea.absdomain.memmodel;

import com.neuromancer42.tea.commons.analyses.AbstractAnalysis;
import com.neuromancer42.tea.commons.analyses.annotations.*;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import com.neuromancer42.tea.absdomain.memmodel.object.FuncObj;
import com.neuromancer42.tea.absdomain.memmodel.object.IMemObj;
import com.neuromancer42.tea.absdomain.memmodel.object.StackObj;

import java.nio.file.Path;
import java.util.*;

@TeAAnalysis(name = "c_mem_model")
public class CMemoryModel extends AbstractAnalysis {
    public static final String name = "c_mem_model";
    private final Path workPath;

    public static final List<String> mallocLikeFuncs = List.of("malloc", "alloca");
    // Note: does not check overlap of dst & src in memcpy
    private static final List<String> memcpyLikeFuncs = List.of("memcpy", "memmove");

    // type infos
    @ConsumeDom(description = "types")
    public ProgramDom domT;
    @ConsumeDom(description = "fields")
    public ProgramDom domF;
    @ConsumeDom(description = "constants")
    public ProgramDom domC;
    @ConsumeRel(doms = {"T", "F", "T"})
    public ProgramRel relStructFldType;
    @ConsumeRel(doms = {"T", "T", "C"})
    public ProgramRel relArrContentType;

    // Note : variables
    @ConsumeDom(description = "functions")
    public ProgramDom domM;
    @ConsumeRel(name = "funcRef", doms = {"M", "V"}, description = "function name is also a funcptr variable")
    public ProgramRel relFuncRef;
    @ConsumeDom(description = "registers")
    public ProgramDom domV;
    @ConsumeDom(description = "allocation instructions")
    public ProgramDom domA;
    @ConsumeDom(description = "program points, used for locate allocations")
    public ProgramDom domP;
    @ConsumeRel(doms = {"V", "A", "T"})
    public ProgramRel relGlobalAlloca;
    @ConsumeRel(doms = {"V", "A", "T"})
    public ProgramRel relAlloca;
    @ConsumeRel(doms = {"M", "P"})
    public ProgramRel relMP;
    @ConsumeRel(doms = {"P", "V"})
    public ProgramRel relPalloca;

    // Note : dynamic objects, "malloc-ed" ones
    @ConsumeDom(description = "invocations")
    public ProgramDom domI;
    @ConsumeDom
    public ProgramDom domZ;
    @ConsumeRel(doms = {"M", "I"})
    public ProgramRel relMI;
    @ConsumeRel(name = "StaticCall", doms = {"I", "M"})
    public ProgramRel relStaticCall;
    @ConsumeRel(name = "IinvkRet", doms = {"I", "V"})
    public ProgramRel relIinvkRet;
    @ConsumeRel(name = "IinvkArg", doms = {"I", "Z", "V"})
    public ProgramRel relIinvkArg;

    // Notes: each heap object is composed of <base_addr, offset>
    @ProduceDom(description = "abstract heap objects")
    public ProgramDom domH;

    @ProduceRel(doms = {"V", "H"})
    public ProgramRel relGlobalAllocMem;
    @ProduceRel(doms = {"V", "H"})
    public ProgramRel relAllocMem;
    @ProduceRel(doms = {"V", "H"})
    public ProgramRel relMallocMem;
    @ProduceRel(doms = {"C", "H"}, description = "abstraction of nullptr or wild pointers")
    public ProgramRel relConstAddr;
    @ProduceRel(doms = {"M", "H"}, description = "objects allocated in current method")
    public ProgramRel relLocalMH;
    @ProduceRel(doms = {"M", "H"}, description = "objects allocated outside current method")
    public ProgramRel relNonLocalMH;

    // 1. array to its contents
    //    Note i: do not distinguish different elements in an array for points-to analysis
    //    Note ii: an array object can be de-referenced to its contents
    @ProduceRel(doms = {"H", "H"})
    public ProgramRel relHeapArrayContent;
    @ProduceRel(doms = {"H", "T", "C"})
    public ProgramRel relFixArrayShape;
    @ProduceRel(doms = {"H", "T", "V"})
    public ProgramRel relVarArrayShape;

    // 2. struct object with field points to its members
    @ProduceRel(doms = {"H", "F", "H"})
    public ProgramRel relHeapStructField;

    // 3. func ptr objects points to a (executable) function
    @ProduceRel(name = "funcPtr", doms = {"H", "M"}, description = "mark heap object of a function")
    public ProgramRel relFuncPtr;

    @ProduceRel(name = "primH", doms = {"H"}, description = "mark heaps of non-pointer type")
    public ProgramRel relPrimH;
    
    private final Map<String, Map<String, String>> fldTypeMap = new LinkedHashMap<>();
    private final Map<String, String> arrTypeContentMap = new LinkedHashMap<>();
    private final Map<String, String> arrTypeSizeMap = new LinkedHashMap<>();

    private final Map<String, String> mallocVarMap = new LinkedHashMap<>();
    private final Map<String, String> mallocSizeMap = new HashMap<>();

    private final Map<String, IMemObj> refRegObjMap = new LinkedHashMap<>();
    private final Set<IMemObj> stackObjs = new LinkedHashSet<>();
    private final Map<IMemObj, IMemObj> heapArrayContentMap = new LinkedHashMap<>();
    private final Map<IMemObj, String> contentTypeMap = new LinkedHashMap<>();
    private final Map<IMemObj, String> contentFixLenMap = new LinkedHashMap<>();
    private final Map<IMemObj, String> contentVarLenMap = new LinkedHashMap<>();
    private final Map<IMemObj, Map<String, IMemObj>> heapStructFieldMap = new LinkedHashMap<>();

    private final Map<String, Set<String>> localObjMap = new HashMap<>();

    private final Set<IMemObj> primitiveObjs = new LinkedHashSet<>();

    public CMemoryModel(Path path) {
        // TODO: read sensitivity-settings from request
        workPath = path;
    }

    private void init(Map<String, ProgramDom> inputDoms, Map<String, ProgramRel> inputRels) {

        domT = inputDoms.get("T");
        domF = inputDoms.get("F");
        domC = inputDoms.get("C");
        relStructFldType = inputRels.get("StructFldType");
        relStaticCall.load();
        relArrContentType = inputRels.get("ArrContentType");
        relArrContentType.load();

        domM = inputDoms.get("M");
        domP = inputDoms.get("P");
        relFuncRef = inputRels.get("funcRef");
        relFuncRef.load();
        domV = inputDoms.get("V");
        domA = inputDoms.get("A");
        relGlobalAlloca = inputRels.get("GlobalAlloca");
        relGlobalAlloca.load();
        relAlloca = inputRels.get("Alloca");
        relAlloca.load();
        relMP = inputRels.get("MP");
        relMP.load();
        relPalloca = inputRels.get("Palloca");
        relPalloca.load();

        domI = inputDoms.get("I");
        domZ = inputDoms.get("Z");
        relMI = inputRels.get("MI");
        relMI.load();
        relStaticCall = inputRels.get("StaticCall");
        relStaticCall.load();
        relIinvkRet = inputRels.get("IinvkRet");
        relIinvkRet.load();
        relIinvkArg = inputRels.get("IinvkArg");
        relIinvkArg.load();

        domH = new ProgramDom("H");
        relGlobalAllocMem = new ProgramRel("GlobalAllocMem", domV, domH);
        relAllocMem = new ProgramRel("AllocMem", domV, domH);
        relMallocMem = new ProgramRel("MallocMem", domV, domH);
        relConstAddr = new ProgramRel("ConstHeap", domC, domH);
        relLocalMH = new ProgramRel("LocalMH", domM, domH);
        relNonLocalMH = new ProgramRel("NonLocalMH", domM, domH);
        relHeapArrayContent = new ProgramRel("HeapArrayContent", domH, domH);
        relFixArrayShape = new ProgramRel("FixArrayShape", domH, domT, domC);
        relVarArrayShape = new ProgramRel("VarArraYShape", domH, domT, domA);
        relHeapStructField = new ProgramRel("HeapStructField", domH, domF, domH);
        relFuncPtr = new ProgramRel("funcPtr", domH, domM);
        relPrimH = new ProgramRel("primH", domH);
    }

    private void openRels() {
        relGlobalAllocMem.init();
        relAllocMem.init();
        relMallocMem.init();
        relConstAddr.init();
        relLocalMH.init();
        relNonLocalMH.init();
        relHeapArrayContent.init();
        relFixArrayShape.init();
        relVarArrayShape.init();
        relHeapStructField.init();
        relFuncPtr.init();
        relPrimH.init();
    }

    private void closeRels() {
        relStructFldType.close();
        relArrContentType.close();

        relFuncRef.close();
        relGlobalAlloca.close();
        relAlloca.close();
        relMP.close();
        relPalloca.close();

        relMI.close();
        relStaticCall.close();
        relIinvkRet.close();
        relIinvkArg.close();

        relGlobalAllocMem.save(getOutDir());
        relGlobalAllocMem.close();
        relAllocMem.save(getOutDir());
        relAllocMem.close();
        relMallocMem.save(getOutDir());
        relMallocMem.close();
        relConstAddr.save(getOutDir());
        relConstAddr.close();
        relLocalMH.save(getOutDir());
        relLocalMH.close();
        relNonLocalMH.save(getOutDir());
        relNonLocalMH.close();
        relHeapArrayContent.save(getOutDir());
        relHeapArrayContent.close();
        relFixArrayShape.save(getOutDir());
        relFixArrayShape.close();
        relVarArrayShape.save(getOutDir());
        relVarArrayShape.close();
        relHeapStructField.save(getOutDir());
        relHeapStructField.close();
        relFuncPtr.save(getOutDir());
        relFuncPtr.close();
        relPrimH.save(getOutDir());
        relPrimH.close();
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
        Map<String, String> allocVinM = new HashMap<>();
        {
            Map<String, String> PinM = new HashMap<>();
            for (Object[] tuple : relMP.getValTuples()) {
                String m = (String) tuple[0];
                String p = (String) tuple[1];
                PinM.put(p, m);
            }
            for (Object[] tuple : relPalloca.getValTuples()) {
                String p = (String) tuple[0];
                String v = (String) tuple[1];
                String m = PinM.get(p);
                if (m != null) {
                    allocVinM.put(v,m);
                } else {
                    Messages.error("CMemoryModel: Program point %s not in any method", p);
                }
            }
        }
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
            refRegObjMap.put(v, fObj);
        }
        for (Object[] tuple : relGlobalAlloca.getValTuples()) {
            String v = (String) tuple[0];
            String variable = (String) tuple[1];
            String type = (String) tuple[2];
            IMemObj vObj = createStackObj(variable, type, null);
            Messages.debug("CMemoryModel: alloc global %s <- {%s}", v, vObj.toString());
            refRegObjMap.put(v, vObj);
        }
        for (Object[] tuple : relAlloca.getValTuples()) {
            String v = (String) tuple[0];
            String variable = (String) tuple[1];
            String type = (String) tuple[2];
            IMemObj vObj = createStackObj(variable, type, allocVinM.get(v));
            Messages.debug("CMemoryMode: alloc local %s <- {%s}", v, vObj.toString());
            refRegObjMap.put(v, vObj);
        }

        Set<String> mallocInvks = new LinkedHashSet<>();
        for (Object[] tuple : relStaticCall.getValTuples()) {
            String f = (String) tuple[1];
            if (mallocLikeFuncs.contains(f)) {
                String i = (String) tuple[0];
                mallocInvks.add(i);
            }
        }
        Map<String, String> mallocInM = new HashMap<>();
        for (Object[] tuple : relMP.getValTuples()) {
            String i = (String) tuple[1];
            if (mallocInvks.contains(i)) {
                String m = (String) tuple[0];
                mallocInM.put(i, m);
            }
        }
        for (Object[] tuple : relIinvkRet.getValTuples()) {
            String i = (String) tuple[0];
            if (mallocInvks.contains(i)) {
                String v = (String) tuple[1];
                mallocVarMap.put(i, v);
            }
        }
        for (Object[] tuple : relIinvkArg.getValTuples()) {
            String i = (String) tuple[0];
            if (mallocInvks.contains(i)) {
                String z = (String) tuple[1];
                if (!z.equals("0")) {
                    Messages.error("CMemoryModel: malloc invocation {%s} has more than one arguments", i);
                }
                String v = (String) tuple[2];
                mallocSizeMap.put(i, v);
            }
        }
        for (String i : mallocInvks) {
            String baseVar = mallocVarMap.get(i);
            String sizeVar = mallocSizeMap.get(i);
            String inMeth = mallocInM.get(i);
            if (baseVar == null) {
                Messages.error("CMemoryModel: malloc invocation {%s} has no ret var", i);
            } else if (sizeVar == null) {
                Messages.error("CMemoryModel: malloc invocation {%s} has no size var specified", i);
            } else {
                IMemObj obj = createMallocObj(sizeVar, inMeth);
                Messages.debug("CMemoryModel: malloc %s <- {%s}", baseVar, obj.toString());
                refRegObjMap.put(baseVar, obj);
            }
        }

        domH.add(nullObj.toString());
        for (IMemObj obj : refRegObjMap.values()) {
            domH.add(obj.toString());
        }
        for (IMemObj obj : stackObjs) {
            domH.add(obj.toString());
        }
    }

    @Override
    protected void relPhase() {
        relConstAddr.add(Constants.NULL, nullObj.toString());
        for (Object[] tuple : relFuncRef.getValTuples()) {
            String m = (String) tuple[0];
            String v = (String) tuple[1];
            IMemObj fObj = refRegObjMap.get(v);
            relGlobalAllocMem.add(v, fObj.toString());
            relFuncPtr.add(fObj.toString(), m);
        }
        for (Object[] tuple : relGlobalAlloca.getValTuples()) {
            String v = (String) tuple[0];
            IMemObj vObj = refRegObjMap.get(v);
            relGlobalAllocMem.add(v, vObj.toString());
        }
        for (Object[] tuple : relAlloca.getValTuples()) {
            String v = (String) tuple[0];
            IMemObj vObj = refRegObjMap.get(v);
            relAllocMem.add(v, vObj.toString());
        }
        for (String v : mallocVarMap.values()) {
            IMemObj vObj = refRegObjMap.get(v);
            if (vObj != null) {
                relMallocMem.add(v, vObj.toString());
            }
        }
        for (var entry : heapArrayContentMap.entrySet()) {
            IMemObj arrObj = entry.getKey();
            IMemObj contentObj = entry.getValue();
            relHeapArrayContent.add(arrObj.toString(), contentObj.toString());
            String contentType = contentTypeMap.get(contentObj);
            if (contentVarLenMap.containsKey(contentObj)) {
                String sizeVar = contentVarLenMap.get(contentObj);
                relVarArrayShape.add(contentObj.toString(), contentType, sizeVar);
            } else {
                String len = contentFixLenMap.getOrDefault(contentObj, Constants.UNKNOWN);
                relFixArrayShape.add(contentObj.toString(), contentType, len);
            }
        }
        for (var entry : heapStructFieldMap.entrySet()) {
            IMemObj baseObj = entry.getKey();
            for (var fieldEntry : entry.getValue().entrySet()) {
                String field = fieldEntry.getKey();
                IMemObj fieldPtrObj = fieldEntry.getValue();
                relHeapStructField.add(baseObj.toString(), field, fieldPtrObj.toString());
            }
        }
        for (String m : domM) {
            Set<String> localObjs = localObjMap.getOrDefault(m, Set.of());
            for (String h : domH) {
                if (localObjs.contains(h)) {
                    relLocalMH.add(m, h);
                } else {
                    relNonLocalMH.add(m, h);
                }
            }
        }
        for (IMemObj primObj : primitiveObjs) {
            relPrimH.add(primObj.toString());
        }
    }

    @Override
    protected String getOutDir() {
        return workPath.toAbsolutePath().toString();
    }

    private IMemObj createFuncObj(String m) {
        IMemObj funcObj = new FuncObj(m);
        localObjMap.computeIfAbsent(m, k -> new HashSet<>()).add(funcObj.toString());
        Messages.debug("CMemoryModel: create mem object for function [%s]", funcObj.toString());
        return funcObj;
    }

    private static final IMemObj nullObj = new StackObj("*"+Constants.NULL, Constants.TYPE_VOID);

    private IMemObj createStackObj(String accessPath, String type, String inMeth) {
        if (accessPath.equals(Constants.NULL)) {
            Messages.debug("CMemoryModel: find null object %s", accessPath);
            return nullObj;
        }
        IMemObj obj = new StackObj(accessPath, type);
        stackObjs.add(obj);
        if (inMeth != null) {
            localObjMap.computeIfAbsent(inMeth, k -> new HashSet<>()).add(obj.toString());
        }
        Messages.debug("CMemoryModel: create stack object {%s}", obj.toString());
        if (arrTypeContentMap.containsKey(type)) {
            String contentType = arrTypeContentMap.get(type);
            String contentLen = arrTypeSizeMap.getOrDefault(type, Constants.UNKNOWN);

            Map<String, IMemObj> posMap = new LinkedHashMap<>();
            String posStr = "0";
            String contentPath = accessPath + "[" + posStr + "]";
            IMemObj contentObj = createStackObj(contentPath, contentType, inMeth);
            posMap.put(posStr, contentObj);
            heapArrayContentMap.put(obj, contentObj);
            contentTypeMap.put(contentObj, contentType);
            contentFixLenMap.put(contentObj, contentLen);
        } else if (fldTypeMap.containsKey(type)) {
            Map<String, String> fieldMap = fldTypeMap.get(type);
            for (String field : fieldMap.keySet()) {
                String fieldPath = accessPath + "." + field;
                String fieldType = fieldMap.get(field);
                IMemObj fieldObj = createStackObj(fieldPath, fieldType, inMeth);
                heapStructFieldMap.computeIfAbsent(obj, o -> new LinkedHashMap<>()).put(field, fieldObj);
            }
        } else if (!type.endsWith("*")) {
            // Note: all objects other than pointer type is treated as primitive
            primitiveObjs.add(obj);
        }
        return obj;
    }

    private IMemObj createMallocObj(String sizeVarReg, String inMeth) {
        IMemObj obj = new StackObj("malloc(" + sizeVarReg + ")", Constants.TYPE_CHAR+"*");
        stackObjs.add(obj);
        IMemObj contentObj = new StackObj("*malloc(" + sizeVarReg + ")", Constants.TYPE_VOID);
        stackObjs.add(contentObj);
        if (inMeth != null) {
            localObjMap.putIfAbsent(inMeth, new HashSet<>());
            localObjMap.get(inMeth).addAll(List.of(obj.toString(), contentObj.toString()));
        }
        Messages.debug("CMemoryModel: create malloc-ed object {%s}", contentObj.toString());
        heapArrayContentMap.put(obj, contentObj);
        contentTypeMap.put(contentObj, Constants.TYPE_CHAR);
        contentVarLenMap.put(contentObj, sizeVarReg);
        // Note: contents of malloc-ed array is also treated as primitive
        primitiveObjs.add(contentObj);
        return contentObj;
    }
}
