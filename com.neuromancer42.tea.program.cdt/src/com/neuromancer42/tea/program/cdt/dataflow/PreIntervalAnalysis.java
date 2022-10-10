package com.neuromancer42.tea.program.cdt.dataflow;

import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.util.IndexSet;
import com.neuromancer42.tea.program.cdt.parser.evaluation.BinaryEval;
import com.neuromancer42.tea.program.cdt.parser.evaluation.ConstantEval;
import com.neuromancer42.tea.program.cdt.parser.evaluation.IEval;
import com.neuromancer42.tea.program.cdt.parser.evaluation.UnaryEval;
import com.neuromancer42.tea.program.cdt.memmodel.object.IMemObj;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.internal.core.dom.parser.c.CBasicType;

import java.util.*;

public class PreIntervalAnalysis extends JavaAnalysis {
    private final static Interval empty = Interval.EMPTY;
    private final static Interval zero = new Interval(0);
    private final static Interval one = new Interval(1);
    public static final String analysisName = "preInterval";

    public PreIntervalAnalysis() {
        this.name = analysisName;
        createDomConsumer("V", Integer.class);
        createDomConsumer("H", IMemObj.class);
        createDomConsumer("E", IEval.class);

        createDomProducer("OP", String.class);
        createDomProducer("U", Interval.class);
        createDomProducer("UP", ItvPredicate.class);

        createRelConsumer("Peval", "P", "V", "E");
        createRelConsumer("PPtrue", "P", "P", "V");
        createRelConsumer("PPfalse", "P", "P", "V");
        createRelConsumer("LoadPtr", "V", "V");
        createRelConsumer("ci_pt", "V", "H");

        createRelProducer("Econst", "E");
        createRelProducer("Eunary", "E", "OP", "V");
        createRelProducer("Ebinop", "E", "OP", "V", "V");
        createRelProducer("EConstU", "E", "U");
        createRelProducer("evalUnaryU", "OP", "U", "U");
        createRelProducer("evalBinopU", "OP", "U", "U", "U");
        createRelProducer("Uempty", "U");
        createRelProducer("Uinput", "U");

        createRelProducer("PredUnknown", "V");
        createRelProducer("PredL", "V", "UP", "H", "U");
        createRelProducer("PredR", "V", "UP", "U", "H");
        createRelProducer("Pred2", "V", "UP", "H", "H");
        createRelProducer("MaySat", "UP", "U", "U");
        createRelProducer("MayUnsat", "UP", "U", "U");
        createRelProducer("nofilter", "V", "H");
    }

