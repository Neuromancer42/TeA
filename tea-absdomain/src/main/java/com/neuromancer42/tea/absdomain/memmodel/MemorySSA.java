package com.neuromancer42.tea.absdomain.memmodel;

import com.neuromancer42.tea.commons.analyses.AbstractAnalysis;
import com.neuromancer42.tea.commons.analyses.annotations.*;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Messages;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.nio.file.Path;
import java.util.*;

@TeAAnalysis(name = "memory_ssa")
public class MemorySSA extends AbstractAnalysis {
    public static final String name = "memory_ssa";
    public static final Integer INIT_GEN = 0;
    private final Path workpath;

    @ConsumeDom
    public ProgramDom domA;
    @ConsumeDom
    public ProgramDom domM;
    @ConsumeDom
    public ProgramDom domP;
    @ConsumeDom
    public ProgramDom domB;
    @ConsumeDom
    public ProgramDom domZ;

    @ConsumeRel(name = "instruction_basicblock", doms = {"P", "B"})
    public ProgramRel relPB;

    @ConsumeRel(name = "basicblock_pred", doms = {"B", "B"})
    public ProgramRel relBBpred;

    @ConsumeRel(name = "basicblock_entry", doms = {"B", "P"})
    public ProgramRel relBBentry;

    @ConsumeRel(name = "MP", doms = {"M", "P"})
    public ProgramRel relMP;
    @ConsumeRel(name = "MPentry", doms = {"M", "P"})
    public ProgramRel relMPentry;
//    @ConsumeRel(name = "MPexit", doms = {"M", "P"})
//    public ProgramRel relMPexit;

    @ConsumeRel(name = "load_may_use", doms = {"P", "A"})
    public ProgramRel relLoadUse;
    @ConsumeRel(name = "store_may_def", doms = {"P", "A"})
    public ProgramRel relStoreDef;
    @ConsumeRel(name = "iarg_may_use", doms = {"P", "Z", "A"})
    public ProgramRel relIArgUse;
    @ConsumeRel(name = "iarg_may_def", doms = {"P", "Z", "A"})
    public ProgramRel relIArgDef;
    @ConsumeRel(name = "iret_may_use", doms = {"P", "A"})
    public ProgramRel relIRetUse;
    @ConsumeRel(name = "iret_may_def", doms = {"P", "A"})
    public ProgramRel relIRetDef;

    @ConsumeRel(name = "live_on_entry", doms = {"M", "Z", "A"})
    public ProgramRel relLiveOnEntry;
    @ConsumeRel(name = "ret_may_use", doms = {"P", "A"})
    public ProgramRel relRetUse;
    @ConsumeRel(name = "global_obj", doms = {"A"})
    public ProgramRel relGlobalObj;

    @ConsumeRel(name = "DUedge", doms = {"P", "P"})
    public ProgramRel relDUedge;

    @ProduceDom(description = "local mem regions of every function")
    public ProgramDom domR;
    @ProduceDom(description = "version number of a region")
    public ProgramDom domG;

    @ProduceRel(name = "region_contain", doms = {"R", "A"})
    public ProgramRel relRegContain;

    @ProduceRel(name = "memuse", doms = {"P", "R", "G"})
    public ProgramRel relMemUse;
    @ProduceRel(name = "memdef", doms = {"P", "R", "G", "G"})
    public ProgramRel relMemDef;
    @ProduceRel(name = "memphi", doms = {"P", "R", "G", "B", "G"}) // Note: location of memory phi does not matter
    public ProgramRel relMemPhi;
    @ProduceRel(name = "memassign", doms = {"R", "G", "G"}) // simplified version of memory phi
    public ProgramRel relMemAssign;
    @ProduceRel(name = "mementry", doms = {"M", "R"})
    public ProgramRel relMemEntry;
    @ProduceRel(name = "initgen", doms = {"G"})
    public ProgramRel relInitGen;

    private final Map<String, List<BitSet>> methRegions = new HashMap<>();
    private final Map<String, Set<Integer>> entryRegions = new HashMap<>();
    private final Map<String, Map<Integer, Map<String, Integer>>> memuses = new HashMap<>();
    private final Map<String, Map<Integer, Map<String, Pair<Integer, Integer>>>> memdefs = new HashMap<>();
    private final Map<String, Map<Integer, Map<String, Pair<Integer, Map<String, Integer>>>>> memphis = new HashMap<>();

