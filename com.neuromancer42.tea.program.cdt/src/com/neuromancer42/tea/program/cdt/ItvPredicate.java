package com.neuromancer42.tea.program.cdt;

import com.neuromancer42.tea.program.cdt.internal.evaluation.BinaryEval;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class ItvPredicate {
    public enum Symbol {
        EQ, NE, GE, GT, LE, LT, BOTTOM
    }
    private final Symbol sym;

    private static Symbol negate(Symbol s) {
        switch (s) {
            case EQ:
                return Symbol.NE;
            case NE:
                return Symbol.EQ;
            case GE:
                return Symbol.LT;
            case GT:
                return Symbol.LE;
            case LE:
                return Symbol.GT;
            case LT:
                return Symbol.GE;
            default:
                return Symbol.BOTTOM;
        }
    }

    private ItvPredicate(Symbol s) {
        this.sym = s;
    }

    public ItvPredicate(String op, boolean negated) {
        Symbol s;
        if (op.contains(BinaryEval.op_eq))
            s = Symbol.EQ;
        else if (op.contains(BinaryEval.op_ne))
            s = Symbol.NE;
        else if (op.contains(BinaryEval.op_le))
            s = Symbol.LE;
        else if (op.contains(BinaryEval.op_lt))
            s = Symbol.LT;
        else {
            s = Symbol.BOTTOM;
        }
        if (!negated) {
            sym = s;
        } else {
            sym = negate(s);
        }
    }

    public boolean maysat(Interval itv1, Interval itv2) {
        switch (sym) {
            case EQ:
                return itv1.mayEQ(itv2);
            case NE:
                return itv1.mayNE(itv2);
            case GE:
                return itv1.mayGE(itv2);
            case GT:
                return itv1.mayGT(itv2);
            case LE:
                return itv1.mayLE(itv2);
            case LT:
                return itv1.mayLT(itv2);
            default:
                return true;
        }
    }

    public boolean mayunsat(Interval itv1, Interval itv2) {
        switch (sym) {
            case EQ:
                return itv1.mayNE(itv2);
            case NE:
                return itv1.mayEQ(itv2);
            case GE:
                return itv1.mayLT(itv2);
            case GT:
                return itv1.mayLE(itv2);
            case LE:
                return itv1.mayGT(itv2);
            case LT:
                return itv1.mayGE(itv2);
            default:
                return true;
        }
    }

    public boolean mustsat(Interval itv1, Interval itv2) {
        return  !mayunsat(itv1, itv2);
    }

    public boolean mustunsat(Interval itv1, Interval itv2) {
        return !maysat(itv1, itv2);
    }

    @Override
    public String toString() {
        switch (sym) {
            case EQ: return "P==";
            case NE: return "P!=";
            case GE: return "P>=";
            case GT: return "P>";
            case LE: return "P<=";
            case LT: return "P<";
            default: return "Unknown";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ItvPredicate)) return false;
        ItvPredicate p = (ItvPredicate) o;
        return (this.sym == p.sym);
    }

    @Override
    public int hashCode() {
        return  Objects.hash(sym);
    }

    static Set<ItvPredicate> allPredicates() {
        Set<ItvPredicate> all = new LinkedHashSet<>();
        for (Symbol s : Symbol.values()) {
            all.add(new ItvPredicate(s));
        }
        return all;
    }
}
