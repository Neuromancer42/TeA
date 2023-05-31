package com.neuromancer42.tea.absdomain.memmodel;

import com.neuromancer42.tea.commons.analyses.AbstractAnalysis;
import com.neuromancer42.tea.commons.analyses.annotations.*;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;

import java.nio.file.Path;
import java.util.*;

@TeAAnalysis(name = "access_path")
public class AccessPath extends AbstractAnalysis {
    static public final String name = "access_path";
    private final Path workPath;

    @ConsumeDom
    ProgramDom domH;
    @ConsumeDom
    ProgramDom domZ;
    @ConsumeDom
    ProgramDom domT;
    @ConsumeDom
    ProgramDom domV;
    @ConsumeDom
    ProgramDom domC;

    @ConsumeRel(name = "struct_type_field", doms = {"T", "Z", "T"})
    ProgramRel relStructTyField;
    @ConsumeRel(name = "array_type_component", doms = {"T", "T"})
    ProgramRel relArrayTyComp;
    @ConsumeRel(name = "HeapUsedAsType", doms = {"H", "T"})
    ProgramRel relHeapType;

    @ProduceDom
    ProgramDom domA;
    @ProduceRel(name = "baseAP", doms = {"H", "A"})
    ProgramRel relBaseAP;
    @ProduceRel(name = "APtoObj", doms = {"A", "H"})
    ProgramRel relAPtoObj;
    @ProduceRel(name = "APtype", doms = {"A", "T"})
    ProgramRel relAPtype;
    @ProduceRel(name = "APfield", doms = {"A", "Z", "A"})
    ProgramRel relAPfield;
    @ProduceRel(name = "APmixed", doms = {"A"})
    ProgramRel relAPmixed;
    @ProduceRel(name = "ap_array_shape", doms = {"A", "T", "C"})
    ProgramRel relAPArrShape;

    Map<String, Set<String>> heapTypeMap = new LinkedHashMap<>();
    Map<String, String> arrayComponentMap = new HashMap<>();
    Map<String, Map<String, String>> structFieldMap = new HashMap<>();

    Map<String, Set<String>> heapAPs = new HashMap<>();
    Map<String, String> apType = new HashMap<>();
    Map<String, Map<String, String>> apField = new HashMap<>();

    @Override
    protected void domPhase() {
        // create access paths to primitive locations
        // we do not distinguish between array locations, and multi-dim arrays are also merged
        for (Object[] tuple : relArrayTyComp.getValTuples()) {
            String arrTy = (String) tuple[0];
            String compTy = (String) tuple[1];
            arrayComponentMap.put(arrTy, compTy);
        }
        for (Object[] tuple : relStructTyField.getValTuples()) {
            String structTy = (String) tuple[0];
            Map<String, String> fieldMap = structFieldMap.computeIfAbsent(structTy, k -> new LinkedHashMap<>());
            String z = (String) tuple[1];
            String fldTy = (String) tuple[2];
            fieldMap.put(z, fldTy);
        }
        for (Object[] tuple : relHeapType.getValTuples()) {
            String heap = (String) tuple[0];
            Set<String> maybeType = heapTypeMap.computeIfAbsent(heap, k -> new HashSet<>());
            String type = (String) tuple[1];
            String unwrapped = actualType(type);
            maybeType.add(unwrapped);
        }
        for (String obj : domH) {
            Set<String> apSet = new LinkedHashSet<>();
            if (heapTypeMap.containsKey(obj) && heapTypeMap.get(obj).size() == 1) {
                for (String ty : heapTypeMap.get(obj)) {
                    createAP(obj, ty, apSet);
                }
            } else {
                apSet.add(obj);
            }
            heapAPs.put(obj, apSet);

            for (String ap : apSet) {
                domA.add(ap);
            }
        }
    }

    @Override
    protected void relPhase() {
        for (String ap : domA) {
            if (apType.containsKey(ap)) {
                relAPtype.add(ap, apType.get(ap));
            } else if (apField.containsKey(ap)) {
                for (var fieldentry : apField.get(ap).entrySet()) {
                    relAPfield.add(ap, fieldentry.getKey(), fieldentry.getValue());
                }
            } else {
                relAPmixed.add(ap);
            }
        }
        for (String h: domH) {
            relBaseAP.add(h, h);
            for (String ap : heapAPs.get(h)) {
                relAPtoObj.add(ap, h);
            }
        }
    }

    private void createAP(String prefix, String ty, Set<String> apSet) {
        if (structFieldMap.containsKey(ty)) {
            apSet.add(prefix);
            Map<String, String> fieldMap = new LinkedHashMap<>();
            apField.put(prefix, fieldMap);
            for (var field : structFieldMap.get(ty).entrySet()) {
                String f = field.getKey();
                String fTy = field.getValue();
                String sub = prefix + "." + f;
                fieldMap.put(f, sub);
                createAP(sub, actualType(fTy), apSet);
            }
        } else {
            // TODO check if it is basic type?
            apSet.add(prefix);
            apType.put(prefix, ty);
        }
    }

    private String actualType(String actualType) {
        while (arrayComponentMap.containsKey(actualType))
            actualType = arrayComponentMap.get(actualType);
        return actualType;
    }

    public AccessPath(Path workPath) {
        this.workPath = workPath;
    }

    @Override
    protected String getOutDir() {
        return workPath.toAbsolutePath().toString();
    }
}
