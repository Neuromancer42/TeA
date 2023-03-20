package com.neuromancer42.tea.absdomain.dataflow;

import com.google.protobuf.TextFormat;
import com.neuromancer42.tea.absdomain.dataflow.interval.Interval;
import com.neuromancer42.tea.absdomain.dataflow.interval.ItvPredicate;
import com.neuromancer42.tea.commons.analyses.AbstractAnalysis;
import com.neuromancer42.tea.commons.analyses.annotations.*;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Constants;
import com.neuromancer42.tea.commons.configs.Messages;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.nio.file.Path;
import java.util.*;

@TeAAnalysis(name = "gen_interval")
public class IntervalGenerator extends AbstractAnalysis {
    public static final String name = "gen_interval";

    private static final String emptyRepr = Interval.EMPTY.toString();
    private static final String zeroRepr = (new Interval(0)).toString();
    private static final String oneRepr = (new Interval(1)).toString();
    public static final String mininfRepr = Interval.MIN_INF.toString();
    public static final String maxinfRepr = Interval.MAX_INF.toString();
    private final Path workPath;

    public IntervalGenerator(Path path) {
        workPath = path;
    }

    @ConsumeDom(description = "variables")
    public ProgramDom domV;
    @ConsumeDom(description = "program points")
    public ProgramDom domP;
    @ConsumeDom(description = "expressions")
    public ProgramDom domE;
    @ConsumeDom(description = "constants")
    public ProgramDom domC;
    @ConsumeDom(description = "unary/binary operators")
    public ProgramDom domOP;
    @ConsumeDom(description = "types")
    public ProgramDom domT;
    @ConsumeRel(doms = {"V", "V"})
    public ProgramRel relLoadPtr;
    @ConsumeRel(doms = {"V", "V"})
    public ProgramRel relStorePtr;

    @ConsumeRel(doms = {"V", "C"})
    public ProgramRel relEconst;
    @ConsumeRel(doms = {"V", "OP", "V"})
    public ProgramRel relEunary;
    @ConsumeRel(doms = {"V", "OP", "V", "V"})
    public ProgramRel relEbinop;

    @ProduceDom(description = "intervals")
    public ProgramDom domU;
    @ProduceDom(description = "interval predicates")
    public ProgramDom domUP;
    @ProduceRel(doms = {"C", "U"}, description = "interval value of constants")
    public ProgramRel relConstU;
    @ProduceRel(name = "evalUnaryU", doms = {"OP", "U", "U"}, description = "unary computations of intervals")
    public ProgramRel relEvalUnaryU;
    @ProduceRel(name = "evalBinopU", doms = {"OP", "U", "U", "U"}, description = "binary computations of intervals")
    public ProgramRel relEvalBinopU;
    @ProduceRel(doms = {"U"}, description = "mark special empty value")
    public ProgramRel relUempty;
    @ProduceRel(doms = {"U"})
    public ProgramRel relUzero;
    @ProduceRel(doms = {"U"})
    public ProgramRel relUnonzero;
    @ProduceRel(doms = {"U"})
    public ProgramRel relUunknown;
    @ProduceRel(doms = {"U"}, description = "mark possible values of input")
    public ProgramRel relUinput;
    @ProduceRel(name = "evalPlusU", doms = {"U", "U", "U"})
    public ProgramRel relEvalPlusU;
    @ProduceRel(name = "evalMultU", doms = {"U", "U", "U"})
    public ProgramRel relEvalMultU;
    @ProduceRel(name = "evalLEU", doms = {"U", "U"})
    public ProgramRel relEvalLEU;