    @Override
    protected void domPhase() {
        BitSet globalObj = new BitSet();
        for (Object[] tuple : relGlobalObj.getValTuples()) {
            String h = (String) tuple[0];
            globalObj.set(domA.indexOf(h));
        }
        Map<String, Map<String, BitSet>> entryObjs = new LinkedHashMap<>();
        for (Object[] tuple : relLiveOnEntry.getValTuples()) {
            String m = (String) tuple[0];
            String i = (String) tuple[1];
            String h = (String) tuple[2];
            entryObjs.computeIfAbsent(m, k -> new LinkedHashMap<>()).computeIfAbsent(i, k -> new BitSet()).set(domA.indexOf(h));
        }
        Map<String, BitSet> retObjs = new LinkedHashMap<>();
        for (Object[] tuple : relRetUse.getValTuples()) {
            String p = (String) tuple[0];
            String h = (String) tuple[1];
            retObjs.computeIfAbsent(p, k -> new BitSet()).set(domA.indexOf(h));
        }

        Map<String, BitSet> storeObjs = new HashMap<>();
        for (Object[] tuple : relStoreDef.getValTuples()) {
            String p = (String) tuple[0];
            String h = (String) tuple[1];
            storeObjs.computeIfAbsent(p, k -> new BitSet()).set(domA.indexOf(h));
        }

        Map<String, BitSet> loadObjs = new HashMap<>();
        for (Object[] tuple : relLoadUse.getValTuples()) {
            String p = (String) tuple[0];
            String h = (String) tuple[1];
            loadObjs.computeIfAbsent(p, k -> new BitSet()).set(domA.indexOf(h));
        }

        Map<String, Map<String, BitSet>> iArgUseObjs = new HashMap<>();
        for (Object[] tuple : relIArgUse.getValTuples()) {
            String p = (String) tuple[0];
            String i = (String) tuple[1];
            String h = (String) tuple[2];
            iArgUseObjs.computeIfAbsent(p, k -> new LinkedHashMap<>()).computeIfAbsent(i, k -> new BitSet()).set(domA.indexOf(h));
        }
        Map<String, Map<String, BitSet>> iArgDefObjs = new HashMap<>();
        for (Object[] tuple : relIArgDef.getValTuples()) {
            String p = (String) tuple[0];
            String i = (String) tuple[1];
            String h = (String) tuple[2];
            iArgDefObjs.computeIfAbsent(p, k -> new LinkedHashMap<>()).computeIfAbsent(i, k -> new BitSet()).set(domA.indexOf(h));
        }
        Map<String, BitSet>  iRetUseObjs = new HashMap<>();
        for (Object[] tuple : relIRetUse.getValTuples()) {
            String p = (String) tuple[0];
            String h = (String) tuple[1];
            iRetUseObjs.computeIfAbsent(p, k -> new BitSet()).set(domA.indexOf(h));
        }
        Map<String, BitSet> iRetDefObjs = new HashMap<>();
        for (Object[] tuple : relIRetDef.getValTuples()) {
            String p = (String) tuple[0];
            String h = (String) tuple[1];
            iRetDefObjs.computeIfAbsent(p, k -> new BitSet()).set(domA.indexOf(h));
        }

        Map<String, Set<String>> prevEdges = new LinkedHashMap<>();
        for (Object[] tuple : relDUedge.getValTuples()) {
            String from = (String) tuple[0];
            String to = (String) tuple[1];
            prevEdges.computeIfAbsent(to, k -> new LinkedHashSet<>()).add(from);
        }

        Map<String, List<String>> mp = new HashMap<>();
        for (Object[] tuple : relMP.getValTuples()) {
            String m = (String) tuple[0];
            String p = (String) tuple[1];
            mp.computeIfAbsent(m, k -> new ArrayList<>()).add(p);
        }

        Map<String, String> mpEntry = new HashMap<>();
        for (Object[] tuple : relMPentry.getValTuples()) {
            String m = (String) tuple[0];
            String p = (String) tuple[1];
            mpEntry.put(m, p);
        }

        Map<String, String> PinBB = new HashMap<>();
        for (Object[] tuple : relPB.getValTuples()) {
            String p = (String) tuple[0];
            String bb = (String) tuple[1];
            PinBB.put(p, bb);
        }

        Map<String, String> BBentry = new HashMap<>();
        for (Object[] tuple : relBBentry.getValTuples()) {
            String bb = (String) tuple[0];
            String p = (String) tuple[1];
            BBentry.put(bb, p);
        }

        Map<String, Set<String>> BBpreds = new HashMap<>();
        for (Object[] tuple : relBBpred.getValTuples()) {
            String pred = (String) tuple[0];
            String bb = (String) tuple[1];
            BBpreds.computeIfAbsent(bb, k -> new LinkedHashSet<>()).add(pred);
        }

        domG.add(INIT_GEN.toString());
        for (String m : mp.keySet()) {
            if (!mpEntry.containsKey(m)) {
                continue;
            }
            Messages.debug("MemorySSA: build SSA for method %s", m);
            String entryP = mpEntry.get(m);
            Map<String, BitSet> useObjs = new LinkedHashMap<>();
            Map<String, BitSet> defObjs = new LinkedHashMap<>();
            List<BitSet> origPartition = new ArrayList<>();

//            Messages.debug("MemorySSA: global objects");
//            Messages.debug("%s", globalObj.toString());

            origPartition.add(globalObj);

            for (String i : entryObjs.getOrDefault(m, Map.of()).keySet()) {
                BitSet entryObj = entryObjs.getOrDefault(m, Map.of()).get(i);
                origPartition.add(entryObj);

//                Messages.debug("MemorySSA: entry object %s", i);
//                Messages.debug("%s", entryObj.toString());
            }

            List<String> localPs = mp.get(m);
            // Sort program points for stable output
            localPs.sort(String::compareTo);
            for (String p : localPs) {
                if (storeObjs.containsKey(p)) {
                    BitSet storeObj = storeObjs.get(p);
                    origPartition.add(storeObj);
                    defObjs.computeIfAbsent(p, k -> new BitSet()).or(storeObj);

//                    Messages.debug("MemorySSA: store object %s", p);
//                    Messages.debug("%s", storeObj.toString());
                }
                if (loadObjs.containsKey(p)) {
                    BitSet loadObj = loadObjs.get(p);
                    origPartition.add(loadObj);
                    useObjs.computeIfAbsent(p, k -> new BitSet()).or(loadObj);

//                    Messages.debug("MemorySSA: load object %s", p);
//                    Messages.debug("%s", loadObj.toString());
                }
                if (iArgUseObjs.containsKey(p)) {
                    BitSet useObj = useObjs.computeIfAbsent(p, k -> new BitSet());
                    for (BitSet invkUse : iArgUseObjs.getOrDefault(p, Map.of()).values()) {
                        origPartition.add(invkUse);
                        useObj.or(invkUse);

//                        Messages.debug("MemorySSA: invk use object %s", p);
//                        Messages.debug("%s", invkUse.toString());
                    }
                }
                if (iArgDefObjs.containsKey(p)) {
                    BitSet defObj = defObjs.computeIfAbsent(p, k -> new BitSet());
                    for (BitSet invkDef : iArgDefObjs.getOrDefault(p, Map.of()).values()) {
                        origPartition.add(invkDef);
                        defObj.or(invkDef);

//                        Messages.debug("MemorySSA: invk def object %s", p);
//                        Messages.debug("%s", invkDef.toString());
                    }
                }
                if (iRetUseObjs.containsKey(p)) {
                    BitSet useObj = iRetUseObjs.get(p);
                    origPartition.add(useObj);
                    useObjs.computeIfAbsent(p, k -> new BitSet()).or(useObj);

//                    Messages.debug("MemorySSA: ret use object %s", p);
//                    Messages.debug("%s", useObj.toString());
                }
                if (iRetDefObjs.containsKey(p)) {
                    BitSet defObj = iRetDefObjs.get(p);
                    origPartition.add(defObj);
                    defObjs.computeIfAbsent(p, k -> new BitSet()).or(defObj);

//                    Messages.debug("MemorySSA: ret def object %s", p);
//                    Messages.debug("%s", defObj.toString());
                }
                if (retObjs.containsKey(p)) {
                    BitSet retObj = retObjs.get(p);
                    origPartition.add(retObj);
                    useObjs.computeIfAbsent(p, k -> new BitSet()).or(retObj);

//                    Messages.debug("MemorySSA: returned object %s", p);
//                    Messages.debug("%s", retObj.toString());
                }
            }
//            Messages.debug("MemorySSA: initially has %d partitions", origPartition.size());
            Map<Integer, Map<String, Integer>> localMemUses = new LinkedHashMap<>();
            Map<Integer, Map<String, Pair<Integer, Integer>>> localMemDefs = new LinkedHashMap<>();
            Map<Integer, Map<String, Pair<Integer, Map<String, Integer>>>> localMemPhis = new LinkedHashMap<>();
            List<BitSet> localregions = buildSSA(useObjs, defObjs, entryP, origPartition, prevEdges, PinBB, BBpreds, BBentry, localMemUses, localMemDefs, localMemPhis);
            methRegions.put(m, localregions);
            memuses.put(m, localMemUses);
            memdefs.put(m, localMemDefs);
            memphis.put(m, localMemPhis);

            BitSet entryObj = new BitSet();
            for (BitSet liveOnEntry : entryObjs.getOrDefault(m, Map.of()).values()) {
                entryObj.or(liveOnEntry);
            }
            Set<Integer> localEntryRegions = new HashSet<>();
            entryRegions.put(m, localEntryRegions);
            for (int rid = 0; rid < localregions.size(); ++rid) {
                if (localMemUses.containsKey(rid) || localMemDefs.containsKey(rid) || localMemPhis.containsKey(rid)) {
                    if (localregions.get(rid).intersects(entryObj) || localregions.get(rid).intersects(globalObj)) {
                        localEntryRegions.add(rid);
                    }
                    String regStr = m + "." + rid;
                    domR.add(regStr);
                    for (var def : localMemDefs.get(rid).values()) {
                        domG.add(def.getLeft().toString());
                    }
                    for (var phi : localMemPhis.get(rid).values()) {
                        domG.add(phi.getLeft().toString());
                    }
                }
            }
        }
    }

