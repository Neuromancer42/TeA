package com.neuromancer42.tea.program.cdt;

import com.neuromancer42.tea.core.analyses.AnalysesUtil;
import com.neuromancer42.tea.core.analyses.JavaAnalysis;
import com.neuromancer42.tea.core.analyses.ProgramDom;
import com.neuromancer42.tea.core.analyses.ProgramRel;
import com.neuromancer42.tea.core.project.Messages;
import com.neuromancer42.tea.core.project.Trgt;
import com.neuromancer42.tea.core.util.IndexSet;
import com.neuromancer42.tea.program.cdt.internal.evaluation.BinaryEval;
import com.neuromancer42.tea.program.cdt.internal.evaluation.ConstantEval;
import com.neuromancer42.tea.program.cdt.internal.evaluation.IEval;
import com.neuromancer42.tea.program.cdt.internal.evaluation.UnaryEval;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.internal.core.dom.parser.c.CBasicType;

import java.util.*;

public class PreIntervalAnalysis extends JavaAnalysis {
    private final Trgt<ProgramDom<Integer>> tDomV;
    private final Trgt<ProgramDom<IEval>> tDomE;
    private final Trgt<ProgramDom<String>> tDomOP;
    private final Trgt<ProgramDom<Interval>> tDomU;

    private final Trgt<ProgramRel> tRelPeval;
    private final Trgt<ProgramRel> tRelEConst;
    private final Trgt<ProgramRel> tRelEUnary;
    private final Trgt<ProgramRel> tRelEBinop;
    private final Trgt<ProgramRel> tRelEConstU;
    private final Trgt<ProgramRel> tRelEvalUnaryU;
    private final Trgt<ProgramRel> tRelEvalBinopU;
    
    private final static Interval empty = Interval.EMPTY;
    private final static Interval zero = new Interval(0);
    private final static Interval one = new Interval(1);

    public PreIntervalAnalysis() {
        this.name = "preInterval";
        tDomV = AnalysesUtil.createDomTrgt(name, "V", Integer.class);
        tDomE = AnalysesUtil.createDomTrgt(name, "E", IEval.class);
        tDomOP = AnalysesUtil.createDomTrgt(name, "OP", String.class);
        tDomU = AnalysesUtil.createDomTrgt(name, "U", Interval.class);

        tRelPeval = AnalysesUtil.createRelTrgt(name, "Peval", "P", "V", "E");
        tRelEConst = AnalysesUtil.createRelTrgt(name, "Econst", "E");
        tRelEUnary = AnalysesUtil.createRelTrgt(name, "Eunary", "E", "OP", "V");
        tRelEBinop = AnalysesUtil.createRelTrgt(name, "Ebinop", "E", "OP", "V", "V");
        tRelEConstU = AnalysesUtil.createRelTrgt(name, "EConstU", "E", "U");
        tRelEvalUnaryU = AnalysesUtil.createRelTrgt(name, "evalUnaryU", "OP", "U", "U");
        tRelEvalBinopU = AnalysesUtil.createRelTrgt(name, "evalBinopU", "OP", "U", "U", "U");
        registerConsumers(tDomV, tDomE, tRelPeval);
        registerProducers(tDomOP, tDomU, tRelEConst, tRelEUnary, tRelEBinop, tRelEConstU, tRelEvalUnaryU, tRelEvalBinopU);
    }

    @Override
    public void run() {
        ProgramDom<Integer> domV = tDomV.get();
        ProgramDom<IEval> domE = tDomE.get();
        ProgramRel relPeval = tRelPeval.get();
        relPeval.load();

        ProgramDom<String> domOP = ProgramDom.createDom("OP", String.class);
        ProgramDom<Interval> domU = ProgramDom.createDom("U", Interval.class);
        ProgramDom<?>[] genDoms = new ProgramDom[]{domOP, domU};
        for (ProgramDom<?> dom : genDoms) {
            dom.init();
        }
        Set<Integer> intConstants = new HashSet<>();
        for (Object[] tuple : relPeval.getValTuples()) {
            IEval eval = (IEval) tuple[2];
            if (eval instanceof ConstantEval) {
                IType type = eval.getType();
                String constRepr = ((ConstantEval) eval).getValue();
                if (type.isSameType(CBasicType.INT)) {
                    try {
                        int primVal = Integer.parseInt(constRepr);
                        Messages.debug("PreInterval: find new constant integer %d", primVal);
                        intConstants.add(primVal);
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
        intConstants.add(0);
        intConstants.add(1);
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

        ProgramRel relEConst = new ProgramRel("Econst", domE);
        ProgramRel relEUnary = new ProgramRel("Eunary", domE, domOP, domV);
        ProgramRel relEBinop = new ProgramRel("EBinop", domE, domOP, domV, domV);
        ProgramRel relEConstU = new ProgramRel("EConstU", domE, domU);
        ProgramRel relEvalUnaryU = new ProgramRel("evalUnaryU", domOP, domU, domU);
        ProgramRel relEvalBinopU = new ProgramRel("evalBinopU", domOP, domU, domU, domU);

        ProgramRel[] genRels = new ProgramRel[]{relEConst, relEUnary, relEBinop, relEConstU, relEvalUnaryU, relEvalBinopU};

        for (ProgramRel rel : genRels) {
            rel.init();
        }
        for (Object[] tuple : relPeval.getValTuples()) {
            IEval eval = (IEval) tuple[2];
            if (eval instanceof ConstantEval) {
                relEConst.add(eval);
                IType type = eval.getType();
                String constRepr = ((ConstantEval) eval).getValue();
                if (type.isSameType(CBasicType.INT)) {
                    try {
                        int primVal = Integer.parseInt(constRepr);
                        relEConstU.add(eval, new Interval(primVal));
                        continue;
                    } catch (NumberFormatException ignored) {}
                }
                relEConstU.add(eval, Interval.EMPTY);
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

        assert domU.contains(empty);
        assert domU.contains(zero);
        assert domU.contains(one);
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
                    computeEq(sortedITVs, relEvalBinopU);
                    break;
                case BinaryEval.op_ne:
                    computeNe(sortedITVs, relEvalBinopU);
                    break;
                case BinaryEval.op_lt:
                    computeLt(sortedITVs, relEvalBinopU);
                    break;
                case BinaryEval.op_le:
                    computeLe(sortedITVs, relEvalBinopU);
                    break;
                default:
                    for (Interval x : domU) {
                        relEvalUnaryU.add(op, x, empty);
                        for (Interval y : domU)
                            relEvalBinopU.add(op, x, y, empty);
                    }
            }
        }
        relPeval.close();
        for (ProgramRel rel : genRels) {
            rel.save();
            rel.close();
        }

        tDomOP.accept(domOP);
        tDomU.accept(domU);

        tRelEConst.accept(relEConst);
        tRelEUnary.accept(relEUnary);
        tRelEBinop.accept(relEBinop);
        tRelEConstU.accept(relEConstU);
        tRelEvalUnaryU.accept(relEvalUnaryU);
        tRelEvalBinopU.accept(relEvalBinopU);
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

    private static void computeEq(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
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

    private static void computeNe(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
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

    private static void computeLt(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
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

    private static void computeLe(IndexSet<Interval> sortedITVs, ProgramRel relEvalBinopU) {
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