    @ConsumeRel(doms = {"P", "P", "V"})
    public ProgramRel relPPtrue;
//    @ConsumeRel(doms = {"P", "P", "V"})
//    public ProgramRel relPPfalse;
    @ConsumeRel(name = "ci_pt", doms = {"V", "H"})
    public ProgramRel relCIPT;
    @ConsumeDom(description = "(abstract) heap objects")
    public ProgramDom domH;
    @ProduceRel(doms = {"V"}, description = "mark unhandled cond-vars")
    public ProgramRel relPredUnknown;
    @ProduceRel(doms = {"V", "UP", "H", "U"}, description = "cond-var comes from comparing value of H and constant U")
    public ProgramRel relPredL;
    @ProduceRel(doms = {"V", "UP", "U", "H"}, description = "cond-var comes from comparing constant U and value of H")
    public ProgramRel relPredR;
    @ProduceRel(doms = {"V", "UP", "H", "H"}, description = "cond-var comes from comparing values of two variables")
    public ProgramRel relPred2;
    @ProduceRel(doms = {"UP", "U", "U"}, description = "condition may be true")
    public ProgramRel relMaySat;
    @ProduceRel(doms = {"UP", "U", "U"}, description = "condition may be false")
    public ProgramRel relMayUnsat;
    @ProduceRel(name = "nofilter", doms = {"V", "H"}, description = "cond-var has nothing to do with these heap objects")
    public ProgramRel relNoFilter;

    private final Map<String, Integer> literalMap = new HashMap<>();
    private final Set<Interval> sortedITVs = new LinkedHashSet<>();
    private final Map<String, Integer> regToLiteral = new HashMap<>();
    private final Map<String, Pair<String, String>> regToUnary = new HashMap<>();
    private final Map<String, Triple<String, String, String>> regToBinop = new HashMap<>();

    @Override
    protected void domPhase() {
        for (ItvPredicate itvp : ItvPredicate.allPredicates())
            domUP.add(itvp.toString());
        Set<Integer> intConstants = new LinkedHashSet<>();
        for (String c : domC) {
            try {
                int primVal = Integer.parseInt(c);
                literalMap.put(c, primVal);
                //Messages.debug("IntervalGenerator: find new constant integer %d", primVal);
                if (primVal >=  Interval.min_bound && primVal <= Interval.max_bound) {
                    intConstants.add(primVal);
                    intConstants.add(-primVal);
                }
            } catch (NumberFormatException e) {
                Messages.error("IntervalGenerator: not a constant integer %s", c);
            }
        }
        for (Object[] tuple : relEconst.getValTuples()) {
            String v = (String) tuple[0];
            String c = (String) tuple[1];
            if (literalMap.containsKey(c))
                regToLiteral.put(v, literalMap.get(c));
        }
        for (Object[] tuple : relEunary.getValTuples()) {
            String v = (String) tuple[0];
            String op = (String) tuple[1];
            String u = (String) tuple[2];
            regToUnary.put(v, new ImmutablePair<>(op, u));
        }
        for (Object[] tuple : relEbinop.getValTuples()) {
            String v = (String) tuple[0];
            String op = (String) tuple[1];
            String u1 = (String) tuple[2];
            String u2 = (String) tuple[3];
            regToBinop.put(v, new ImmutableTriple<>(op, u1, u2));
        }

        intConstants.add(0);
        intConstants.add(1);
        intConstants.add(-1);
        intConstants.add(Interval.min_bound);
        intConstants.add(Interval.max_bound);
        List<Integer> boundList = new ArrayList<>(intConstants);
        Collections.sort(boundList);
        Messages.debug("IntervalGenerator: found int constants: %s", Arrays.toString(boundList.toArray()));

        sortedITVs.add(Interval.MIN_INF);
        sortedITVs.add(new Interval(Integer.MIN_VALUE, boundList.get(0) - 1));
        for (int i = 0; i < boundList.size(); ++i) {
            sortedITVs.add(new Interval(boundList.get(i)));
            int l = boundList.get(i) + 1;
            int r = (i + 1 == boundList.size()) ? Integer.MAX_VALUE : (boundList.get(i + 1) - 1);
            if (l <= r)
                sortedITVs.add(new Interval(l, r));
        }
        sortedITVs.add(Interval.MAX_INF);
        Messages.debug("IntervalGenerator: generated Intervals: %s", Arrays.toString(sortedITVs.toArray()));
        domU.add(Interval.EMPTY.toString());
        for (Interval itv : sortedITVs) {
            domU.add(itv.toString());
        }
    }