    @Override
    public void run() {
        ProgramRel relLoadPtr = consume("LoadPtr");
        ProgramRel relCIPT = consume("ci_pt");
        relLoadPtr.load();
        relCIPT.load();
        Map<Integer, IMemObj> regToHeap = buildVal2HeapMap(relLoadPtr, relCIPT);
        relLoadPtr.close();
        relCIPT.close();

        ProgramRel relPeval = consume("Peval");
        relPeval.load();
        Map<Integer, Integer> regToLiteral = new HashMap<>();
        Map<Integer, IEval> regToEval = new LinkedHashMap<>();

        ProgramDom<String> domOP = ProgramDom.createDom("OP", String.class);
        ProgramDom<Interval> domU = ProgramDom.createDom("U", Interval.class);
        ProgramDom<ItvPredicate> domUP = ProgramDom.createDom("UP", ItvPredicate.class);
        domUP.addAll(ItvPredicate.allPredicates());
        ProgramDom<?>[] genDoms = new ProgramDom[]{domOP, domU, domUP};
        for (ProgramDom<?> dom : genDoms) {
            dom.init();
        }
        Set<Integer> intConstants = new HashSet<>();
        for (Object[] tuple : relPeval.getValTuples()) {
            Integer reg = (Integer) tuple[1];
            IEval eval = (IEval) tuple[2];
            regToEval.put(reg, eval);
            if (eval instanceof ConstantEval) {
                IType type = eval.getType();
                String constRepr = ((ConstantEval) eval).getValue();
                if (type.isSameType(CBasicType.INT)) {
                    try {
                        int primVal = Integer.parseInt(constRepr);
                        regToLiteral.put(reg, primVal);
                        Messages.debug("PreInterval: find new constant integer %d", primVal);
                        intConstants.add(primVal);
                        intConstants.add(-primVal);
                    } catch (NumberFormatException e) {
                        Messages.error("PreInterval: not a constant integer %s", constRepr);
                    }
                } else {
                    Messages.error("PreInterval: [TODO] unhandled constant value %s[%s]", type, constRepr);
                }
            } else if (eval instanceof UnaryEval) {
                String op = ((UnaryEval) eval).getOperator();
                domOP.add(op);
            } else if (eval instanceof BinaryEval) {
                String op = ((BinaryEval) eval).getOperator();
                domOP.add(op);
            }
        }
        relPeval.close();

        intConstants.add(0);
        intConstants.add(1);
        intConstants.add(-1);
        intConstants.add(Interval.min_bound);
        intConstants.add(Interval.max_bound);
        List<Integer> boundList = new ArrayList<>(intConstants);
        Collections.sort(boundList);

        IndexSet<Interval> sortedITVs = new IndexSet<>();
        sortedITVs.add(Interval.MIN_INF);
        sortedITVs.add(new Interval(Integer.MIN_VALUE, boundList.get(0) - 1));
        for (int i = 0; i < boundList.size(); ++i) {
            sortedITVs.add(new Interval(boundList.get(i)));
            int l = boundList.get(i) + 1;
            int r = (i + 1 == boundList.size()) ? Integer.MAX_VALUE : (boundList.get(i + 1) - 1);
            sortedITVs.add(new Interval(l, r));
        }
        sortedITVs.add(Interval.MAX_INF);
        domU.add(Interval.EMPTY);
        for (Interval itv : sortedITVs) {
            domU.add(itv);
        }
        for (ProgramDom<?> dom : genDoms) {
            dom.save();
        }

        ProgramDom<Integer> domV = consume("V");
        ProgramDom<IMemObj> domH = consume("H");
        ProgramDom<IEval> domE = consume("E");
        ProgramRel relEConst = new ProgramRel("Econst", domE);
        ProgramRel relEUnary = new ProgramRel("Eunary", domE, domOP, domV);
        ProgramRel relEBinop = new ProgramRel("Ebinop", domE, domOP, domV, domV);
        ProgramRel relEConstU = new ProgramRel("EConstU", domE, domU);
        ProgramRel relEvalUnaryU = new ProgramRel("evalUnaryU", domOP, domU, domU);
        ProgramRel relEvalBinopU = new ProgramRel("evalBinopU", domOP, domU, domU, domU);
        ProgramRel relUempty = new ProgramRel("Uempty", domU);
        ProgramRel relUinput = new ProgramRel("Uinput", domU);
        ProgramRel relPredUnknown = new ProgramRel("PredUnknown", domV);
        ProgramRel relPredL = new ProgramRel("PredL", domV, domUP, domH, domU);
        ProgramRel relPredR = new ProgramRel("PredR", domV, domUP, domU, domH);
        ProgramRel relPred2 = new ProgramRel("Pred2", domV, domUP, domH, domH);
        ProgramRel relMaySat = new ProgramRel("MaySat", domUP, domU, domU);
        ProgramRel relMayUnsat = new ProgramRel("MayUnsat", domUP, domU, domU);
        ProgramRel relNofilter = new ProgramRel("nofilter", domV, domH);
        // TODO: lift generated rels as private member of JavaAnalysis
        ProgramRel[] genRels = new ProgramRel[]{relEConst, relEUnary, relEBinop, relEConstU, relEvalUnaryU, relEvalBinopU, relUempty, relUinput, relPredUnknown, relPredL, relPredR, relPred2, relMaySat, relMayUnsat, relNofilter};
        for (ProgramRel rel : genRels) {
            rel.init();
        }

        {
            // build arithmetics
            assert domU.contains(empty);
            assert domU.contains(zero);
            assert domU.contains(one);
            relUempty.add(empty);
            for (Interval u : domU) {
                if (!(u.equals(empty) || u.equals(Interval.MAX_INF) || u.equals(Interval.MIN_INF))) {
                    relUinput.add(u);
                }
            }
            for (String op : domOP) {
                switch (op) {
                    case UnaryEval.op_plus:
                        for (Interval x : domU)
                            relEvalUnaryU.add(op, x, x);
                        break;
                    case UnaryEval.op_minus:
                        relEvalUnaryU.add(op, empty, empty);
                        for (Interval x : sortedITVs) {
                            int l = -x.upper;
                            int r = -x.lower;
                            List<Interval> res = filterInterval(sortedITVs, l, r);
                            for (Interval z : res)
                                relEvalUnaryU.add(op, x, z);
                        }
                        break;
                    case UnaryEval.op_incr:
                        computeIncr(sortedITVs, relEvalUnaryU);
                        break;
                    case UnaryEval.op_decr:
                        computeDecr(sortedITVs, relEvalUnaryU);
                        break;
                    case UnaryEval.op_not:
                        computeNot(sortedITVs, relEvalUnaryU);
                        break;
                    case BinaryEval.op_and:
                        computeAnd(sortedITVs, relEvalBinopU);
                        break;
                    case BinaryEval.op_or:
                        computeOr(sortedITVs, relEvalBinopU);
                        break;
                    case BinaryEval.op_plus:
                        computePlus(sortedITVs, relEvalBinopU);
                        break;
                    case BinaryEval.op_minus:
                        computeMinus(sortedITVs, relEvalBinopU);
                        break;
                    case BinaryEval.op_multiply:
                        computeMultiply(sortedITVs, relEvalBinopU);
                        break;
                    case BinaryEval.op_eq:
                        computeEQ(sortedITVs, relEvalBinopU);
                        break;
                    case BinaryEval.op_ne:
                        computeNE(sortedITVs, relEvalBinopU);
                        break;
                    case BinaryEval.op_lt:
                        computeLT(sortedITVs, relEvalBinopU);
                        break;
                    case BinaryEval.op_le:
                        computeLE(sortedITVs, relEvalBinopU);
                        break;
                    default:
                        for (Interval x : domU) {
                            relEvalUnaryU.add(op, x, empty);
                            for (Interval y : domU)
                                relEvalBinopU.add(op, x, y, empty);
                        }
                }
            }
            for (var entry : regToEval.entrySet()) {
                Integer reg = entry.getKey();
                IEval eval = entry.getValue();
                if (eval instanceof ConstantEval) {
                    relEConst.add(eval);
                    Integer literal = regToLiteral.get(reg);
                    if (literal != null) {
                        relEConstU.add(eval, new Interval(literal));
                    } else {
                        relEConstU.add(eval, Interval.EMPTY);
                    }
                } else if (eval instanceof UnaryEval) {
                    UnaryEval uEval = (UnaryEval) eval;
                    String op = uEval.getOperator();
                    int inner = uEval.getOperand();
                    relEUnary.add(eval, op, inner);
                } else if (eval instanceof BinaryEval) {
                    BinaryEval bEval = (BinaryEval) eval;
                    String op = bEval.getOperator();
                    int l = bEval.getLeftOperand();
                    int r = bEval.getRightOperand();
                    relEBinop.add(eval, op, l, r);
                } else {
                    Messages.warn("PreInterval: mark unhandled eval as EMPTY %s[%s]", eval.getClass().getSimpleName(), eval.toDebugString());
                    relEConst.add(eval);
                    relEConstU.add(eval, Interval.EMPTY);
                }
            }
        }

        {
            // build predicates
            for (ItvPredicate up : domUP) {
                for (Interval u1 : domU) {
                    for (Interval u2 : domU) {
                        if (up.maysat(u1, u2))
                            relMaySat.add(up, u1, u2);
                        if (up.mayunsat(u1, u2))
                            relMayUnsat.add(up, u1, u2);
                    }
                }
            }
            ProgramRel relPPtrue = consume("PPtrue");
            ProgramRel relPPfalse = consume("PPfalse");
            relPPtrue.load();
            relPPfalse.load();
            for (Object[] tuple : relPPtrue.getValTuples()) {
                Interval u1 = null;
                Interval u2 = null;
                IMemObj h1 = null;
                IMemObj h2 = null;
                ItvPredicate pred = null;
                Integer condv = (Integer) tuple[2];

                // strip out outermost negations
                boolean negated = false;
                IEval eval = regToEval.get(condv);
                while (eval instanceof UnaryEval && ((UnaryEval) eval).getOperator().equals(UnaryEval.op_not)) {
                    condv = ((UnaryEval) eval).getOperand();
                    eval = regToEval.get(condv);
                    negated = !negated;
                }

                if (regToLiteral.containsKey(condv)) {
                    int l1 = regToLiteral.get(condv);
                    u1 = new Interval(l1);
                    u2 = new Interval(0);
                } else if (regToHeap.containsKey(condv)) {
                    h1 = regToHeap.get(condv);
                    u2 = new Interval(0);
                    pred = new ItvPredicate(BinaryEval.op_ne, negated);
                } else if (eval instanceof BinaryEval) {
                    pred = new ItvPredicate(((BinaryEval) eval).getOperator(), negated);
                    int v1 = ((BinaryEval) eval).getLeftOperand();
                    Integer l1 = regToLiteral.get(v1);
                    if (l1 != null) u1 = new Interval(l1);
                    h1 = regToHeap.get(v1);
                    int v2 = ((BinaryEval) eval).getRightOperand();
                    Integer l2 = regToLiteral.get(v2);
                    if (l2 != null) u2 = new Interval(l2);
                    h2 = regToHeap.get(v2);
                }
                assert ((u1 == null || h1 == null) && (u2 == null || h2 == null));
                if (pred == null) {
                    relPredUnknown.add(condv);
                } else {
                    if (h1 != null && h2 != null) {
                        for (IMemObj h : domH) {
                            if (!h.equals(h1) && !h.equals(h2))
                                relNofilter.add(condv, h);
                        }
                        relPred2.add(condv, pred, h1, h2);
                    } else if (h1 != null && u2 != null) {
                        for (IMemObj h : domH) {
                            if (!h.equals(h1))
                                relNofilter.add(condv, h);
                        }
                        relPredL.add(condv, pred, h1, u2);
                    } else if (u1 != null && h2 != null) {
                        for (IMemObj h : domH) {
                            if (!h.equals(h2))
                                relNofilter.add(condv, h);
                        }
                        relPredR.add(condv, pred, u1, h2);
                    } else if (u1 != null && u2 != null) {
                        Messages.error("Interval: cond-val @%d is constant [%d]", condv, regToLiteral.get(condv));
                        relPredUnknown.add(condv);
                    } else {
                        relPredUnknown.add(condv);
                    }
                }
            }
            // no need to dive into relPPfalse as the condition-values are the same
            relPPtrue.close();
            relPPfalse.close();
        }
        for (ProgramRel rel: genRels) {
            rel.save();
            rel.close();
            produceRel(rel);
        }

        for (ProgramDom<?> dom : new ProgramDom[]{domOP, domU, domUP}) {
            dom.save();
            produceDom(dom);
        }
    }

