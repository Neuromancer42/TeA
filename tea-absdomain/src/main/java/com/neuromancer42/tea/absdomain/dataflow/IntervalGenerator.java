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
import com.neuromancer42.tea.commons.util.IndexSet;
import org.neuromancer42.tea.ir.Expr;

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
    @ConsumeRel(doms = {"V", "V"})
    public ProgramRel relLoadPtr;

    @ProduceRel(name = "primV", doms = {"V"}, description = "mark numeric variables")
    public ProgramRel relPrimV;
    @ProduceRel(name = "primH", doms = {"H"}, description = "mark numeric heap objects, useful for finding indirect inputs")
    public ProgramRel relPrimH;

    @ConsumeRel(doms = {"P", "V", "E"})
    public ProgramRel relPeval;
    @ProduceDom(description = "operators")
    public ProgramDom domOP;
    @ProduceDom(description = "intervals")
    public ProgramDom domU;
    @ProduceDom(description = "interval predicates")
    public ProgramDom domUP;
    @ProduceRel(doms = {"E"}, description = "mark constant expressions")
    public ProgramRel relEconst;
    @ProduceRel(doms = {"E", "OP", "V"}, description = "mark unary expressions")
    public ProgramRel relEunary;
    @ProduceRel(doms = {"E", "OP", "V", "V"}, description = "mark binary expressions")
    public ProgramRel relEbinop;
    @ProduceRel(doms = {"E", "U"}, description = "interval value of constant expressions")
    public ProgramRel relEConstU;
    @ProduceRel(name = "evalUnaryU", doms = {"OP", "U", "U"}, description = "unary computations of intervals")
    public ProgramRel relEvalUnaryU;
    @ProduceRel(name = "evalBinopU", doms = {"OP", "U", "U", "U"}, description = "binary computations of intervals")
    public ProgramRel relEvalBinopU;
    @ProduceRel(doms = {"U"}, description = "mark special empty value")
    public ProgramRel relUempty;
    @ProduceRel(doms = {"U"}, description = "mark possible values of input")
    public ProgramRel relUinput;

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

    @ConsumeDom(description = "array size")
    public ProgramDom domSZ;
    @ConsumeRel(doms = {"H", "SZ", "H"})
    public ProgramRel relHeapAllocArr;
    @ConsumeRel(doms = {"H", "SZ"})
    public ProgramRel relHeapArraySize;
    @ProduceRel(doms = {"SZ", "U", "SZ"})
    public ProgramRel relMayOutOfBound;

    private final IndexSet<Interval> sortedITVs = new IndexSet<>();
    private final Map<String, Expr.Expression> regToEval = new LinkedHashMap<>();
    private final Map<String, Integer> regToLiteral = new LinkedHashMap<>();
    private final Set<String> primitiveVars = new LinkedHashSet<>();
    private final Set<String> primitiveObjs = new LinkedHashSet<>();
    private Map<String, String> regToHeap = new LinkedHashMap<>();

    @Override
    protected void domPhase() {
        for (ItvPredicate itvp : ItvPredicate.allPredicates())
            domUP.add(itvp.toString());
        Set<Integer> intConstants = new LinkedHashSet<>();
        for (Object[] tuple : relPeval.getValTuples()) {
            String reg = (String) tuple[1];
            String exprStr = (String) tuple[2];

            Expr.Expression expr = null;
            try {
                expr = TextFormat.parse(exprStr, Expr.Expression.class);
            } catch (TextFormat.ParseException e) {
                Messages.error("IntervalGenerator: expression string '%s' cannot be parsed", exprStr);
                Messages.fatal(e);
                assert false;
            }

            regToEval.put(reg, expr);
            String type = expr.getType();
            if (expr.hasLiteral()) {
                String constRepr = expr.getLiteral().getLiteral();
                if (type.equals(Constants.TYPE_INT)) {
                    primitiveVars.add(reg);
                    try {
                        int primVal = Integer.parseInt(constRepr);
                        regToLiteral.put(reg, primVal);
                        Messages.debug("IntervalGenerator: find new constant integer %d", primVal);
                        intConstants.add(primVal);
                        intConstants.add(-primVal);
                    } catch (NumberFormatException e) {
                        Messages.error("IntervalGenerator: not a constant integer %s", constRepr);
                    }
                } else {
                    Messages.error("IntervalGenerator: [TODO] unhandled constant value %s[%s]", type, constRepr);
                }
            } else if (expr.hasUnary()) {
                //TODO separate numerics and pointers?
                primitiveVars.add(reg);
                String op = expr.getUnary().getOperator();
                domOP.add(op);
            } else if (expr.hasBinary()) {
                primitiveVars.add(reg);
                String op = expr.getBinary().getOperator();
                domOP.add(op);
            }
        }
        for (String sz : domSZ) {
            Interval itv = sz2Itv(sz);
            Messages.debug("IntervalGenerator: find constant array bound %d~%d", itv.lower, itv.upper);
            intConstants.add(itv.lower);
            intConstants.add(-itv.lower);
            intConstants.add(itv.upper);
            intConstants.add(-itv.upper);
        }

        intConstants.add(0);
        intConstants.add(1);
        intConstants.add(-1);
        intConstants.add(Interval.min_bound);
        intConstants.add(Interval.max_bound);
        List<Integer> boundList = new ArrayList<>(intConstants);
        Collections.sort(boundList);

        sortedITVs.add(Interval.MIN_INF);
        sortedITVs.add(new Interval(Integer.MIN_VALUE, boundList.get(0) - 1));
        for (int i = 0; i < boundList.size(); ++i) {
            sortedITVs.add(new Interval(boundList.get(i)));
            int l = boundList.get(i) + 1;
            int r = (i + 1 == boundList.size()) ? Integer.MAX_VALUE : (boundList.get(i + 1) - 1);
            sortedITVs.add(new Interval(l, r));
        }
        sortedITVs.add(Interval.MAX_INF);
        domU.add(Interval.EMPTY.toString());
        for (Interval itv : sortedITVs) {
            domU.add(itv.toString());
        }
    }

    @Override
    protected void relPhase() {
        {
            // mark heaps
            regToHeap = buildVal2HeapMap();
            for (String v : primitiveVars)
                relPrimV.add(v);
            for (String h : primitiveObjs)
                relPrimH.add(h);
        }
        {
            // build arithmetics
            assert domU.contains(emptyRepr);
            assert domU.contains(zeroRepr);
            assert domU.contains(oneRepr);
            relUempty.add(emptyRepr);
            for (String u : domU) {
                if (!(u.equals(emptyRepr) || u.equals(maxinfRepr) || u.equals(mininfRepr))) {
                    relUinput.add(u);
                }
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
                            List<Interval> res = filterInterval(sortedITVs, l, r);
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
                        computePlus(sortedITVs, relEvalBinopU);
                        break;
                    case Constants.OP_SUB:
                        computeMinus(sortedITVs, relEvalBinopU);
                        break;
                    case Constants.OP_MUL:
                        computeMultiply(sortedITVs, relEvalBinopU);
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
                        computeLE(sortedITVs, relEvalBinopU);
                        break;
                    default:
                        for (String x : domU) {
                            relEvalUnaryU.add(op, x, emptyRepr);
                            for (String y : domU)
                                relEvalBinopU.add(op, x, y, emptyRepr);
                        }
                }
            }
            for (var entry : regToEval.entrySet()) {
                String reg = entry.getKey();
                Expr.Expression expr = entry.getValue();
                String exprStr = TextFormat.shortDebugString(expr);
                if (expr.hasLiteral()) {
                    relEconst.add(exprStr);
                    Integer literal = regToLiteral.get(reg);
                    if (literal != null) {
                        relEConstU.add(exprStr, new Interval(literal).toString());
                    } else {
                        relEConstU.add(exprStr, emptyRepr);
                    }
                } else if (expr.hasUnary()) {
                    Expr.UnaryExpr uEval = expr.getUnary();
                    String op = uEval.getOperator();
                    String inner = uEval.getOprand();
                    relEunary.add(exprStr, op, inner);
                } else if (expr.hasBinary()) {
                    Expr.BinaryExpr bEval = expr.getBinary();
                    String op = bEval.getOperator();
                    String l = bEval.getOprand1();
                    String r = bEval.getOprand2();
                    relEbinop.add(exprStr, op, l, r);
                } else {
                    Messages.warn("IntervalGenerator: mark unhandled eval as EMPTY [%s]", exprStr);
                    relEconst.add(exprStr);
                    relEConstU.add(exprStr, emptyRepr);
                }
            }
            {
                // mark array objects
                Map<String, Map<String, String>> contentMap = new LinkedHashMap<>();
                for (Object[] tuple : relHeapAllocArr.getValTuples()) {
                    String arrObj = (String) tuple[0];
                    String pos = (String) tuple[1];
                    String contentObj = (String) tuple[2];
                    contentMap.computeIfAbsent(arrObj, k -> new LinkedHashMap<>()).put(pos, contentObj);
                }
                for (Object[] tuple : relHeapArraySize.getValTuples()) {
                    String arrObj = (String) tuple[0];
                    String sz = (String) tuple[1];
                    for (var entry: contentMap.get(arrObj).entrySet()) {
                        String pos = entry.getKey();
                        for (Interval u : sortedITVs) {
                            if (mayOutOfBound(pos, u, sz)) {
                                relMayOutOfBound.add(pos, u.toString(), sz);
                            }
                        }
                    }
                }
            }
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
                String condv = (String) tuple[2];

                // strip out outermost negations
                boolean negated = false;
                Expr.Expression expr = regToEval.get(condv);
                while (expr.hasUnary() && expr.getUnary().getOprand().equals(Constants.OP_NOT)) {
                    condv = expr.getUnary().getOprand();
                    expr = regToEval.get(condv);
                    negated = !negated;
                }

                // TODO: add handling of pointer's NULL-check, e.g. if (ptr) a = *ptr;
                if (regToLiteral.containsKey(condv)) {
                    int l1 = regToLiteral.get(condv);
                    u1 = new Interval(l1);
                    u2 = new Interval(0);
                } else if (regToHeap.containsKey(condv)) {
                    h1 = regToHeap.get(condv);
                    u2 = new Interval(0);
                    pred = new ItvPredicate(Constants.OP_NE, negated);
                } else if (expr.hasBinary()) {
                    pred = new ItvPredicate(expr.getBinary().getOperator(), negated);
                    String v1 = expr.getBinary().getOprand1();
                    Integer l1 = regToLiteral.get(v1);
                    if (l1 != null) u1 = new Interval(l1);
                    h1 = regToHeap.get(v1);
                    String v2 = expr.getBinary().getOprand2();
                    Integer l2 = regToLiteral.get(v2);
                    if (l2 != null) u2 = new Interval(l2);
                    h2 = regToHeap.get(v2);
                }
                assert ((u1 == null || h1 == null) && (u2 == null || h2 == null));
                if (pred == null) {
                    relPredUnknown.add(condv);
                } else {
                    if (h1 != null && h2 != null) {
                        for (String h : domH) {
                            if (!h.equals(h1) && !h.equals(h2))
                                relNoFilter.add(condv, h);
                        }
                        relPred2.add(condv, pred.toString(), h1, h2);
                    } else if (h1 != null && u2 != null) {
                        for (String h : domH) {
                            if (!h.equals(h1))
                                relNoFilter.add(condv, h);
                        }
                        relPredL.add(condv, pred.toString(), h1, u2.toString());
                    } else if (u1 != null && h2 != null) {
                        for (String h : domH) {
                            if (!h.equals(h2))
                                relNoFilter.add(condv, h);
                        }
                        relPredR.add(condv, pred.toString(), u1.toString(), h2);
                    } else if (u1 != null && u2 != null) {
                        Messages.error("Interval: cond-val @%d is constant [%d]", condv, regToLiteral.get(condv));
                        relPredUnknown.add(condv);
                    } else {
                        relPredUnknown.add(condv);
                    }
                }
            }
        }
    }

    private static Interval sz2Itv(String sz) {
        if (sz.contains("~")) {
            String ls = sz.substring(0, sz.indexOf("~"));
            String rs = sz.substring(sz.indexOf("~") + 1);

            // Note: l cannot be unknown
            int l = Integer.parseInt(ls);
            int r;
            if (!rs.equals("unknown"))
                r = Integer.parseInt(rs);
            else
                r = Interval.max_bound;
            return new Interval(l, r);
        } else {
            if (!sz.equals("unknown")) {
                int len = Integer.parseInt(sz);
                return new Interval(len);
            } else {
                return new Interval(0, Interval.max_bound);
            }
        }
    }

    private static boolean mayOutOfBound(String pos, Interval u, String sz) {
        Interval posItv = sz2Itv(pos);
        Interval szItv = sz2Itv(sz);
        assert posItv.lower <= posItv.upper;
        assert szItv.lower <= posItv.upper;
        assert u.lower <= u.upper;
        Interval newPosItv = new Interval(posItv.lower + u.lower, posItv.upper + u.upper);
        return newPosItv.mayGE(szItv) || newPosItv.mayLT(new Interval(0));
    }

    private Map<String, String> buildVal2HeapMap() {
        Map<String, Set<String>> pt = new HashMap<>();
        for (Object[] tuple : relCIPT.getValTuples()) {
            String v = (String) tuple[0];
            String h = (String) tuple[1];
            pt.computeIfAbsent(v, k -> new HashSet<>()).add(h);
        }
        Map<String, String> val2heap = new HashMap<>();
        for (Object[] tuple : relLoadPtr.getValTuples()) {
            String u = (String) tuple[0];
            if (primitiveVars.contains(u)) {
                String ptr = (String) tuple[1];
                Set<String> objs = pt.get(ptr);
                if (objs != null && objs.size() == 1) {
                    for (String obj : objs) {
                        val2heap.put(u, obj);
                        primitiveObjs.add(obj);
                    }
                }
            }
        }
        return val2heap;
    }

    private static void computeIncr(IndexSet<Interval> sortedITVs, ProgramRel relEvalUnaryU) {
        String op = Constants.OP_INCR;
        relEvalUnaryU.add(op, emptyRepr, emptyRepr);
        relEvalUnaryU.add(op, mininfRepr, mininfRepr);
        for (Interval x : sortedITVs) {
            int l = x.lower + 1;
            int r = x.upper + 1;
            List<Interval> res = filterInterval(sortedITVs, l, r);
            for (Interval z : res)
                relEvalUnaryU.add(op, x.toString(), z.toString());
        }
    }

    private static void computeDecr(IndexSet<Interval> sortedITVs, ProgramRel relEvalUnaryU) {
        String op = Constants.OP_DECR;
        relEvalUnaryU.add(op, emptyRepr, emptyRepr);
        relEvalUnaryU.add(op, maxinfRepr, maxinfRepr);
        for (Interval x : sortedITVs) {
            int l = x.lower - 1;
            int r = x.upper - 1;
            List<Interval> res = filterInterval(sortedITVs, l, r);
            for (Interval z : res)
                relEvalUnaryU.add(op, x.toString(), z.toString());
        }
    }

    private static void computeNot(IndexSet<Interval> sortedITVs, ProgramRel relEvalUnaryU) {
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

    private static void computeAnd(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
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

    private static void computeOr(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
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

    private static void computePlus(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = Constants.OP_ADD;
        relEvalBinopU.add(op, emptyRepr, emptyRepr, emptyRepr);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, emptyRepr, x.toString(), emptyRepr);
            relEvalBinopU.add(op, x.toString(), emptyRepr, emptyRepr);
            for (Interval y : sortedITVs) {
                int l = x.lower + y.lower;
                if (x.equals(Interval.MIN_INF) || y.equals(Interval.MIN_INF)) {
                    l = Interval.min_inf;
                }
                int r = x.upper + y.upper;
                if (x.equals(Interval.MAX_INF) || y.equals(Interval.MAX_INF)) {
                    r = Interval.max_inf;
                }
                List<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval z : res) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), z.toString());
                }
            }
        }
    }

    private static void computeMinus(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
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
                List<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval z : res) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), z.toString());
                }
            }
        }
    }

    private static void computeMultiply(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = Constants.OP_MUL;
        relEvalBinopU.add(op, emptyRepr, emptyRepr, emptyRepr);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, emptyRepr, x.toString(), emptyRepr);
            relEvalBinopU.add(op, x.toString(), emptyRepr, emptyRepr);
            for (Interval y : sortedITVs) {
                int p1 =  x.lower * y.lower,
                        p2 = x.lower * y.upper,
                        p3 = x.upper * y.lower,
                        p4 = x.upper * y.upper;
                int l = Collections.min(Arrays.asList(p1, p2 ,p3, p4));
                int r = Collections.max(Arrays.asList(p1, p2, p3, p4));
                List<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval z : res) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), z.toString());
                }
            }
        }
    }

    private static void computeEQ(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
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

    private static void computeNE(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
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

    private static void computeLT(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = Constants.OP_LT;
        relEvalBinopU.add(op, emptyRepr, emptyRepr, emptyRepr);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, emptyRepr, x.toString(), emptyRepr);
            relEvalBinopU.add(op, x.toString(), emptyRepr, emptyRepr);
            {
                int l = Interval.min_inf;
                int r = x.upper - 1;
                List<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), zeroRepr);
                }
            }
            {
                int l = x.lower + 1;
                int r = Interval.max_inf;
                List<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), oneRepr);
                }
            }
        }
    }

    private static void computeLE(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = Constants.OP_LE;
        relEvalBinopU.add(op, emptyRepr, emptyRepr, emptyRepr);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, emptyRepr, x.toString(), emptyRepr);
            relEvalBinopU.add(op, x.toString(), emptyRepr, emptyRepr);
            {
                int l = Interval.min_inf;
                int r = x.upper;
                List<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), zeroRepr);
                }
            }
            {
                int l = x.lower;
                int r = Interval.max_inf;
                List<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    relEvalBinopU.add(op, x.toString(), y.toString(), oneRepr);
                }
            }
        }
    }

    private static List<Interval> filterInterval(IndexSet<Interval> sortedITVs, int l, int r) {
        List<Interval> res = new ArrayList<>();
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
