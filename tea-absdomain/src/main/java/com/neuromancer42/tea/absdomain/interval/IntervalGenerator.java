package com.neuromancer42.tea.absdomain.interval;

import com.neuromancer42.tea.absdomain.interval.itv.Interval;
import com.neuromancer42.tea.commons.analyses.AbstractAnalysis;
import com.neuromancer42.tea.commons.analyses.annotations.*;
import com.neuromancer42.tea.commons.bddbddb.ProgramDom;
import com.neuromancer42.tea.commons.bddbddb.ProgramRel;
import com.neuromancer42.tea.commons.configs.Messages;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

@TeAAnalysis(name = "gen_interval")
public class IntervalGenerator extends AbstractAnalysis {
    public static final String name = "gen_interval";

    private static final String emptyRepr = Interval.EMPTY.toString();
    private static final String unknownRepr = Interval.UNKNOWN.toString();
    private static final String zeroRepr = Interval.ZERO.toString();
    private static final String oneRepr = Interval.ONE.toString();
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
    @ConsumeDom(description = "constants")
    public ProgramDom domC;
    @ConsumeDom(description = "types")
    public ProgramDom domT;
    @ConsumeDom(description = "heap objects")
    public ProgramDom domH;

    @ConsumeRel(name = "variable_const_int", doms = {"V", "C"})
    public ProgramRel relVconstint;
    @ConsumeRel(name = "array_type_size", doms = {"T", "C"})
    public ProgramRel relArrLen;
    @ConsumeRel(doms = {"H", "C", "T"})
    public ProgramRel relObjFixShape;
    @ConsumeRel(doms = {"H", "V", "T"})
    public ProgramRel relObjVarShape;

    @ConsumeRel(name = "operation_icmp_eq", doms = {"V", "V", "V"})
    public ProgramRel relIcmpEQ;
    @ConsumeRel(name = "operation_icmp_ne", doms = {"V", "V", "V"})
    public ProgramRel relIcmpNE;
    @ConsumeRel(name = "operation_icmp_ugt", doms = {"V", "V", "V"})
    public ProgramRel relIcmpUGT;
    @ConsumeRel(name = "operation_icmp_uge", doms = {"V", "V", "V"})
    public ProgramRel relIcmpUGE;
    @ConsumeRel(name = "operation_icmp_ult", doms = {"V", "V", "V"})
    public ProgramRel relIcmpULT;
    @ConsumeRel(name = "operation_icmp_ule", doms = {"V", "V", "V"})
    public ProgramRel relIcmpULE;
    @ConsumeRel(name = "operation_icmp_sgt", doms = {"V", "V", "V"})
    public ProgramRel relIcmpSGT;
    @ConsumeRel(name = "operation_icmp_sge", doms = {"V", "V", "V"})
    public ProgramRel relIcmpSGE;
    @ConsumeRel(name = "operation_icmp_slt", doms = {"V", "V", "V"})
    public ProgramRel relIcmpSLT;
    @ConsumeRel(name = "operation_icmp_sle", doms = {"V", "V", "V"})
    public ProgramRel relIcmpSLE;

