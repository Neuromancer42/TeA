package com.neuromancer42.tea.absdomain.memmodel;

import com.neuromancer42.tea.commons.analyses.AbstractAnalysis;
import com.neuromancer42.tea.commons.analyses.annotations.ConsumeDom;
import com.neuromancer42.tea.commons.analyses.annotations.ConsumeRel;
import com.neuromancer42.tea.commons.analyses.annotations.ProduceRel;
import com.neuromancer42.tea.commons.analyses.annotations.TeAAnalysis;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;

import java.nio.file.Path;
import java.util.*;

@TeAAnalysis(name = "post_pt")
public class PostPointerAnalysis extends AbstractAnalysis {

    public static final String name = "post_pt";

    private final Path workPath;


    public PostPointerAnalysis(Path path) {
        workPath = path;
    }

    @ConsumeDom(description = "functions")
    public ProgramDom domM;

    @ConsumeDom(description = "variables")
    public ProgramDom domV;

    @ConsumeDom(description = "program points")
    public ProgramDom domP;

    @ConsumeDom(description = "abstract heaps")
    public ProgramDom domH;

    @ConsumeRel(doms = {"M"}, description = "marked external functions")
    public ProgramRel relExtMeth;

    @ConsumeRel(doms = {"M", "P"})
    public ProgramRel relMP;

    @ConsumeRel(name = "ci_MH", doms = {"M", "H"})
    public ProgramRel relCIMH;

    @ConsumeRel(doms = {"P", "V"})
    public ProgramRel relPstore;

    @ConsumeRel(doms = {"V", "V"})
    public ProgramRel relStorePtr;

    @ConsumeRel(name = "ci_pt", doms = {"V", "H"})
    public ProgramRel relCIPT;

    @ProduceRel(name = "P_strong_update", doms = {"P", "H"})
    public ProgramRel relPStrongUpdate;

    @ProduceRel(name = "P_weak_update", doms = {"P", "H"})
    public ProgramRel relPWeakUpdate;

    @ProduceRel(name = "P_no_update", doms = {"P", "H"})
    public ProgramRel relPNoUpdate;

    @ProduceRel(name = "ci_non_MH", doms = {"M", "H"})
    public ProgramRel relNonMH;

    public void run(Map<String, ProgramDom> inputDoms, Map<String, ProgramRel> inputRels) {
        domM = inputDoms.get("M");
        domV = inputDoms.get("V");
        domP = inputDoms.get("P");
        domH = inputDoms.get("H");

        relExtMeth = inputRels.get("ExtMeth");
        relMP = inputRels.get("MP");
        relCIMH = inputRels.get("ci_MH");
        relPstore = inputRels.get("Pstore");
        relStorePtr = inputRels.get("StorePtr");
        relCIPT = inputRels.get("ci_pt");
        ProgramRel[] consumedRels = new ProgramRel[]{relExtMeth, relMP, relCIMH, relPstore, relStorePtr, relCIPT};
        for (var rel : consumedRels)
            rel.load();

        relPStrongUpdate = new ProgramRel("P_strong_update", domP, domH);
        relPWeakUpdate = new ProgramRel("P_weak_update", domP, domH);
        relPNoUpdate = new ProgramRel("P_no_update", domP, domH);
        relNonMH = new ProgramRel("ci_non_MH", domM, domH);
        ProgramRel[] generatedRels = new ProgramRel[]{relPStrongUpdate, relPWeakUpdate, relPNoUpdate, relNonMH};
        for (var rel: generatedRels) {
            rel.init();
        }

        relPhase();

        for (var rel: consumedRels) {
            rel.close();
        }

        for (var rel: generatedRels) {
            rel.save(getOutDir());
            rel.close();
        }
    }

    @Override
    protected void domPhase() {
        // no new doms
    }

    @Override
    protected void relPhase() {
        Set<String> extMeths = new HashSet<>();
        for (Object[] tuple : relExtMeth.getValTuples()) {
            String m = (String) tuple[0];
            extMeths.add(m);
        }
        Map<String, String> PinM = new HashMap<>();
        for (Object[] tuple: relMP.getValTuples()) {
            String f = (String) tuple[0];
            String p = (String) tuple[1];
            PinM.put(p, f);
        }
        Map<String, Set<String>> mh = new LinkedHashMap<>();
        for (Object[] tuple: relCIMH.getValTuples()) {
            String f = (String) tuple[0];
            String h = (String) tuple[1];
            mh.computeIfAbsent(f, k -> new LinkedHashSet<>()).add(h);
        }

        Map<String, String> storeToPtr = new HashMap<>();
        for (Object[] tuple: relStorePtr.getValTuples()) {
            String u = (String) tuple[0];
            String v = (String) tuple[1];
            storeToPtr.put(v, u);
        }
        Map<String, Set<String>> pt = new HashMap<>();
        for (Object[] tuple: relCIPT.getValTuples()) {
            String u = (String) tuple[0];
            String h = (String) tuple[1];
            pt.computeIfAbsent(u, k -> new LinkedHashSet<>()).add(h);
        }


        for (Object[] tuple : relPstore.getValTuples()) {
            String p = (String) tuple[0];
            String m = PinM.get(p);
            Set<String> objs = mh.getOrDefault(m, new LinkedHashSet<>());
            String v = (String) tuple[1];
            String u = storeToPtr.get(v);
            Set<String> modObjs = pt.getOrDefault(u, new LinkedHashSet<>());
            for (String h : objs) {
                if (modObjs.contains(h)) {
                    if (modObjs.size() == 1) {
                        relPStrongUpdate.add(p, h);
                    } else {
                        relPWeakUpdate.add(p,h);
                    }
                } else {
                    relPNoUpdate.add(p,h);
                }
            }
        }

        for (String m : domM) {
            if (extMeths.contains(m))  {
                // Note: this is a temporal trick, treat all heaps in external methods as untouched
                //       interval.dl used another handling which does the same thing
                // TODO: mark impure external functions?
                for (String h : domH) {
                    relNonMH.add(m, h);
                }
            } else {
                Set<String> hs = mh.getOrDefault(m, Set.of());
                for (String h : domH) {
                    if (!hs.contains(h)) {
                        relNonMH.add(m, h);
                    }
                }
            }
        }
    }

    @Override
    protected String getOutDir() {
        return workPath.toAbsolutePath().toString();
    }
}