    @Override
    protected void relPhase() {
        // mark heaps
        Map<String, String> regToHeap = new LinkedHashMap<>();
        regToHeap = buildVarRegToHeap();
        {
            // build arithmetics
            assert domU.contains(emptyRepr);
            assert domU.contains(zeroRepr);
            assert domU.contains(oneRepr);
            relUempty.add(emptyRepr);
            relUzero.add(zeroRepr);
            for (String u : domU) {
                if (!(u.equals(emptyRepr) || u.equals(maxinfRepr) || u.equals(mininfRepr))) {
                    relUinput.add(u);
                }
                if (!u.equals(zeroRepr)) {
                    relUnonzero.add(u);
                }
                relUunknown.add(u);
            }
            for (String op : domOP) {
                switch (op) {
                    case Constants.OP_ID:
                        for (String x : domU)
                            relEvalUnaryU.add(op, x, x);
                        break;
                    case Constants.OP_NEG:
                        relEvalUnaryU.add(op, emptyRepr, emptyRepr);
                        for (Interval x : sortedITVs) {
                            int l = -x.upper;
                            int r = -x.lower;
                            Set<Interval> res = filterInterval(sortedITVs, l, r);
                            for (Interval z : res)
                                relEvalUnaryU.add(op, x.toString(), z.toString());
                        }
                        break;
                    case Constants.OP_INCR:
                        computeIncr(sortedITVs, relEvalUnaryU);
                        break;
                    case Constants.OP_DECR:
                        computeDecr(sortedITVs, relEvalUnaryU);
                        break;
                    case Constants.OP_NOT:
                        computeNot(sortedITVs, relEvalUnaryU);
                        break;
                    case Constants.OP_AND:
                        computeAnd(sortedITVs, relEvalBinopU);
                        break;
                    case Constants.OP_OR:
                        computeOr(sortedITVs, relEvalBinopU);
                        break;
                    case Constants.OP_ADD:
                        computePlus(sortedITVs, relEvalBinopU, relEvalPlusU);
                        break;
                    case Constants.OP_SUB:
                        computeMinus(sortedITVs, relEvalBinopU);
                        break;
                    case Constants.OP_MUL:
                        computeMultiply(sortedITVs, relEvalBinopU, relEvalMultU);
                        break;
                    case Constants.OP_EQ:
                        computeEQ(sortedITVs, relEvalBinopU);
                        break;
                    case Constants.OP_NE:
                        computeNE(sortedITVs, relEvalBinopU);
                        break;
                    case Constants.OP_LT:
                        computeLT(sortedITVs, relEvalBinopU);
                        break;
                    case Constants.OP_LE:
                        computeLE(sortedITVs, relEvalBinopU, relEvalLEU);
                        break;
                    default:
                        for (String x : domU) {
                            relEvalUnaryU.add(op, x, emptyRepr);
                            for (String y : domU)
                                relEvalBinopU.add(op, x, y, emptyRepr);
                        }
                }
            }
            for (String c : domC) {
                if (literalMap.containsKey(c)) {
                    int primVal = literalMap.get(c);
                    relConstU.add(c, new Interval(primVal).toString());
                } else {
                    for (Interval itv : sortedITVs) {
                        relConstU.add(c, itv.toString());
                    }
                }
            }
//            {
//                // mark array objects
//                Map<String, Map<String, String>> contentMap = new LinkedHashMap<>();
//                for (Object[] tuple : relHeapAllocArr.getValTuples()) {
//                    String arrObj = (String) tuple[0];
//                    String pos = (String) tuple[1];
//                    String contentObj = (String) tuple[2];
//                    contentMap.computeIfAbsent(arrObj, k -> new LinkedHashMap<>()).put(pos, contentObj);
//                }
//                for (Object[] tuple : relHeapArraySize.getValTuples()) {
//                    String arrObj = (String) tuple[0];
//                    String sz = (String) tuple[1];
//                    for (var entry: contentMap.get(arrObj).entrySet()) {
//                        String pos = entry.getKey();
//                        for (Interval u : sortedITVs) {
//                            if (mayOutOfBound(pos, u, sz)) {
//                                relMayOutOfBound.add(pos, u.toString(), sz);
//                            }
//                        }
//                    }
//                }
//            }
        }

        {
            // build predicates
            Set<Interval> allItvs = new LinkedHashSet<>();
            allItvs.add(Interval.EMPTY);
            for (Interval itv: sortedITVs) {
                allItvs.add(itv);
            }
            for (ItvPredicate up : ItvPredicate.allPredicates()) {
                for (Interval u1 : allItvs) {
                    for (Interval u2 : allItvs) {
                        if (up.maysat(u1, u2))
                            relMaySat.add(up.toString(), u1.toString(), u2.toString());
                        if (up.mayunsat(u1, u2))
                            relMayUnsat.add(up.toString(), u1.toString(), u2.toString());
                    }
                }
            }

            for (Object[] tuple : relPPtrue.getValTuples()) {
                Interval u1 = null;
                Interval u2 = null;
                String h1 = null;
                String h2 = null;
                ItvPredicate pred = null;
                String origCondV = (String) tuple[2];
                {
                    // strip out outermost unary operations
                    String condv = origCondV;
                    // TODO: handling ++ and --?
                    boolean negated = false;
                    while (regToUnary.containsKey(condv)) {
                        Pair<String, String> expr = regToUnary.get(condv);
                        //Messages.debug("IntervalGenerator: strip cond-var %s to expression {%s}", condv, expr.toString());
                        condv = expr.getRight();
                        String op = expr.getLeft();
                        if (op.equals(Constants.OP_NOT)) {
                            negated = !negated;
                        }
                    }

                    // TODO: add handling of pointer's NULL-check, e.g. if (ptr) a = *ptr;
                    if (regToLiteral.containsKey(condv)) {
                        Integer l1 = regToLiteral.get(condv);
                        if (l1 != null) {
                            u1 = new Interval(l1);
                            u2 = new Interval(0);
                        }
                        //Messages.debug("IntervalGenerator: cond-var %s is literal %s", condv, u1);
                    } else if (regToHeap.containsKey(condv)) {
                        h1 = regToHeap.get(condv);
                        u2 = new Interval(0);
                        pred = new ItvPredicate(Constants.OP_NE, negated);
                        //Messages.debug("IntervalGenerator: cond-var %s is var %s", condv, h1);
                    } else if (regToBinop.containsKey(condv)) {
                        Triple<String, String, String> binop = regToBinop.get(condv);
                        //Messages.debug("IntervalGenerator: cond-var %s is expr %s", condv, binop.toString());
                        pred = new ItvPredicate(binop.getLeft(), negated);
                        String v1 = binop.getMiddle();
                        Integer l1 = regToLiteral.get(v1);
                        if (l1 != null) u1 = new Interval(l1);
                        h1 = regToHeap.get(v1);
                        String v2 = binop.getRight();
                        Integer l2 = regToLiteral.get(v2);
                        if (l2 != null) u2 = new Interval(l2);
                        h2 = regToHeap.get(v2);
                    }
                    assert ((u1 == null || h1 == null) && (u2 == null || h2 == null));
                }
                if (pred == null) {
                    //Messages.debug("IntervalGenerator: cannot handle cond-var %s, mark as unknown", condv);
                    relPredUnknown.add(origCondV);
                } else {
                    if (h1 != null && h2 != null) {
                        for (String h : domH) {
                            if (!h.equals(h1) && !h.equals(h2))
                                relNoFilter.add(origCondV, h);
                        }
                        relPred2.add(origCondV, pred.toString(), h1, h2);
                    } else if (h1 != null && u2 != null) {
                        for (String h : domH) {
                            if (!h.equals(h1))
                                relNoFilter.add(origCondV, h);
                        }
                        relPredL.add(origCondV, pred.toString(), h1, u2.toString());
                    } else if (u1 != null && h2 != null) {
                        for (String h : domH) {
                            if (!h.equals(h2))
                                relNoFilter.add(origCondV, h);
                        }
                        relPredR.add(origCondV, pred.toString(), u1.toString(), h2);
                    } else if (u1 != null && u2 != null) {
                        Messages.error("IntervalGenerator: cond-val @%d is constant [%s(%s,%s)]", origCondV, pred, u1, u2);
                        relPredUnknown.add(origCondV);
                    } else {
                        Messages.error("IntervalGenerator: unhandled binary expr for cond-var %s", origCondV);
                        relPredUnknown.add(origCondV);
                    }
                }
            }
        }
    }