    @Override
    protected void relPhase() {
        relInitGen.add(INIT_GEN.toString());
        for (String m : domM) {
            List<BitSet> localRegions = methRegions.get(m);
            if (localRegions == null)
                continue;
            Set<Integer> entryRegion = entryRegions.getOrDefault(m, Set.of());
            Map<Integer, Map<String, Integer>> localMemUses = memuses.getOrDefault(m, Map.of());
            Map<Integer, Map<String, Pair<Integer, Integer>>> localMemDefs = memdefs.getOrDefault(m, Map.of());
            Map<Integer, Map<String, Pair<Integer, Map<String, Integer>>>> localMemPhis = memphis.getOrDefault(m, Map.of());
            for (int rid = 0; rid < localRegions.size(); ++rid) {
                if (localMemUses.containsKey(rid) || localMemDefs.containsKey(rid) || localMemPhis.containsKey(rid)) {
                    String regStr = m + "." + rid;
                    if (entryRegion.contains(rid)) {
                        relMemEntry.add(m, regStr);
                    }
                    for (var useEntry : localMemUses.get(rid).entrySet()) {
                        String p = useEntry.getKey();
                        Integer gen = useEntry.getValue();
                        relMemUse.add(p, regStr, gen.toString());
                    }
                    for (var defEntry : localMemDefs.get(rid).entrySet()) {
                        String p = defEntry.getKey();
                        Integer newGen = defEntry.getValue().getLeft();
                        Integer oldGen = defEntry.getValue().getRight();
                        relMemDef.add(p, regStr, newGen.toString(), oldGen.toString());
                    }
                    for (var phiEntry : localMemPhis.get(rid).entrySet()) {
                        String p = phiEntry.getKey();
                        Integer newGen = phiEntry.getValue().getLeft();
                        for (var phiTarget : phiEntry.getValue().getRight().entrySet()) {
                            String bb = phiTarget.getKey();
                            Integer targetGen = phiTarget.getValue();
                            relMemPhi.add(p, regStr, newGen.toString(), bb, targetGen.toString());
                            relMemAssign.add(regStr, newGen.toString(), targetGen.toString());
                        }
                    }

                    BitSet region = localRegions.get(rid);
                    for (int objId = region.nextSetBit(0); objId >= 0; objId = region.nextSetBit(objId + 1)) {
                        String obj = domA.get(objId);
                        relRegContain.add(regStr, obj);
                    }
                }
            }
        }
    }

