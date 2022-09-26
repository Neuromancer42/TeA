package com.neuromancer42.tea.program.cdt;

import com.neuromancer42.tea.core.analyses.AnalysesUtil;
import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.project.Trgt;
import com.neuromancer42.tea.program.cdt.internal.memory.IMemObj;
import org.eclipse.cdt.codan.core.model.cfg.IBasicBlock;

import java.util.*;

public class PreDataflowAnalysis extends JavaAnalysis {

    private final Trgt<ProgramDom<IBasicBlock>> tDomP;
    private final Trgt<ProgramDom<IMemObj>> tDomH;

    private final Trgt<ProgramRel> tRelMP;
    private final Trgt<ProgramRel> tRelCIMH;
    private final Trgt<ProgramRel> tRelPstore;
    private final Trgt<ProgramRel> tRelCIPT;
    private final Trgt<ProgramRel> tRelStorePtr;
    private final Trgt<ProgramRel> tRelPStrongUpdate;
    private final Trgt<ProgramRel> tRelPWeakUpdate;
    private final Trgt<ProgramRel> tRelPNoUpdate;

    public PreDataflowAnalysis() {
        this.name = "preDataflow";
        tDomP = AnalysesUtil.createDomTrgt(name, "P", IBasicBlock.class);
        tDomH = AnalysesUtil.createDomTrgt(name, "H", IMemObj.class);

        tRelMP = AnalysesUtil.createRelTrgt(name, "MP", "M", "P");
        tRelCIMH = AnalysesUtil.createRelTrgt(name, "ci_MH", "M", "H");
        tRelPstore = AnalysesUtil.createRelTrgt(name, "Pstore", "P", "V");
        tRelStorePtr = AnalysesUtil.createRelTrgt(name, "StorePtr", "V", "V");
        tRelCIPT = AnalysesUtil.createRelTrgt(name, "ci_pt", "V", "H");
        tRelPStrongUpdate = AnalysesUtil.createRelTrgt(name, "P_strong_update", "P", "H");
        tRelPWeakUpdate = AnalysesUtil.createRelTrgt(name, "P_weak_update", "P", "H");
        tRelPNoUpdate = AnalysesUtil.createRelTrgt(name, "P_no_update", "P", "H");
        registerConsumers(tDomP, tDomH, tRelMP, tRelCIMH, tRelPstore, tRelStorePtr, tRelCIPT);
        registerProducers(tRelPStrongUpdate, tRelPWeakUpdate, tRelPNoUpdate);
    }

    @Override
    public void run() {
        ProgramDom<IBasicBlock> domP = tDomP.get();
        ProgramDom<IMemObj> domH = tDomH.get();

        ProgramRel relMP = tRelMP.get();
        ProgramRel relCIMH = tRelCIMH.get();
        ProgramRel relPstore = tRelPstore.get();
        ProgramRel relStorePtr = tRelStorePtr.get();
        ProgramRel relCIPT = tRelCIPT.get();
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

        for (var rel: generatedRels) {
            rel.save();
            rel.close();
        }

        for (var rel: consumedRels) {
            rel.close();
        }

        tRelPStrongUpdate.accept(relPStrongUpdate);
        tRelPWeakUpdate.accept(relPWeakUpdate);
        tRelPNoUpdate.accept(relPNoUpdate);
    }
}