    @ProduceDom(description = "intervals")
    public ProgramDom domU;
    @ProduceRel(doms = {"C", "U"}, description = "interval value of constants")
    public ProgramRel relConstU;
    @ProduceRel(doms = {"U"}, description = "mark special empty value")
    public ProgramRel relUempty;
    @ProduceRel(doms = {"U"})
    public ProgramRel relUzero;
    @ProduceRel(doms = {"U"})
    public ProgramRel relUone;
    @ProduceRel(doms = {"U"})
    public ProgramRel relUnonzero;
    @ProduceRel(doms = {"U"})
    public ProgramRel relUunknown;
    @ProduceRel(doms = {"U"}, description = "mark possible values of input")
    public ProgramRel relUinput;
    @ProduceRel(name = "evalAddU", doms = {"U", "U", "U"})
    public ProgramRel relEvalAddU;
    @ProduceRel(name = "evalSubU", doms = {"U", "U", "U"})
    public ProgramRel relEvalSubU;
    @ProduceRel(name = "evalMulU", doms = {"U", "U", "U"})
    public ProgramRel relEvalMulU;
    @ProduceRel(name = "evalAndU", doms = {"U", "U", "U"})
    public ProgramRel relEvalAndU;
    @ProduceRel(name = "evalOrU", doms = {"U", "U", "U"})
    public ProgramRel relEvalOrU;
    @ProduceRel(name = "evalXorU", doms = {"U", "U", "U"})
    public ProgramRel relEvalXorU;
    @ProduceRel(name = "evalEQU", doms = {"U", "U"})
    public ProgramRel relEvalEQU;
    @ProduceRel(name = "evalNEU", doms = {"U", "U"})
    public ProgramRel relEvalNEU;
    @ProduceRel(name = "evalLTU", doms = {"U", "U"})
    public ProgramRel relEvalLTU;
    @ProduceRel(name = "evalLEU", doms = {"U", "U"})
    public ProgramRel relEvalLEU;
    @ProduceRel(name = "evalGTU", doms = {"U", "U"})
    public ProgramRel relEvalGTU;
    @ProduceRel(name = "evalGEU", doms = {"U", "U"})
    public ProgramRel relEvalGEU;
    @ConsumeRel(doms = {"P", "P", "V"})
    public ProgramRel relPPtrue;
    @ProduceRel(doms = {"V"}, description = "mark unhandled cond-vars")
    public ProgramRel relPredUnknown;

    private final Map<String, Integer> literalMap = new HashMap<>();
    private final LinkedHashSet<Interval> sortedITVs = new LinkedHashSet<>();
    private final Map<String, Integer> regToLiteral = new HashMap<>();
    private final Set<String> cmpVars = new HashSet<>();

