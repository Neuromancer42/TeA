package javabind.analyses.interval;

public class ItvPredicate {
    public enum Symbols {
        EQ, NE, GE, GT, LE, LT, BOTTOM
    }
    private Symbols sym;
    private long bound;
    public ItvPredicate(Symbols s, long b, boolean negated) {
        bound = b;
        if (!negated) {
            sym = s;
        } else {
            switch (s) {
                case EQ:
                    sym = Symbols.NE;
                    break;
                case NE:
                    sym = Symbols.EQ;
                    break;
                case GE:
                    sym = Symbols.LT;
                    break;
                case GT:
                    sym = Symbols.LE;
                    break;
                case LE:
                    sym = Symbols.GT;
                    break;
                case LT:
                    sym = Symbols.GE;
                    break;
                default:
                    sym = Symbols.BOTTOM;
                    bound = 0;
            }
        }
    }

    public ItvPredicate(ItvPredicate itv, boolean negated) {
        this(itv.sym, itv.bound, negated);
    }

    public ItvPredicate(Symbols s, int b) {
        this(s, b, false);
    }

    public ItvPredicate(String s, int b) {
        bound = b;
        if (s.contains("=="))
            sym = Symbols.EQ;
        else if (s.contains("!="))
            sym = Symbols.NE;
        else if (s.contains(">="))
            sym = Symbols.GE;
        else if (s.contains(">"))
            sym = Symbols.GT;
        else if (s.contains("<="))
            sym = Symbols.LE;
        else if (s.contains("<"))
            sym = Symbols.LT;
        else {
            sym = Symbols.BOTTOM;
            bound = 0;
            System.err.println("Unhandled symbol: " + s);
        }
    }

    public ItvPredicate(int b, String s) {
        bound = b;
        if (s.contains("=="))
            sym = Symbols.EQ;
        else if (s.contains("!="))
            sym = Symbols.NE;
        else if (s.contains(">="))
            sym = Symbols.LE;
        else if (s.contains(">"))
            sym = Symbols.LT;
        else if (s.contains("<="))
            sym = Symbols.GT;
        else if (s.contains("<"))
            sym = Symbols.GT;
        else {
            sym = Symbols.BOTTOM;
            bound = 0;
            System.err.println("Unhandled symbol: " + s);
        }
    }

    @Override
    public String toString() {
        switch (sym) {
            case EQ: return "P==" + bound;
            case NE: return "P!=" + bound;
            case GE: return "P>=" + bound;
            case GT: return "P>" + bound;
            case LE: return "P<=" + bound;
            case LT: return "P<" + bound;
            default: return "TRUE";
        }
    }

    public boolean maysat(AbstractValue itv) {
        // ill-formed interval is equal to top value (-INF, INF)
        if (itv.lower > itv.upper) return true;
        switch (sym) {
            case EQ:
                return (itv.lower <= bound) && (bound <= itv.lower);
            case NE:
                return (itv.lower < bound) || (bound < itv.upper);
            case GE:
                return bound <= itv.upper;
            case GT:
                return bound < itv.upper;
            case LE:
                return itv.lower <= bound;
            case LT:
                return itv.lower < bound;
            default:
                return true;
        }
    }

    public boolean mayunsat(AbstractValue itv) {
        // ill-formed = (-INF, INF)
        if (itv.lower > itv.upper) return true;
        switch (sym) {
            case EQ:
                return (itv.lower < bound) || (bound < itv.upper);
            case NE:
                return (itv.lower <= bound) && (bound <= itv.upper);
            case GE:
                return itv.lower < bound;
            case GT:
                return itv.lower <= bound;
            case LE:
                return bound < itv.upper;
            case LT:
                return bound <= itv.upper;
            default:
                return true;
        }
    }

    public boolean mustsat(AbstractValue itv) {
        return !this.mayunsat(itv);
    }
    public boolean mustunsat(AbstractValue itv) {
        return !this.maysat(itv);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof ItvPredicate)) return false;
        ItvPredicate p = (ItvPredicate) o;
        return (this.sym == p.sym) && (this.bound == p.bound);
    }

    @Override
    public int hashCode() {
        return  (int) bound*Symbols.values().length + sym.hashCode();
    }
}
