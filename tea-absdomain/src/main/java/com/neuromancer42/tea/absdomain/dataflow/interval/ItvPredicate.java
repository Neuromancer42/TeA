package com.neuromancer42.tea.absdomain.dataflow.interval;

import com.neuromancer42.tea.commons.configs.Constants;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public class ItvPredicate {
    public enum Symbol {
        EQ, NE, GE, GT, LE, LT, BOTTOM
    }
    private final Symbol sym;

    private static Symbol negate(Symbol s) {
        return switch (s) {
            case EQ -> Symbol.NE;
            case NE -> Symbol.EQ;
            case GE -> Symbol.LT;
            case GT -> Symbol.LE;
            case LE -> Symbol.GT;
            case LT -> Symbol.GE;
            default -> Symbol.BOTTOM;
        };
    }

    private ItvPredicate(Symbol s) {
        this.sym = s;
    }

    public ItvPredicate(String op, boolean negated) {
        Symbol s;
        if (op.contains(Constants.OP_EQ))
            s = Symbol.EQ;
        else if (op.contains(Constants.OP_NE))
            s = Symbol.NE;
        else if (op.contains(Constants.OP_LE))
            s = Symbol.LE;
        else if (op.contains(Constants.OP_LT))
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
        return switch (sym) {
            case EQ -> itv1.mayEQ(itv2);
            case NE -> itv1.mayNE(itv2);
            case GE -> itv1.mayGE(itv2);
            case GT -> itv1.mayGT(itv2);
            case LE -> itv1.mayLE(itv2);
            case LT -> itv1.mayLT(itv2);
            default -> true;
        };
    }

    public boolean mayunsat(Interval itv1, Interval itv2) {
        return switch (sym) {
            case EQ -> itv1.mayNE(itv2);
            case NE -> itv1.mayEQ(itv2);
            case GE -> itv1.mayLT(itv2);
            case GT -> itv1.mayLE(itv2);
            case LE -> itv1.mayGT(itv2);
            case LT -> itv1.mayGE(itv2);
            default -> true;
        };
    }

    public boolean mustsat(Interval itv1, Interval itv2) {
        return  !mayunsat(itv1, itv2);
    }

    public boolean mustunsat(Interval itv1, Interval itv2) {
        return !maysat(itv1, itv2);
    }

    @Override
    public String toString() {
        return switch (sym) {
            case EQ -> "P==";
            case NE -> "P!=";
            case GE -> "P>=";
            case GT -> "P>";
            case LE -> "P<=";
            case LT -> "P<";
            default -> "Unknown";
        };
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

    public static Set<ItvPredicate> allPredicates() {
        Set<ItvPredicate> all = new LinkedHashSet<>();
        for (Symbol s : Symbol.values()) {
            all.add(new ItvPredicate(s));
        }
        return all;
    }
}