    @Override
    protected void domPhase() {
        for (String c : domC) {
            try {
                int primVal = Integer.parseInt(c);
                literalMap.put(c, primVal);
            } catch (NumberFormatException e) {
                Messages.error("IntervalGenerator: not a constant integer %s", c);
            }
        }
        for (Object[] tuple : relVconstint.getValTuples()) {
            String v = (String) tuple[0];
            String c = (String) tuple[1];
            if (literalMap.containsKey(c))
                regToLiteral.put(v, literalMap.get(c));
        }
        for (Object[] tuple : relIcmpEQ.getValTuples()) {
            String v = (String) tuple[0];
            cmpVars.add(v);
        }
        for (Object[] tuple : relIcmpNE.getValTuples()) {
            String v = (String) tuple[0];
            cmpVars.add(v);
        }
        for (Object[] tuple : relIcmpULT.getValTuples()) {
            String v = (String) tuple[0];
            cmpVars.add(v);
        }
        for (Object[] tuple : relIcmpULE.getValTuples()) {
            String v = (String) tuple[0];
            cmpVars.add(v);
        }
        for (Object[] tuple : relIcmpUGT.getValTuples()) {
            String v = (String) tuple[0];
            cmpVars.add(v);
        }
        for (Object[] tuple : relIcmpUGE.getValTuples()) {
            String v = (String) tuple[0];
            cmpVars.add(v);
        }
        for (Object[] tuple : relIcmpSLT.getValTuples()) {
            String v = (String) tuple[0];
            cmpVars.add(v);
        }
        for (Object[] tuple : relIcmpSLE.getValTuples()) {
            String v = (String) tuple[0];
            cmpVars.add(v);
        }
        for (Object[] tuple : relIcmpSGT.getValTuples()) {
            String v = (String) tuple[0];
            cmpVars.add(v);
        }
        for (Object[] tuple : relIcmpSGE.getValTuples()) {
            String v = (String) tuple[0];
            cmpVars.add(v);
        }
        Set<Integer> intConstants = new HashSet<>();
        for (Object[] tuple : relArrLen.getValTuples()) {
            Integer len = literalMap.get((String) tuple[1]);
            assert len != null;
            intConstants.add(len);
        }
        for (Object[] tuple : relObjFixShape.getValTuples()) {
            Integer len = literalMap.get((String) tuple[1]);
            assert len != null;
            intConstants.add(len);
        }
        for (Object[] tuple : relObjVarShape.getValTuples()) {
            String lenVar = (String) tuple[1];
            Integer len = regToLiteral.get(lenVar);
            if (len != null)
                intConstants.add(len);
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
        domU.add(emptyRepr);
        domU.add(unknownRepr);
        for (Interval itv : sortedITVs) {
            domU.add(itv.toString());
        }
    }

    @Override
    protected void relPhase() {
        {
            // build arithmetics
            assert domU.contains(emptyRepr);
            assert domU.contains(unknownRepr);
            assert domU.contains(zeroRepr);
            assert domU.contains(oneRepr);
            relUempty.add(emptyRepr);
            relUunknown.add(unknownRepr);
            relUzero.add(zeroRepr);
            relUone.add(oneRepr);
            for (String u : domU) {
                if (!(u.equals(emptyRepr) || u.equals(unknownRepr) || u.equals(maxinfRepr) || u.equals(mininfRepr))) {
                    relUinput.add(u);
                }
                if (!u.equals(zeroRepr)) {
                    relUnonzero.add(u);
                }
            }
            computeAdd(sortedITVs, (List<String> elems) -> relEvalAddU.add(elems.get(0), elems.get(1), elems.get(2)));
            computeSub(sortedITVs, (List<String> elems) -> relEvalSubU.add(elems.get(0), elems.get(1), elems.get(2)));
            computeMul(sortedITVs, (List<String> elems) -> relEvalMulU.add(elems.get(0), elems.get(1), elems.get(2)));
            computeAnd(sortedITVs, (List<String> elems) -> relEvalAndU.add(elems.get(0), elems.get(1), elems.get(2)));
            computeOr(sortedITVs, (List<String> elems) -> relEvalOrU.add(elems.get(0), elems.get(1), elems.get(2)));
            computeXor(sortedITVs, (List<String> elems) -> relEvalXorU.add(elems.get(0), elems.get(1), elems.get(2)));
            computeEQ(sortedITVs, (List<String> elems) -> relEvalEQU.add(elems.get(0), elems.get(1)));
            computeNE(sortedITVs, (List<String> elems) -> relEvalNEU.add(elems.get(0), elems.get(1)));
            computeLT(sortedITVs, (List<String> elems) -> relEvalLTU.add(elems.get(0), elems.get(1)));
            computeLE(sortedITVs, (List<String> elems) -> relEvalLEU.add(elems.get(0), elems.get(1)));
            computeGT(sortedITVs, (List<String> elems) -> relEvalGTU.add(elems.get(0), elems.get(1)));
            computeGE(sortedITVs, (List<String> elems) -> relEvalGEU.add(elems.get(0), elems.get(1)));

            for (String c : domC) {
                if (literalMap.containsKey(c)) {
                    int primVal = literalMap.get(c);
                    for (Interval itv : sortedITVs) {
                        if (itv.contains(primVal)) {
                            relConstU.add(c, itv.toString());
                            break;
                        }
                    }

                } else {
                    relConstU.add(c, unknownRepr);
                }
            }
        }

        {
            for (Object[] tuple : relPPtrue.getValTuples()) {
                String condV = (String) tuple[2];
                if (!cmpVars.contains(condV))
                    relPredUnknown.add(condV);
            }
        }
    }

    private static void computeAdd(LinkedHashSet<Interval> sortedITVs, Consumer<List<String>> addRel) {
        addRel.accept(List.of(emptyRepr, emptyRepr, emptyRepr));
        addRel.accept(List.of(unknownRepr, unknownRepr, unknownRepr));
        for (Interval x : sortedITVs) {
            addRel.accept(List.of(emptyRepr, x.toString(), emptyRepr));
            addRel.accept(List.of(unknownRepr, x.toString(), unknownRepr));
            addRel.accept(List.of(x.toString(), emptyRepr, emptyRepr));
            addRel.accept(List.of(x.toString(), unknownRepr, unknownRepr));
            for (Interval y : sortedITVs) {
                int l = x.lower + y.lower;
                if (x.equals(Interval.MIN_INF) || y.equals(Interval.MIN_INF)) {
                    l = Interval.min_inf;
                }
                int r = x.upper + y.upper;
                if (x.equals(Interval.MAX_INF) || y.equals(Interval.MAX_INF)) {
                    r = Interval.max_inf;
                }
                LinkedHashSet<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval z : res) {
                    addRel.accept(List.of(x.toString(), y.toString(), z.toString()));
                }
            }
        }
    }