    private Map<Integer, IMemObj> buildVal2HeapMap(ProgramRel relLoadPtr, ProgramRel relCIPT) {
        Map<Integer, Set<IMemObj>> pt = new HashMap<>();
        for (Object[] tuple : relCIPT.getValTuples()) {
            Integer v = (Integer) tuple[0];
            IMemObj h = (IMemObj) tuple[1];
            pt.computeIfAbsent(v, k -> new HashSet<>()).add(h);
        }
        Map<Integer, IMemObj> val2heap = new HashMap<>();
        for (Object[] tuple : relLoadPtr.getValTuples()) {
            Integer u = (Integer) tuple[0];
            Integer ptr = (Integer) tuple[1];
            Set<IMemObj> objs = pt.get(ptr);
            if (objs != null && objs.size() == 1) {
                for (IMemObj obj : objs) {
                    val2heap.put(u, obj);
                }
            }
        }
        return val2heap;
    }

    private static void computeIncr(IndexSet<Interval> sortedITVs, ProgramRel relEvalUnaryU) {
        String op = UnaryEval.op_incr;
        relEvalUnaryU.add(op, empty, empty);
        relEvalUnaryU.add(op, Interval.MIN_INF, Interval.MIN_INF);
        for (Interval x : sortedITVs) {
            int l = x.lower + 1;
            int r = x.upper + 1;
            List<Interval> res = filterInterval(sortedITVs, l, r);
            for (Interval z : res)
                relEvalUnaryU.add(op, x, z);
        }
    }