    private static List<BitSet> buildSSA(Map<String, BitSet> useObjs, Map<String, BitSet> defObjs, String entryP, List<BitSet> originalPartition, Map<String, Set<String>> prevEdges, Map<String, String> pBB, Map<String, Set<String>> predsBB, Map<String, String> entryBB, Map<Integer, Map<String, Integer>> memuses, Map<Integer, Map<String, Pair<Integer, Integer>>> memdefs, Map<Integer, Map<String, Pair<Integer, Map<String, Integer>>>> memphis) {
        List<BitSet> regions = partitionDisjointMemRegions(originalPartition);
        // Note: sort the list for stable numbering
        regions.sort(Comparator.comparing(BitSet::toString));

//        Messages.debug("MemorySSA: generated %d regions", regions.size());
//        for (BitSet r : regions) {
//            Messages.debug("%s", r.toString());
//        }

        for (int i = 0; i < regions.size(); ++i) {
            BitSet r = regions.get(i);

//            Messages.debug("MemorySSA: build region %d 's versions", i);
//            Messages.debug("%s", r.toString());

            Set<String> uses = new LinkedHashSet<>();
            for (String p : useObjs.keySet()) {
                BitSet objs = useObjs.get(p);
                if (objs.intersects(r)) {
                    uses.add(p);
                }
            }
//            Messages.debug("MemorySSA: region %d has %d uses", i, uses.size());
            Set<String> defs = new LinkedHashSet<>();
            for (String p : defObjs.keySet()) {
                BitSet objs = defObjs.get(p);
                if (objs.intersects(r)) {
                    defs.add(p);
                }
            }
//            Messages.debug("MemorySSA: region %d has %d defs", i, defs.size());
            if (defs.isEmpty() && uses.isEmpty()) {
                continue;
            }
            Map<String, String> prevOp = new HashMap<>();
            Map<String, Map<String, String>> phiOps = new LinkedHashMap<>();
            for (String use : uses) {
                Set<String> pFrontiers = computeDominateFrontiers(use, uses, defs, entryP, prevEdges);
                propagateMemoryPhi(use, pFrontiers, pBB, entryBB, predsBB, prevOp, phiOps);
            }
            for (String def : defs) {
                Set<String> pFrontiers = computeDominateFrontiers(def, uses, defs, entryP, prevEdges);
                propagateMemoryPhi(def, pFrontiers, pBB, entryBB, predsBB, prevOp, phiOps);
            }
//            Messages.debug("MemorySSA: region %d initially has %d phis", i, phiOps.size());
            Map<String, Integer> rUses = new LinkedHashMap<>();
            Map<String, Pair<Integer, Integer>> rDefs = new LinkedHashMap<>();
            Map<String, Pair<Integer, Map<String, Integer>>> rPhis = new LinkedHashMap<>();
            versioningRegions(uses, defs, prevOp, phiOps, rUses, rDefs, rPhis);
//            Messages.debug("MemorySSA: region %d pruned to %d phis", i, rPhis.size());
            memuses.put(i, rUses);
            memdefs.put(i, rDefs);
            memphis.put(i, rPhis);
        }
        return regions;
    }

