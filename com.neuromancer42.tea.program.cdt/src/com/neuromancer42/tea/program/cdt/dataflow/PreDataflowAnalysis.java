package com.neuromancer42.tea.program.cdt.dataflow;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.analyses.ProgramRel;

import com.neuromancer42.tea.program.cdt.memmodel.object.IMemObj;
import org.eclipse.cdt.codan.core.model.cfg.IBasicBlock;

import java.util.*;

public class PreDataflowAnalysis extends JavaAnalysis {

    public PreDataflowAnalysis() {
        this.name = "preDataflow";
        createDomConsumer("P", IBasicBlock.class);
        createDomConsumer("H", IMemObj.class);

        createRelConsumer("MP", "M", "P");
        createRelConsumer("ci_MH", "M", "H");
        createRelConsumer("Pstore", "P", "V");
        createRelConsumer("StorePtr", "V", "V");
        createRelConsumer("ci_pt", "V", "H");

        createRelProducer("P_strong_update", "P", "H");
        createRelProducer("P_weak_update", "P", "H");
        createRelProducer("P_no_update", "P", "H");
    }

    @Override
    public void run() {
        ProgramDom<IBasicBlock> domP = consumeTrgt("P");
        ProgramDom<IMemObj> domH = consumeTrgt("H");

        ProgramRel relMP = consumeTrgt("MP");
        ProgramRel relCIMH = consumeTrgt("ci_MH");
        ProgramRel relPstore = consumeTrgt("Pstore");
        ProgramRel relStorePtr = consumeTrgt("StorePtr");
        ProgramRel relCIPT = consumeTrgt("ci_pt");
        ProgramRel[] consumedRels = new ProgramRel[]{relMP, relCIMH, relPstore, relStorePtr, relCIPT};
        for (var rel : consumedRels)
            rel.load();

        Map<IBasicBlock, Object> PinM = new HashMap<>();
        for (Object[] tuple: relMP.getValTuples()) {
            Object f = tuple[0];
            IBasicBlock p = (IBasicBlock) tuple[1];
            PinM.put(p, f);
        }
        Map<Object, Set<IMemObj>> mh = new LinkedHashMap<>();
        for (Object[] tuple: relCIMH.getValTuples()) {
            Object f = tuple[0];
            IMemObj h = (IMemObj) tuple[1];
            mh.computeIfAbsent(f, k -> new LinkedHashSet<>()).add(h);
        }

        Map<Integer, Integer> storeToPtr = new HashMap<>();
        for (Object[] tuple: relStorePtr.getValTuples()) {
            Integer u = (Integer) tuple[0];
            Integer v = (Integer) tuple[1];
            storeToPtr.put(v, u);
        }
        Map<Integer, Set<IMemObj>> pt = new HashMap<>();
        for (Object[] tuple: relCIPT.getValTuples()) {
            Integer u = (Integer) tuple[0];
            IMemObj h = (IMemObj) tuple[1];
            pt.computeIfAbsent(u, k -> new LinkedHashSet<>()).add(h);
        }

        ProgramRel relPStrongUpdate = new ProgramRel("P_strong_update", domP, domH);
        ProgramRel relPWeakUpdate = new ProgramRel("P_weak_update", domP, domH);
        ProgramRel relPNoUpdate = new ProgramRel("P_no_update", domP, domH);
        ProgramRel[] generatedRels = new ProgramRel[]{relPStrongUpdate, relPWeakUpdate, relPNoUpdate};
        for (var rel: generatedRels) {
            rel.init();
        }

        for (Object[] tuple : relPstore.getValTuples()) {
            IBasicBlock p = (IBasicBlock) tuple[0];
            Object m = PinM.get(p);
            Set<IMemObj> objs = mh.getOrDefault(m, new LinkedHashSet<>());
            Integer v = (Integer) tuple[1];
            Integer u = storeToPtr.get(v);
            Set<IMemObj> modObjs = pt.getOrDefault(u, new LinkedHashSet<>());
            boolean isStrong = modObjs.size() == 1;
            for (IMemObj h : objs) {
                if (modObjs.contains(h)) {
                    if (isStrong) {
                        relPStrongUpdate.add(p, h);
                    } else {
                        relPWeakUpdate.add(p,h);
                    }
                } else {
                    relPNoUpdate.add(p,h);
                }
            }
        }

        for (var rel: consumedRels) {
            rel.close();
        }

        for (var rel: generatedRels) {
            rel.save();
            rel.close();
            produceRel(rel);
        }
    }
}