    private static void computeDecr(IndexSet<Interval> sortedITVs, ProgramRel relEvalUnaryU) {
        String op = UnaryEval.op_decr;
        relEvalUnaryU.add(op, empty, empty);
        relEvalUnaryU.add(op, Interval.MAX_INF, Interval.MAX_INF);
        for (Interval x : sortedITVs) {
            int l = x.lower - 1;
            int r = x.upper - 1;
            List<Interval> res = filterInterval(sortedITVs, l, r);
            for (Interval z : res)
                relEvalUnaryU.add(op, x, z);
        }
    }

    private static void computeNot(IndexSet<Interval> sortedITVs, ProgramRel relEvalUnaryU) {
        String op = UnaryEval.op_not;
        relEvalUnaryU.add(op, empty, empty);
        for (Interval x : sortedITVs) {
            if (x.equals(zero)) {
                relEvalUnaryU.add(op, x, one);
            } else {
                assert !x.contains(0);
                relEvalUnaryU.add(op, x, zero);
            }
        }
    }

    private static void computeAnd(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = BinaryEval.op_and;
        relEvalBinopU.add(op, empty, empty, empty);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, empty, x, empty);
            relEvalBinopU.add(op, x, empty, empty);
            if (x.equals(zero)) {
                relEvalBinopU.add(op, x, empty, zero);
                for (Interval y : sortedITVs)
                    relEvalBinopU.add(op, x, y, zero);
            } else {
                for (Interval y : sortedITVs) {
                    if (y.equals(zero)) {
                        relEvalBinopU.add(op, x, y, zero);
                    } else {
                        relEvalBinopU.add(op, x, y, one);
                    }
                }
            }
        }
    }

    private static void computeOr(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = BinaryEval.op_or;
        relEvalBinopU.add(op, empty, empty, empty);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, empty, x, empty);
            relEvalBinopU.add(op, x, empty, empty);
            if (x.equals(zero)) {
                for (Interval y : sortedITVs) {
                    if (y.equals(zero)) {
                        relEvalBinopU.add(op, x, y, zero);
                    } else {
                        relEvalBinopU.add(op, x, y, one);
                    }
                }
            } else {
                relEvalBinopU.add(op, x, empty, one);
                for (Interval y : sortedITVs) {
                    relEvalBinopU.add(op, x, y, one);
                }
            }
        }
    }

    private static void computePlus(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = BinaryEval.op_plus;
        relEvalBinopU.add(op, empty, empty, empty);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, empty, x, empty);
            relEvalBinopU.add(op, x, empty, empty);
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
                    relEvalBinopU.add(op, x, y, z);
                }
            }
        }
    }

    private static void computeMinus(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = BinaryEval.op_minus;
        relEvalBinopU.add(op, empty, empty, empty);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, empty, x, empty);
            relEvalBinopU.add(op, x, empty, empty);
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
                    relEvalBinopU.add(op, x, y, z);
                }
            }
        }
    }


    private static void computeMultiply(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = BinaryEval.op_multiply;
        relEvalBinopU.add(op, empty, empty, empty);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, empty, x, empty);
            relEvalBinopU.add(op, x, empty, empty);
            for (Interval y : sortedITVs) {
                int p1 =  x.lower * y.lower,
                        p2 = x.lower * y.upper,
                        p3 = x.upper * y.lower,
                        p4 = x.upper * y.upper;
                int l = Collections.min(Arrays.asList(p1, p2 ,p3, p4));
                int r = Collections.max(Arrays.asList(p1, p2, p3, p4));
                List<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval z : res) {
                    relEvalBinopU.add(op, x, y, z);
                }
            }
        }
    }

    private static void computeEQ(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = BinaryEval.op_eq;
        relEvalBinopU.add(op, empty, empty, empty);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, empty, x, empty);
            relEvalBinopU.add(op, x, empty, empty);
            relEvalBinopU.add(op, x, x, one);
            if (x.equals(Interval.MIN_INF) || x.equals(Interval.MAX_INF) || x.lower < x.upper) {
                relEvalBinopU.add(op, x, x, zero);
            }
            for (Interval y : sortedITVs) {
                if (!x.equals(y)) {
                    relEvalBinopU.add(op, x, y, zero);
                }
            }
        }
    }

    private static void computeNE(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = BinaryEval.op_ne;
        relEvalBinopU.add(op, empty, empty, empty);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, empty, x, empty);
            relEvalBinopU.add(op, x, empty, empty);
            relEvalBinopU.add(op, x, x, zero);
            if (x.equals(Interval.MIN_INF) || x.equals(Interval.MAX_INF) || x.lower < x.upper) {
                relEvalBinopU.add(op, x, x, one);
            }
            for (Interval y : sortedITVs) {
                if (!x.equals(y)) {
                    relEvalBinopU.add(op, x, y, one);
                }
            }
        }
    }

    private static void computeLT(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = BinaryEval.op_lt;
        relEvalBinopU.add(op, empty, empty, empty);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, empty, x, empty);
            relEvalBinopU.add(op, x, empty, empty);
            {
                int l = Interval.min_inf;
                int r = x.upper - 1;
                List<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    relEvalBinopU.add(op, x, y, zero);
                }
            }
            {
                int l = x.lower + 1;
                int r = Interval.max_inf;
                List<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    relEvalBinopU.add(op, x, y, one);
                }
            }
        }
    }

    private static void computeLE(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
        String op = BinaryEval.op_le;
        relEvalBinopU.add(op, empty, empty, empty);
        for (Interval x : sortedITVs) {
            relEvalBinopU.add(op, empty, x, empty);
            relEvalBinopU.add(op, x, empty, empty);
            {
                int l = Interval.min_inf;
                int r = x.upper;
                List<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    relEvalBinopU.add(op, x, y, zero);
                }
            }
            {
                int l = x.lower;
                int r = Interval.max_inf;
                List<Interval> res = filterInterval(sortedITVs, l, r);
                for (Interval y : res) {
                    relEvalBinopU.add(op, x, y, one);
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
}