    private static void versioningRegions(Set<String> uses, Set<String> defs, Map<String, String> prevOp, Map<String, Map<String, String>> phiOps, Map<String, Integer> rMemUse, Map<String, Pair<Integer, Integer>> rMemDef, Map<String, Pair<Integer, Map<String, Integer>>> rMemPhi) {
        // set version numbers for all memdef and memphi
        // and fill in union find set, in order to prune redundant memory phis
        // version #0 for either "live on entry" or "undef"
        List<Integer> verRemapping = new ArrayList<>();
        verRemapping.add(INIT_GEN);
        Map<String, Integer> defVer = new LinkedHashMap<>();
        for (String def : defs) {
            int ver = verRemapping.size();
            defVer.put(def, ver);
            verRemapping.add(ver);
        }
        Map<String, Integer> phiVer = new LinkedHashMap<>();
        for (String phi : phiOps.keySet()) {
            int ver = verRemapping.size();
            phiVer.put(phi, ver);
            verRemapping.add(ver);
        }
        // find all targets of memory phis, and fill in usage counting as well;
        Map<Integer, Map<String, Integer>> phiTargetMap = new HashMap<>();
        Map<Integer, Set<Integer>> usedPhis = new HashMap<>();
        Map<Integer, Map<Integer, Set<String>>> phiRevTargetMap = new HashMap<>();
        Deque<Pair<Integer, Integer>> mergeWorkList = new LinkedList<>();
        for (var phiEntry : phiOps.entrySet()) {
            String phi = phiEntry.getKey();
            int phiNewVer = phiVer.get(phi);
            Map<String, String> targetOp = phiEntry.getValue();
            Map<String, Integer> targetVerMap = new LinkedHashMap<>();
            Map<Integer, Set<String>> revTargetMap = new HashMap<>();
//            Messages.debug("MemorySSA: build targets for phi %d : %s", phiNewVer, phi);
            for (var targetEntry : targetOp.entrySet()) {
                String bb = targetEntry.getKey();
                String op = targetEntry.getValue();
//                Messages.debug("MemorySSA: finding previous version for target %s", bb);
                Integer targetVer;
                // phi/def/uses may overlap, and always in this order: phi (merging previous paths) -> use (before invoke) -> def (after invoke)
                if (defs.contains(op)) {
                    targetVer = defVer.get(op);
//                    Messages.debug("MemorySSA: phi uses target ver %d from previous def %s", targetVer, op);
                } else if (uses.contains(op)) {
                    Integer usedVer = null;
                    while (uses.contains(op)) {
                        if (phiOps.containsKey(op)) {
                            usedVer = phiVer.get(op);
//                            Messages.debug("MemorySSA: previous use %s uses version %d of recent phi", op, usedVer);
                            break;
                        }
//                        Messages.debug("MemorySSA: phi finds target ver for previous use %s", op);
                        op = prevOp.get(op);
                        if (defs.contains(op)) {
                            usedVer = phiVer.get(op);
//                            Messages.debug("MemorySSA: previous use uses version %d of previous def %s", usedVer, op);
                            break;
                        }
                    }
                    if (usedVer == null) {
                        if (phiOps.containsKey(op)) {
                            usedVer = verRemapping.get(phiVer.get(op));
//                            Messages.debug("MemorySSA: previous use uses version %d of previous phi %s", usedVer, op);
                        } else {
//                            Messages.debug("MemorySSA: previous use uses ver 0 of initial %s", op == null ? "null" : op);
                            usedVer = INIT_GEN;
                        }
                    }
                    targetVer = usedVer;
                } else if (phiOps.containsKey(op)) {
                    targetVer = phiVer.get(op);
//                    Messages.debug("MemorySSA: phi uses target ver %d from previous phi %s", targetVer, op);
                } else {
//                    Messages.debug("MemorySSA: phi uses target ver 0 from entry %s", op == null ? "null" : op);
                    targetVer = INIT_GEN;
                }
                targetVerMap.put(bb, targetVer);

                usedPhis.computeIfAbsent(targetVer, k -> new LinkedHashSet<>()).add(phiNewVer);
                revTargetMap.computeIfAbsent(targetVer, k -> new LinkedHashSet<>()).add(bb);
            }
            phiTargetMap.put(phiNewVer, targetVerMap);

            phiRevTargetMap.put(phiNewVer, revTargetMap);

            int mergeVer = tryMergeTargets(phiNewVer, revTargetMap.keySet());
            if (mergeVer >= 0) {
                mergeWorkList.offer(new ImmutablePair<>(phiNewVer, mergeVer));
            }
        }
        // prune all single-source phi operations
        while (!mergeWorkList.isEmpty()) {
            Pair<Integer, Integer> mergePair = mergeWorkList.poll();
            Integer origVer = mergePair.getKey();
            Integer remapped = getRemapped(verRemapping, mergePair.getValue()); // merged ver may have been remmapped
            if (!phiTargetMap.containsKey(origVer)) {
//                Messages.debug("MemorySSA: original version %d has been remapped", origVer);
                continue; // already merged
            }
//            Messages.debug("MemorySSA: merging version %d into %d", origVer, remapped);
            // remove single-source phi operation
            phiTargetMap.remove(origVer);
            verRemapping.set(origVer, remapped);

            // join original version's usage into remapped version
            Set<Integer> phisToUpdate = usedPhis.remove(origVer);
            if (phisToUpdate != null) {
                usedPhis.computeIfAbsent(remapped, k -> new LinkedHashSet<>()).addAll(phisToUpdate);
                for (Integer updatePhi : phisToUpdate) {
                    Map<Integer, Set<String>> revTargetMap = phiRevTargetMap.get(updatePhi);
                    Set<String> remappingTargets = revTargetMap.remove(origVer);
                    revTargetMap.computeIfAbsent(remapped, k -> new LinkedHashSet<>()).addAll(remappingTargets);

                    int mergeVer = tryMergeTargets(updatePhi, revTargetMap.keySet());
                    if (mergeVer >= 0) {
                        mergeWorkList.offer(new ImmutablePair<>(updatePhi, mergeVer));
                    }
                }
            }
        }
        // update all remapping in verRemapping by path-halving
        for (int origVer = 0; origVer < verRemapping.size(); ++origVer) {
            int newVer = getRemapped(verRemapping, origVer);
            verRemapping.set(origVer, newVer);
//            Messages.debug("MemorySSA: version %d finally merged to version %d", origVer, newVer);
        }
        // generate all remapped memory operations
        for (String use : uses) {
            Set<String> prevUses = new LinkedHashSet<>();
            String prev = use;
            Integer prevVer = null;
            while (uses.contains(prev)) {
                if (rMemUse.containsKey(prev)) {
                    prevVer = rMemUse.get(prev);
//                    Messages.debug("MemorySSA: memuse %s already marked version %d", prev, prevVer);
                    break;
                }
//                Messages.debug("MemorySSA: exploiting version of memuse %s", prev);
                boolean noloop = prevUses.add(prev);
                if (!noloop) {
//                    Messages.debug("MemorySSA: usage loop from memuse %s", prev);
                    prevVer = INIT_GEN;
                    break;
                }
                // phi/def/uses may overlap, and always in this order: phi (merging previous paths) -> use (before invoke) -> def (after invoke)
                if (phiOps.containsKey(prev)) {
                    prevVer = verRemapping.get(phiVer.get(prev));
//                    Messages.debug("MemorySSA: use version %d from recent phi %s", prevVer, prev);
                    break;
                }
                prev = prevOp.get(prev);
                if (defs.contains(prev)) {
                    prevVer = defVer.get(prev);
//                    Messages.debug("MemorySSA: use version %d from previous def %s", prevVer, prev);
                    break;
                }
            }
            if (prevVer == null) {
                if (phiOps.containsKey(prev)) {
                    prevVer = verRemapping.get(phiVer.get(prev));
//                    Messages.debug("MemorySSA: use version %d from previous phi %s", prevVer, prev);
                } else {
                    prevVer = INIT_GEN;
//                    Messages.debug("MemorySSA: memuse %s uses ver 0 from %s", use, prev == null ? "null" : prev);
                }
            }
            for (String prevUse : prevUses) {
//                Messages.debug("MemorySSA: memuse %s uses version %s", use, prevVer);
                rMemUse.putIfAbsent(prevUse, prevVer);
            }
        }

        for (var defEntry : defVer.entrySet()) {
            String def = defEntry.getKey();
            int defNewVer = defEntry.getValue();
//            Messages.debug("MemorySSA: find orig of memdef #%d: %s", defNewVer, def);
            Integer prevVer;
            // phi/def/uses may overlap, and always in this order: phi (merging previous paths) -> use (before invoke) -> def (after invoke)
            if (uses.contains(def)) {
                prevVer = rMemUse.get(def);
//                Messages.debug("MemorySSA: uses version %d of recent use", prevVer);
            } else if (phiOps.containsKey(def)) {
                prevVer = verRemapping.get(phiVer.get(def));
//                Messages.debug("MemorySSA: uses version %d of recent phi", prevVer);
            } else {
                String prev = prevOp.get(def);
                if (defs.contains(prev)) {
                    prevVer = defVer.get(prev);
//                    Messages.debug("MemorySSA: uses version %d of previous def %s", prevVer, prev);
                } else if (uses.contains(prev)) {
                    prevVer = rMemUse.get(prev);
//                    Messages.debug("MemorySSA: uses version %d of previous use %s", prevVer, prev);
                } else if (phiOps.containsKey(prev)) {
                    prevVer = verRemapping.get(phiVer.get(prev));
//                    Messages.debug("MemorySSA: uses version %d of previous phi %s", prevVer, prev);
                } else {
//                    Messages.debug("MemorySSA: memdef %s uses initial version at %s", def, prev == null ? "null" : prev);
                    prevVer = INIT_GEN;
                }
            }
            rMemDef.put(def, new ImmutablePair<>(defNewVer, prevVer));
        }
        for (var phiEntry : phiVer.entrySet()) {
            String phi = phiEntry.getKey();
            int phiNewVer = phiEntry.getValue();
            Map<String, Integer> targetMap = phiTargetMap.get(phiNewVer);
            if (targetMap != null) {
                targetMap.replaceAll((bb, origVer) -> verRemapping.get(origVer));
                rMemPhi.put(phi, new ImmutablePair<>(phiNewVer, targetMap));
            }
        }
    }

