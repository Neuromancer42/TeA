package com.neuromancer42.tea.absdomain.memmodel;

import com.neuromancer42.tea.absdomain.memmodel.object.*;
import com.neuromancer42.tea.commons.analyses.AbstractAnalysis;
import com.neuromancer42.tea.commons.analyses.annotations.*;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;

import java.nio.file.Path;
import java.util.*;

@TeAAnalysis(name = "llvm_mem_model")
public class LLVMMemoryModel extends AbstractAnalysis {
    public static final String name = "llvm_mem_model";
    public static final List<String> mallocLikeFuncs = List.of("malloc", "calloc", "realloc", "alloca");

    private final Path workPath;

    @ConsumeDom(description = "types")
    public ProgramDom domT;
    @ConsumeDom(description = "constants")
    public ProgramDom domC;

    // Note : variables
    @ConsumeDom(description = "functions")
    public ProgramDom domM;
    @ConsumeDom(description = "registers")
    public ProgramDom domV;
    @ConsumeDom(description = "program points, used for locate allocations")
    public ProgramDom domP;
    @ConsumeDom
    public ProgramDom domZ;
    @ConsumeRel(doms = {"M", "V"}, description = "function name is also a funcptr variable")
    public ProgramRel relFuncRef;
    @ConsumeRel(doms = {"V", "T"})
    public ProgramRel relGlobalAlloca;
    @ConsumeRel(doms = {"V", "V", "T"})
    public ProgramRel relAlloca;
    @ConsumeRel(name = "StaticCall", doms = {"P", "M"})
    public ProgramRel relStaticCall;
    @ConsumeRel(name = "IinvkRet", doms = {"P", "V"})
    public ProgramRel relIinvkRet;
    @ConsumeRel(doms = {"M", "V"})
    public ProgramRel relMV;

    // Notes: each heap object is composed of <base_addr, offset>
    @ProduceDom(description = "abstract heap objects")
    public ProgramDom domH;
    @ProduceRel(doms = {"H"})
    public ProgramRel relObjNull;
    @ProduceRel(doms = {"H"})
    public ProgramRel relObjUnknown;
//    @ProduceRel(doms = {"V", "H"})
//    public ProgramRel relGlobalAllocObj;
    @ProduceRel(doms = {"V", "H"})
    public ProgramRel relAllocObj;
    @ProduceRel(doms = {"H", "M"})
    public ProgramRel relObjFunc;
    @ProduceRel(doms = {"H", "C", "T"})
    public ProgramRel relObjFixShape;
    @ProduceRel(doms = {"H", "V", "T"})
    public ProgramRel relObjVarShape;
    @ProduceRel(doms = {"M", "H"}, description = "objects allocated in current method")
    public ProgramRel relLocalMH;
    @ProduceRel(doms = {"M", "H"}, description = "objects allocated outside current method")
    public ProgramRel relNonLocalMH;

    private final Map<String, FixShapeObj> globalVarObjMap = new LinkedHashMap<>();
    private final Map<String, FuncObj> funcVarObjMap = new LinkedHashMap<>();
    private final Map<String, VarShapeObj> localVarObjMap = new LinkedHashMap<>();
    private final Map<String, DynamicObj> dynObjMap = new LinkedHashMap<>();
    private final Map<String, String> HinM = new HashMap<>();

    public LLVMMemoryModel(Path path) {
        // TODO: read sensitivity-settings from request
        workPath = path;
    }

    @Override
    protected String getOutDir() {
        return workPath.toAbsolutePath().toString();
    }
    protected void domPhase() {
        domH.add(SpecialObj.nullObj.toString());
        domH.add(SpecialObj.unknownObj.toString());
        for (Object[] tuple : relFuncRef.getValTuples()) {
            String m = (String) tuple[0];
            String v = (String) tuple[1];
            FuncObj funcObj = new FuncObj(m);
            funcVarObjMap.put(v, funcObj);
            domH.add(funcObj.toString());
        }
        for (Object[] tuple : relGlobalAlloca.getValTuples()) {
            String v = (String) tuple[0];
            String t = (String) tuple[1];
            FixShapeObj stackObj = new FixShapeObj("*" + v, t);
            globalVarObjMap.put(v, stackObj);
            domH.add(stackObj.toString());
        }

        Map<String, String> VinM = new HashMap<>();
        for (Object[] tuple : relMV.getValTuples()) {
            String m = (String) tuple[0];
            String v = (String) tuple[1];
            VinM.put(v, m);
        }
        for (Object[] tuple : relAlloca.getValTuples()) {
            String v = (String) tuple[0];
            String num = (String) tuple[1];
            String type = (String) tuple[2];
            VarShapeObj localObj = new VarShapeObj("*" + v, type, num);
            localVarObjMap.put(v, localObj);
            domH.add(localObj.toString());
            String m = VinM.get(v);
            assert m != null;
            HinM.put(localObj.toString(), m);
        }
        Set<String> mallocLikeInvks = new HashSet<>();
        for (Object[] tuple : relStaticCall.getValTuples()) {
            String p = (String) tuple[0];
            String m = (String) tuple[1];
            if (mallocLikeFuncs.contains(m))
                mallocLikeInvks.add(p);
        }
        for (Object[] tuple : relIinvkRet.getValTuples()) {
            String p = (String) tuple[0];
            if (mallocLikeInvks.contains(p)) {
                String v = (String) tuple[1];
                DynamicObj dynObj = new DynamicObj(p);
                dynObjMap.put(v, dynObj);
                domH.add(dynObj.toString());
                String m = VinM.get(v);
                assert m != null;
                HinM.put(dynObj.toString(), m);
            }
        }
    }

    @Override
    protected void relPhase() {
        relObjNull.add(SpecialObj.nullObj.toString());
        relObjUnknown.add(SpecialObj.unknownObj.toString());
        for (var e : funcVarObjMap.entrySet()) {
            String v = e.getKey();
            FuncObj obj = e.getValue();
//            relGlobalAllocObj.add(v, obj.toString());
            relAllocObj.add(v, obj.toString());
            relObjFunc.add(obj.toString(), obj.getFunc());
        }
        for (var e : globalVarObjMap.entrySet()) {
            String v = e.getKey();
            FixShapeObj obj = e.getValue();
//            relGlobalAllocObj.add(v, obj.toString());
            relAllocObj.add(v, obj.toString());
            relObjFixShape.add(obj.toString(), obj.getNumElems(), obj.getObjectType());
        }
        for (var e : localVarObjMap.entrySet()) {
            String v = e.getKey();
            VarShapeObj obj = e.getValue();
            relAllocObj.add(v, obj.toString());
            relObjVarShape.add(obj.toString(), obj.getVarNumElems(), obj.getObjectType());
        }
        for (var e : dynObjMap.entrySet()) {
            String v = e.getKey();
            DynamicObj obj = e.getValue();
            relAllocObj.add(v, obj.toString());
        }
        for (String h : domH) {
            String inM = HinM.get(h);
            for (String m : domM) {
                if (m.equals(inM)) {
                    relLocalMH.add(m, h);
                } else {
                    relNonLocalMH.add(m, h);
                }
            }
        }
    }
}
