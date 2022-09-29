package com.neuromancer42.tea.program.cdt.memmodel;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.program.cdt.memmodel.object.FuncObj;
import com.neuromancer42.tea.program.cdt.memmodel.object.IMemObj;
import com.neuromancer42.tea.program.cdt.memmodel.object.StackObj;
import org.eclipse.cdt.core.dom.ast.*;
import org.eclipse.cdt.internal.core.dom.parser.c.CPointerType;

import java.util.*;

public class CMemoryModel extends JavaAnalysis {
    private final Map<Integer, IMemObj> vRegObjMap = new LinkedHashMap<>();
    private final Set<IMemObj> stackObjs = new LinkedHashSet<>();
    private final Map<IMemObj, IMemObj> ptrStoreMap = new LinkedHashMap<>();
    private final Map<IMemObj, IMemObj[]> arrayStoreMap = new LinkedHashMap<>();
    private final Map<IMemObj, Map<IField, IMemObj>> fieldStoreMap = new LinkedHashMap<>();

    public CMemoryModel() {
        this.name = "CMemModel";
        createDomConsumer("V", Integer.class);
        createDomConsumer("F", IField.class);
        createDomConsumer("M", IFunction.class);

        createDomProducer("H", IMemObj.class);

        createRelConsumer("funcRef", "M", "V");
        createRelConsumer("GlobalAlloca", "V", "A");
        createRelConsumer("Alloca", "V", "A");

        createRelProducer("GlobalAlloc", "V", "H");
        createRelProducer("Alloc", "V", "H");
        createRelProducer("HeapAllocPtr", "H", "H");
        createRelProducer("HeapAllocArr", "H", "H");
        createRelProducer("HeapAllocFld", "H", "F", "H");
        createRelProducer("funcPtr", "H", "M");
    }

    @Override
    public void run() {
        ProgramDom<Integer> domV = consume("V");
        ProgramDom<IFunction> domM = consume("M");
        ProgramDom<IField> domF = consume("F");
        ProgramRel relFuncRef = consume("funcRef");
        relFuncRef.load();
        ProgramRel relGlobalAlloca = consume("GlobalAlloca");
        relGlobalAlloca.load();
        ProgramRel relAlloca = consume("Alloca");
        relAlloca.load();
        ProgramDom<IMemObj> domH = ProgramDom.createDom("H", IMemObj.class);
        ProgramRel relGlobalAlloc = new ProgramRel("GlobalAlloc", domV, domH);
        ProgramRel relAlloc = new ProgramRel("Alloc", domV, domH);
        ProgramRel relHeapAllocPtr = new ProgramRel("HeapAllocPtr", domH, domH);
        ProgramRel relHeapAllocArr = new ProgramRel("HeapAllocArr", domH, domH);
        ProgramRel relHeapAllocFld = new ProgramRel("HeapAllocFld", domH, domF, domH);
        ProgramRel relFuncPtr = new ProgramRel("funcPtr", domH, domM);

        for (Object[] tuple : relFuncRef.getValTuples()) {
            IFunction m = (IFunction) tuple[0];
            Integer v = (Integer) tuple[1];
            IMemObj fObj = createFuncObj(m);
            vRegObjMap.put(v, fObj);
        }
        for (Object[] tuple : relGlobalAlloca.getValTuples()) {
            Integer v = (Integer) tuple[0];
            IVariable variable = (IVariable) tuple[1];
            IMemObj vObj = createStackObj(variable);
            vRegObjMap.put(v, vObj);
        }
        for (Object[] tuple : relAlloca.getValTuples()) {
            Integer v = (Integer) tuple[0];
            IVariable variable = (IVariable) tuple[1];
            IMemObj vObj = createStackObj(variable);
            vRegObjMap.put(v, vObj);
        }

        domH.init();
        for (IMemObj obj : vRegObjMap.values()) {
            domH.add(obj);
        }
        for (IMemObj obj : stackObjs) {
            domH.add(obj);
        }
        domH.save();
        produceDom(domH);

        ProgramRel[] generatedRels = new ProgramRel[]{relGlobalAlloc, relAlloc, relHeapAllocPtr, relHeapAllocArr, relHeapAllocFld, relFuncPtr};
        for (ProgramRel rel : generatedRels) {
            rel.init();
        }
        for (Object[] tuple : relFuncRef.getValTuples()) {
            IFunction m = (IFunction) tuple[0];
            Integer v = (Integer) tuple[1];
            IMemObj fObj = vRegObjMap.get(v);
            relGlobalAlloc.add(v, fObj);
            relFuncPtr.add(fObj, m);
        }
        for (Object[] tuple : relGlobalAlloca.getValTuples()) {
            Integer v = (Integer) tuple[0];
            IMemObj vObj = vRegObjMap.get(v);
            relGlobalAlloc.add(v, vObj);
        }
        for (Object[] tuple : relAlloca.getValTuples()) {
            Integer v = (Integer) tuple[0];
            IMemObj vObj = vRegObjMap.get(v);
            relAlloc.add(v, vObj);
        }
        for (var entry : ptrStoreMap.entrySet()) {
            IMemObj pointerObj = entry.getKey();
            IMemObj contentObj = entry.getValue();
            relHeapAllocPtr.add(pointerObj, contentObj);
        }
        for (var entry : arrayStoreMap.entrySet()) {
            IMemObj arrObj = entry.getKey();
            for (IMemObj offsetPtrObj : entry.getValue()) {
                relHeapAllocArr.add(arrObj, offsetPtrObj);
            }
        }
        for (var entry : fieldStoreMap.entrySet()) {
            IMemObj baseObj = entry.getKey();
            for (var fieldEntry : entry.getValue().entrySet()) {
                IField field = fieldEntry.getKey();
                IMemObj fieldPtrObj = fieldEntry.getValue();
                relHeapAllocFld.add(baseObj, field, fieldPtrObj);
            }
        }

        relFuncRef.close();
        relGlobalAlloca.close();
        relAlloca.close();

        for (ProgramRel rel : generatedRels) {
            rel.save();
            rel.close();
            produceRel(rel);
        }
    }