    private static int getRemapped(List<Integer> verRemapping, int origVer) {
        int newVer = verRemapping.get(origVer);
        while (newVer != verRemapping.get(newVer)) {
            int newVer2 = verRemapping.get(verRemapping.get(newVer));
            verRemapping.set(newVer, newVer2);
            newVer = newVer2;
        }
        return newVer;
    }

    private static int tryMergeTargets(int newVer, Set<Integer> revTargetVers) {
        Set<Integer> revTargets = new HashSet<>(revTargetVers);
        revTargets.remove(newVer);
        if (revTargets.size() <= 1) {
            for (int prevVer : revTargets)
                return prevVer;
            // empty targets merged to init ver
            return INIT_GEN;
        }
        return -1;
    }
    private static void propagateMemoryPhi(String defuseNode, Set<String> pFrontiers, Map<String, String> pInBB, Map<String, String> BBentry, Map<String, Set<String>> prevBBs, Map<String, String> prevOperation, Map<String, Map<String, String>> phiOperations) {
        if (pFrontiers.size() == 1) {
            for (String prev : pFrontiers) {
                prevOperation.put(defuseNode, prev);
//                Messages.debug("MemorySSA: connect %s to previous operation %s", defuseNode, prev);
                return;
            }
        }
        if (pFrontiers.size() == 0) {
//            Messages.debug("MemorySSA: %s is the first memory operation since entry", defuseNode);
            return;
        }
        String bb = pInBB.get(defuseNode);
        String phi = BBentry.get(bb);
        prevOperation.put(defuseNode, phi);

//        Messages.debug("MemorySSA: connect %s to recent phi %s", defuseNode, phi);

        Map<String, String> lastOperations = new HashMap<>();
        for (String p : pFrontiers) {
            lastOperations.put(pInBB.get(p), p);
        }
        for (String p : phiOperations.keySet()) {
            lastOperations.putIfAbsent(pInBB.get(p), p);
        }
        Deque<Pair<String, String>> worklist = new LinkedList<>();
        worklist.add(new ImmutablePair<>(phi, bb));
        while (!worklist.isEmpty()) {
            Pair<String, String> phiAndBB = worklist.poll();
            String curPhi = phiAndBB.getKey();
            if (phiOperations.containsKey(curPhi))
                continue;
//            Messages.debug("MemorySSA: find previous operation for phi %s", curPhi);
            String curBB = phiAndBB.getValue();
            Map<String, String> phiTargets = new LinkedHashMap<>();
            for (String prevBB : prevBBs.getOrDefault(curBB, Set.of())) {
                if (lastOperations.containsKey(prevBB)) {
                    phiTargets.put(prevBB, lastOperations.get(prevBB));
//                    Messages.debug("MemorySSA: previous bb %s has last op %s", prevBB, lastOperations.get(prevBB));
                } else {
                    String prevPhi = BBentry.get(prevBB);
                    phiTargets.put(prevBB, prevPhi);
                    lastOperations.put(prevBB, prevPhi);
                    worklist.offer(new ImmutablePair<>(prevPhi, prevBB));
//                    Messages.debug("MemorySSA: previous bb %s has no op, add phi %s", prevBB, lastOperations.get(prevBB));
                }
            }
            phiOperations.put(curPhi, phiTargets);
        }
    }