    private Map<String, String> buildVarRegToHeap() {
        Map<String, Set<String>> pt = new HashMap<>();
        for (Object[] tuple : relCIPT.getValTuples()) {
            String v = (String) tuple[0];
            String h = (String) tuple[1];
            pt.computeIfAbsent(v, k -> new HashSet<>()).add(h);
        }
        Map<String, String> reg2Heap = new HashMap<>();
        for (Object[] tuple : relLoadPtr.getValTuples()) {
            String u = (String) tuple[0];
            String ptr = (String) tuple[1];
//            Messages.debug("IntervalGenerator: reg %s is loaded from ptr %s", u, ptr);
            {
                Set<String> objs = pt.get(ptr);
                if (objs != null && objs.size() == 1) {
                    for (String obj : objs) {
                        reg2Heap.put(u, obj);
                    }
                }
            }
        }
        return reg2Heap;
    }

    private static void computeIncr(Set<Interval> sortedITVs, ProgramRel relEvalUnaryU) {
        String op = Constants.OP_INCR;
        relEvalUnaryU.add(op, emptyRepr, emptyRepr);
        relEvalUnaryU.add(op, mininfRepr, mininfRepr);
        for (Interval x : sortedITVs) {
            int l = x.lower + 1;
            int r = x.upper + 1;
            Set<Interval> res = filterInterval(sortedITVs, l, r);
            for (Interval z : res)
                relEvalUnaryU.add(op, x.toString(), z.toString());
        }
    }