    private IMemObj createFuncObj(IFunction m) {
        IMemObj funcObj = new FuncObj(m);
        Messages.debug("CMemoryModel: create mem object for function [%s]", funcObj.toDebugString());
        return funcObj;
    }

    private IMemObj createStackObj(IVariable variable) {
        IType t = variable.getType();
        // Note: special case, make variables of basic type observable
        while (t instanceof ITypedef) {
            t = ((ITypedef) t).getType();
        }
        if (t instanceof IBasicType || t instanceof IPointerType) {
            StackObj obj = new StackObj(variable);
            stackObjs.add(obj);
            Messages.debug("CMemoryModel: create stack object {%s}", obj.toDebugString());
            return obj;
        }
        return createStackObj(variable.getName(), variable.getType());
    }

    private IMemObj createStackObj(String name, IType type) {
        while (type instanceof ITypedef) {
            type = ((ITypedef) type).getType();
        }
        if (type instanceof IBasicType || type instanceof IPointerType) {
            IMemObj obj = new StackObj(name, type);
            stackObjs.add(obj);
            Messages.debug("CMemoryModel: create stack object {%s}", obj.toDebugString());
            return obj;
        }
        if (type instanceof IArrayType) {
            // TODO: no extra divide on array contents
            String arrName = name + ".arr";
            IMemObj arrObj = new StackObj(arrName, type);
            stackObjs.add(arrObj);

            int arrlen = 1;
            IMemObj[] offsetObjs = new IMemObj[arrlen];
            for (int i = 0; i < arrlen; ++i) {
                String contentName = name + "[]";
                IType contentType = ((IArrayType) type).getType();
                IMemObj contentObj = createStackObj(contentName, contentType);

                IType ptrType = new CPointerType(contentType, 0);
                IMemObj ptrObj = new StackObj(name, ptrType);
                stackObjs.add(ptrObj);
                ptrStoreMap.put(ptrObj, contentObj);
                offsetObjs[i] = ptrObj;
            }
            arrayStoreMap.put(arrObj, offsetObjs);
            Messages.debug("CMemoryModel: convert stack array to pointer object {%s} |-> {%s}", offsetObjs[0].toDebugString(), ptrStoreMap.get(offsetObjs[0]).toDebugString());
            return offsetObjs[0];
        }
        if (type instanceof ICompositeType) {
            ICompositeType compType = (ICompositeType) type;
            if (compType.getKey() == ICompositeType.k_struct) {
                IMemObj structObj = new StackObj(name, type);
                stackObjs.add(structObj);
                Messages.debug("CMemoryModel: create structure object {%s}", structObj.toDebugString());

                Map<IField, IMemObj> fieldPtrMap = new LinkedHashMap<>();
                for (IField field : compType.getFields()) {
                    String fieldName = name + "." + field.getName();
                    IType fieldType = field.getType();
                    IMemObj fieldObj = createStackObj(fieldName, fieldType);

                    String fieldPtrName = "&" + fieldName;
                    IType fieldPtrType = new CPointerType(fieldType, 0);
                    IMemObj fieldPtrObj = new StackObj(fieldPtrName, fieldPtrType);
                    stackObjs.add(fieldPtrObj);
                    ptrStoreMap.put(fieldPtrObj, fieldObj);
                    Messages.debug("CMemoryModel: create field pointer object {%s} |-> {%s}", fieldPtrObj.toDebugString(), fieldObj.toDebugString());
                    fieldPtrMap.put(field, fieldPtrObj);
                }
                fieldStoreMap.put(structObj, fieldPtrMap);
                return structObj;
            }
        }
        Messages.error("CMemoryModel: [TODO] no extra handling of object [%s] of type %s[%s]", name, type.getClass().getSimpleName(), type);
        IMemObj obj = new StackObj(name, type);
        stackObjs.add(obj);
        return obj;
    }
}