    private static void computeSub(LinkedHashSet<Interval> sortedITVs, Consumer<List<String>> subRel) {
        subRel.accept(List.of(emptyRepr, emptyRepr, emptyRepr));
        subRel.accept(List.of(unknownRepr, unknownRepr, unknownRepr));
        for (Interval x : sortedITVs) {
            subRel.accept(List.of(emptyRepr, x.toString(), emptyRepr));
            subRel.accept(List.of(x.toString(), emptyRepr, emptyRepr));
            subRel.accept(List.of(unknownRepr, x.toString(), unknownRepr));
            subRel.accept(List.of(x.toString(), unknownRepr, unknownRepr));
            for (Interval y : sortedITVs) {
                int l = x.lower - y.upper;
                if (x.equals(Interval.MIN_INF) || y.equals(Interval.MAX_INF)) {
                    l = Interval.min_inf;
                }
                int r = x.upper - y.lower;
                if (x.equals(Interval.MAX_INF) || y.equals(Interval.MIN_INF)) {
                    r = Interval.max_inf;
                }
                LinkedHashSet<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval z : res) {
                    subRel.accept(List.of(x.toString(), y.toString(), z.toString()));
                }
            }
        }
    }

    private static void computeMul(LinkedHashSet<Interval> sortedITVs, Consumer<List<String>> mulRel) {
        mulRel.accept(List.of(emptyRepr, emptyRepr, emptyRepr));
        mulRel.accept(List.of(unknownRepr, unknownRepr, unknownRepr));
        for (Interval x : sortedITVs) {
            mulRel.accept(List.of(emptyRepr, x.toString(), emptyRepr));
            mulRel.accept(List.of(unknownRepr, x.toString(), unknownRepr));
            mulRel.accept(List.of(x.toString(), emptyRepr, emptyRepr));
            mulRel.accept(List.of(x.toString(), unknownRepr, unknownRepr));
            for (Interval y : sortedITVs) {
                int p1 =  x.lower * y.lower,
                        p2 = x.lower * y.upper,
                        p3 = x.upper * y.lower,
                        p4 = x.upper * y.upper;
                int l = Collections.min(Arrays.asList(p1, p2 ,p3, p4));
                int r = Collections.max(Arrays.asList(p1, p2, p3, p4));
                LinkedHashSet<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval z : res) {
                    mulRel.accept(List.of(x.toString(), y.toString(), z.toString()));
                }
            }
        }
    }

    private static void computeAnd(LinkedHashSet<Interval> sortedITVs, Consumer<List<String>> andRel) {
        andRel.accept(List.of(emptyRepr, emptyRepr, emptyRepr));
        andRel.accept(List.of(unknownRepr, unknownRepr, unknownRepr));
        for (Interval x : sortedITVs) {
            for (Interval y : sortedITVs) {
                String xRepr = x.toString();
                String yRepr = y.toString();
                if (xRepr.equals(oneRepr) && yRepr.equals(oneRepr))
                    andRel.accept(List.of(xRepr, yRepr, oneRepr));
                else if (xRepr.equals(oneRepr) && yRepr.equals(zeroRepr))
                    andRel.accept(List.of(xRepr, yRepr, zeroRepr));
                else if (xRepr.equals(zeroRepr) && yRepr.equals(oneRepr))
                    andRel.accept(List.of(xRepr, yRepr, zeroRepr));
                else if (xRepr.equals(zeroRepr) && yRepr.equals(zeroRepr))
                    andRel.accept(List.of(xRepr, yRepr, zeroRepr));
                else
                    andRel.accept(List.of(xRepr, yRepr, unknownRepr));
            }
        }
    }

    private static void computeOr(LinkedHashSet<Interval> sortedITVs, Consumer<List<String>> orRel) {
        orRel.accept(List.of(emptyRepr, emptyRepr, emptyRepr));
        orRel.accept(List.of(unknownRepr, unknownRepr, unknownRepr));
        for (Interval x : sortedITVs) {
            for (Interval y : sortedITVs) {
                String xRepr = x.toString();
                String yRepr = y.toString();
                if (xRepr.equals(oneRepr) && yRepr.equals(oneRepr))
                    orRel.accept(List.of(xRepr, yRepr, oneRepr));
                else if (xRepr.equals(oneRepr) && yRepr.equals(zeroRepr))
                    orRel.accept(List.of(xRepr, yRepr, oneRepr));
                else if (xRepr.equals(zeroRepr) && yRepr.equals(oneRepr))
                    orRel.accept(List.of(xRepr, yRepr, oneRepr));
                else if (xRepr.equals(zeroRepr) && yRepr.equals(zeroRepr))
                    orRel.accept(List.of(xRepr, yRepr, zeroRepr));
                else
                    orRel.accept(List.of(xRepr, yRepr, unknownRepr));
            }
        }
    }

    private static void computeXor(LinkedHashSet<Interval> sortedITVs, Consumer<List<String>> xorRel) {
        xorRel.accept(List.of(emptyRepr, emptyRepr, emptyRepr));
        xorRel.accept(List.of(unknownRepr, unknownRepr, unknownRepr));
        for (Interval x : sortedITVs) {
            for (Interval y : sortedITVs) {
                String xRepr = x.toString();
                String yRepr = y.toString();
                if (xRepr.equals(oneRepr) && yRepr.equals(oneRepr))
                    xorRel.accept(List.of(xRepr, yRepr, zeroRepr));
                else if (xRepr.equals(oneRepr) && yRepr.equals(zeroRepr))
                    xorRel.accept(List.of(xRepr, yRepr, oneRepr));
                else if (xRepr.equals(zeroRepr) && yRepr.equals(oneRepr))
                    xorRel.accept(List.of(xRepr, yRepr, oneRepr));
                else if (xRepr.equals(zeroRepr) && yRepr.equals(zeroRepr))
                    xorRel.accept(List.of(xRepr, yRepr, zeroRepr));
                else
                    xorRel.accept(List.of(xRepr, yRepr, unknownRepr));
            }
        }
    }

    private static void computeEQ(LinkedHashSet<Interval> sortedITVs, Consumer<List<String>> eqRel) {
        eqRel.accept(List.of(emptyRepr, emptyRepr));
        eqRel.accept(List.of(unknownRepr, unknownRepr));
        for (Interval x : sortedITVs) {
            eqRel.accept(List.of(emptyRepr, x.toString()));
            eqRel.accept(List.of(x.toString(), emptyRepr));
            eqRel.accept(List.of(unknownRepr, x.toString()));
            eqRel.accept(List.of(x.toString(), unknownRepr));
            eqRel.accept(List.of(x.toString(), x.toString()));
        }
    }

    private static void computeNE(LinkedHashSet<Interval> sortedITVs, Consumer<List<String>> neRel) {
        neRel.accept(List.of(emptyRepr, emptyRepr));
        neRel.accept(List.of(unknownRepr, unknownRepr));
        for (Interval x : sortedITVs) {
            neRel.accept(List.of(emptyRepr, x.toString()));
            neRel.accept(List.of(x.toString(), emptyRepr));
            neRel.accept(List.of(unknownRepr, x.toString()));
            neRel.accept(List.of(x.toString(), unknownRepr));
            if (x.equals(Interval.MIN_INF) || x.equals(Interval.MAX_INF) || x.lower < x.upper) {
                neRel.accept(List.of(x.toString(), x.toString()));
            }
            for (Interval y : sortedITVs) {
                if (!x.equals(y)) {
                    neRel.accept(List.of(x.toString(), y.toString()));
                }
            }
        }
    }

    private static void computeLT(LinkedHashSet<Interval> sortedITVs, Consumer<List<String>> ltRel) {
        ltRel.accept(List.of(emptyRepr, emptyRepr));
        ltRel.accept(List.of(unknownRepr, unknownRepr));
        ltRel.accept(List.of(unknownRepr, unknownRepr));
        for (Interval x : sortedITVs) {
            ltRel.accept(List.of(emptyRepr, x.toString()));
            ltRel.accept(List.of(x.toString(), emptyRepr));
            ltRel.accept(List.of(unknownRepr, x.toString()));
            ltRel.accept(List.of(unknownRepr, x.toString()));
            ltRel.accept(List.of(x.toString(), unknownRepr));
            ltRel.accept(List.of(x.toString(), unknownRepr));
            {
                int l = x.lower + 1;
                int r = Interval.max_inf;
                LinkedHashSet<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    ltRel.accept(List.of(x.toString(), y.toString()));
                }
            }
        }
    }

    private static void computeLE(LinkedHashSet<Interval> sortedITVs, Consumer<List<String>> leRel) {
        leRel.accept(List.of(emptyRepr, emptyRepr));
        leRel.accept(List.of(unknownRepr, unknownRepr));
        for (Interval x : sortedITVs) {
            leRel.accept(List.of(emptyRepr, x.toString()));
            leRel.accept(List.of(x.toString(), emptyRepr));
            leRel.accept(List.of(unknownRepr, x.toString()));
            leRel.accept(List.of(x.toString(), unknownRepr));
            {
                int l = x.lower;
                int r = Interval.max_inf;
                LinkedHashSet<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    leRel.accept(List.of(x.toString(), y.toString()));
                }
            }
        }
    }

    private static void computeGT(LinkedHashSet<Interval> sortedITVs, Consumer<List<String>> gtRel) {
        gtRel.accept(List.of(emptyRepr, emptyRepr));
        gtRel.accept(List.of(unknownRepr, unknownRepr));
        for (Interval x : sortedITVs) {
            gtRel.accept(List.of(emptyRepr, x.toString()));
            gtRel.accept(List.of(x.toString(), emptyRepr));
            gtRel.accept(List.of(unknownRepr, x.toString()));
            gtRel.accept(List.of(x.toString(), unknownRepr));
            {
                int l = Interval.min_inf;
                int r = x.upper - 1;
                LinkedHashSet<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    gtRel.accept(List.of(x.toString(), y.toString()));
                }
            }
        }
    }

    private static void computeGE(LinkedHashSet<Interval> sortedITVs, Consumer<List<String>> geRel) {
        geRel.accept(List.of(emptyRepr, emptyRepr));
        geRel.accept(List.of(unknownRepr, unknownRepr));
        geRel.accept(List.of(unknownRepr, unknownRepr));
        for (Interval x : sortedITVs) {
            geRel.accept(List.of(emptyRepr, x.toString()));
            geRel.accept(List.of(x.toString(), emptyRepr));
            geRel.accept(List.of(unknownRepr, x.toString()));
            geRel.accept(List.of(unknownRepr, x.toString()));
            geRel.accept(List.of(x.toString(), unknownRepr));
            geRel.accept(List.of(x.toString(), unknownRepr));
            {
                int l = Interval.min_inf;
                int r = x.upper;
                LinkedHashSet<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    geRel.accept(List.of(x.toString(), y.toString()));
                }
            }
        }
    }

    private static LinkedHashSet<Interval> filterInterval(LinkedHashSet<Interval> sortedITVs, int l, int r) {
        LinkedHashSet<Interval> res = new LinkedHashSet<>();
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