    private static void computeDecr(Set<Interval> sortedITVs, ProgramRel relEvalUnaryU) {
        String op = Constants.OP_DECR;
        relEvalUnaryU.add(op, emptyRepr, emptyRepr);
        relEvalUnaryU.add(op, maxinfRepr, maxinfRepr);
        for (Interval x : sortedITVs) {
            int l = x.lower - 1;
            int r = x.upper - 1;
            Set<Interval> res = filterInterval(sortedITVs, l, r);
            for (Interval z : res)
                relEvalUnaryU.add(op, x.toString(), z.toString());
        }
    }

    private static void computeNot(Set<Interval> sortedITVs, ProgramRel relEvalUnaryU) {
        String op = Constants.OP_NOT;
        relEvalUnaryU.add(op, emptyRepr, emptyRepr);
        for (Interval x : sortedITVs) {
            if (x.contains(0)) {
                relEvalUnaryU.add(op, x.toString(), oneRepr);
            }
            if (!(x.isSingleton() && x.contains(0))) {
                relEvalUnaryU.add(op, x.toString(), zeroRepr);
            }
        }
    }

    private static void computeAnd(Set<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = Constants.OP_AND;
        relEvalBinopU.add(op, emptyRepr, emptyRepr, emptyRepr);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, emptyRepr, x.toString(), emptyRepr);
            relEvalBinopU.add(op, x.toString(), emptyRepr, emptyRepr);
            if (x.contains(0)) {
                relEvalBinopU.add(op, x.toString(), emptyRepr, zeroRepr);
                for (Interval y : sortedITVs)
                    relEvalBinopU.add(op, x.toString(), y.toString(), zeroRepr);
            }
            if (!(x.isSingleton() && x.contains(0))) {
                for (Interval y : sortedITVs) {
                    if (y.contains(0)) {
                        relEvalBinopU.add(op, x.toString(), y.toString(), zeroRepr);
                    }
                    if (!(y.isSingleton() && y.contains(0))) {
                        relEvalBinopU.add(op, x.toString(), y.toString(), oneRepr);
                    }
                }
            }
        }
    }

    private static void computeOr(Set<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = Constants.OP_OR;
        relEvalBinopU.add(op, emptyRepr, emptyRepr, emptyRepr);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, emptyRepr, x.toString(), emptyRepr);
            relEvalBinopU.add(op, x.toString(), emptyRepr, emptyRepr);
            if (x.contains(0)) {
                for (Interval y : sortedITVs) {
                    if (y.contains(0)) {
                        relEvalBinopU.add(op, x.toString(), y.toString(), zeroRepr);
                    }
                    if (!(y.isSingleton() && y.contains(0))) {
                        relEvalBinopU.add(op, x.toString(), y.toString(), oneRepr);
                    }
                }
            }
            if (!(x.isSingleton() && x.contains(0))) {
                relEvalBinopU.add(op, x.toString(), emptyRepr, oneRepr);
                for (Interval y : sortedITVs) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), oneRepr);
                }
            }
        }
    }

    private static void computePlus(Set<Interval> sortedITVs, ProgramRel relEvalBinopU, ProgramRel relEvalPlusU) {
        String op = Constants.OP_ADD;
        relEvalBinopU.add(op, emptyRepr, emptyRepr, emptyRepr);
        relEvalPlusU.add(emptyRepr, emptyRepr, emptyRepr);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, emptyRepr, x.toString(), emptyRepr);
            relEvalPlusU.add(emptyRepr, x.toString(), emptyRepr);
            relEvalBinopU.add(op, x.toString(), emptyRepr, emptyRepr);
            relEvalPlusU.add(x.toString(), emptyRepr, emptyRepr);
            for (Interval y : sortedITVs) {
                int l = x.lower + y.lower;
                if (x.equals(Interval.MIN_INF) || y.equals(Interval.MIN_INF)) {
                    l = Interval.min_inf;
                }
                int r = x.upper + y.upper;
                if (x.equals(Interval.MAX_INF) || y.equals(Interval.MAX_INF)) {
                    r = Interval.max_inf;
                }
                Set<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval z : res) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), z.toString());
                    relEvalPlusU.add(x.toString(), y.toString(), z.toString());
                }
            }
        }
    }

    private static void computeMinus(Set<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = Constants.OP_SUB;
        relEvalBinopU.add(op, emptyRepr, emptyRepr, emptyRepr);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, emptyRepr, x.toString(), emptyRepr);
            relEvalBinopU.add(op, x.toString(), emptyRepr, emptyRepr);
            for (Interval y : sortedITVs) {
                int l = x.lower - y.upper;
                if (x.equals(Interval.MIN_INF) || y.equals(Interval.MAX_INF)) {
                    l = Interval.min_inf;
                }
                int r = x.upper - y.lower;
                if (x.equals(Interval.MAX_INF) || y.equals(Interval.MIN_INF)) {
                    r = Interval.max_inf;
                }
                Set<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval z : res) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), z.toString());
                }
            }
        }
    }

    private static void computeMultiply(Set<Interval> sortedITVs, ProgramRel relEvalBinopU, ProgramRel relEvalMultU) {
        String op = Constants.OP_MUL;
        relEvalBinopU.add(op, emptyRepr, emptyRepr, emptyRepr);
        relEvalMultU.add(emptyRepr, emptyRepr, emptyRepr);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, emptyRepr, x.toString(), emptyRepr);
            relEvalMultU.add(emptyRepr, x.toString(), emptyRepr);
            relEvalBinopU.add(op, x.toString(), emptyRepr, emptyRepr);
            relEvalMultU.add(x.toString(), emptyRepr, emptyRepr);
            for (Interval y : sortedITVs) {
                int p1 =  x.lower * y.lower,
                        p2 = x.lower * y.upper,
                        p3 = x.upper * y.lower,
                        p4 = x.upper * y.upper;
                int l = Collections.min(Arrays.asList(p1, p2 ,p3, p4));
                int r = Collections.max(Arrays.asList(p1, p2, p3, p4));
                Set<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval z : res) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), z.toString());
                    relEvalMultU.add(x.toString(), y.toString(), z.toString());
                }
            }
        }
    }

    private static void computeEQ(Set<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = Constants.OP_EQ;
        relEvalBinopU.add(op, emptyRepr, emptyRepr, emptyRepr);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, emptyRepr, x.toString(), emptyRepr);
            relEvalBinopU.add(op, x.toString(), emptyRepr, emptyRepr);
            relEvalBinopU.add(op, x.toString(), x.toString(), oneRepr);
            if (x.equals(Interval.MIN_INF) || x.equals(Interval.MAX_INF) || x.lower < x.upper) {
                relEvalBinopU.add(op, x.toString(), x.toString(), zeroRepr);
            }
            for (Interval y : sortedITVs) {
                if (!x.equals(y)) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), zeroRepr);
                }
            }
        }
    }

    private static void computeNE(Set<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = Constants.OP_NE;
        relEvalBinopU.add(op, emptyRepr, emptyRepr, emptyRepr);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, emptyRepr, x.toString(), emptyRepr);
            relEvalBinopU.add(op, x.toString(), emptyRepr, emptyRepr);
            relEvalBinopU.add(op, x.toString(), x.toString(), zeroRepr);
            if (x.equals(Interval.MIN_INF) || x.equals(Interval.MAX_INF) || x.lower < x.upper) {
                relEvalBinopU.add(op, x.toString(), x.toString(), oneRepr);
            }
            for (Interval y : sortedITVs) {
                if (!x.equals(y)) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), oneRepr);
                }
            }
        }
    }

    private static void computeLT(Set<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = Constants.OP_LT;
        relEvalBinopU.add(op, emptyRepr, emptyRepr, emptyRepr);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, emptyRepr, x.toString(), emptyRepr);
            relEvalBinopU.add(op, x.toString(), emptyRepr, emptyRepr);
            {
                int l = Interval.min_inf;
                int r = x.upper - 1;
                Set<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), zeroRepr);
                }
            }
            {
                int l = x.lower + 1;
                int r = Interval.max_inf;
                Set<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), oneRepr);
                }
            }
        }
    }

    private static void computeLE(Set<Interval> sortedITVs, ProgramRel relEvalBinopU, ProgramRel relEvalLEU) {
        String op = Constants.OP_LE;
        relEvalBinopU.add(op, emptyRepr, emptyRepr, emptyRepr);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, emptyRepr, x.toString(), emptyRepr);
            relEvalBinopU.add(op, x.toString(), emptyRepr, emptyRepr);
            {
                int l = Interval.min_inf;
                int r = x.upper;
                Set<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), zeroRepr);
                }
            }
            {
                int l = x.lower;
                int r = Interval.max_inf;
                Set<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), oneRepr);
                    relEvalLEU.add(x.toString(), y.toString());
                }
            }
        }
    }

    private static Set<Interval> filterInterval(Set<Interval> sortedITVs, int l, int r) {
        Set<Interval> res = new LinkedHashSet<>();
        for (Interval z : sortedITVs) {
            if (z.upper < l)
                continue;
            if (z.overlap(l, r))
                res.add(z);
            if (z.lower > r)
                break;
        }
        return res;
    }

    @Override
    protected String getOutDir() {
        return workPath.toAbsolutePath().toString();
    }
}