    private static Set<String> computeDominateFrontiers(String node, Set<String> uses, Set<String> defs, String entry, Map<String, Set<String>> prevEdges) {
        Set<String> frontiers = new LinkedHashSet<>();
        Deque<String> prevNodes = new LinkedList<>(prevEdges.getOrDefault(node, Set.of()));
        Set<String> visited = new HashSet<>();
        while (!prevNodes.isEmpty()) {
            String prevNode = prevNodes.poll();
            if (visited.contains(prevNode))
                continue;
            visited.add(prevNode);
            if (uses.contains(prevNode) || defs.contains(prevNode) || entry.equals(prevNode)) {
                frontiers.add(prevNode);
            } else {
                for (String prevprev : prevEdges.getOrDefault(prevNode, Set.of())) {
                    prevNodes.offer(prevprev);
                }
            }
        }
        return frontiers;
    }

    private static List<BitSet> partitionDisjointMemRegions(List<BitSet> cands) {
        List<BitSet> regions = new ArrayList<>();
        for (BitSet cand : cands) {
//            Messages.debug("MemorySSA: new partition");
//            Messages.debug("%s", cand);
            List<BitSet> newRegions = new ArrayList<>();
            BitSet rem = new BitSet();
            rem.or(cand);
            for (BitSet orig : regions) {
                if (rem.isEmpty()) {
                    newRegions.add(orig);
                    continue;
                }
                BitSet inter = new BitSet();
                inter.or(rem);
                inter.and(orig);
                if (!inter.isEmpty()) {
                    newRegions.add(inter);
                    rem.xor(inter);
                    orig.xor(inter);
                    if (!orig.isEmpty())
                        newRegions.add(orig);
                } else {
                    newRegions.add(orig);
                }
            }
            if (!rem.isEmpty())
                newRegions.add(rem);
            regions.clear();
            regions.addAll(newRegions);
//            Messages.debug("MemorySSA: updated partition");
//            for (BitSet r : regions) {
//                Messages.debug("%s", r.toString());
//            }
        }
        return regions;
    }

    public MemorySSA(Path workpath) {
        this.workpath = workpath;
    }

    @Override
    protected String getOutDir() {
        return workpath.toAbsolutePath().toString();
    }
}
