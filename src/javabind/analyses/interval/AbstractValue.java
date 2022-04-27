package javabind.analyses.interval;

public class AbstractValue {
    public long lower;
    public long upper;
    static public final int MIN = Integer.MIN_VALUE;
    static public final int MAX = Integer.MAX_VALUE;

    public AbstractValue(long l, long r) {
        if (l < MIN || l > MAX || r < MIN || r > MAX || l > r) {
            System.err.println("Warning: ill-formed interval ["+l+","+r+"]!");
        }
        lower = l < MIN ? MIN : l;
        upper = r > MAX ? MAX : r;
        if (l > r) {
            lower = MIN;
            upper = MAX;
        }
    }

    public boolean mayequal(long l, long r) {
        if ((lower > upper) || (l > r)) return true;
        return (lower <= r) && (l <= upper);
    }

    public boolean mayequal(AbstractValue itv) {
        return mayequal(itv.lower, itv.upper);
    }

    public boolean contains(int x) {
        return (lower <= x && x <= upper);
    }
    @Override
    public String toString() {
        return "["+lower +','+upper+']';
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (!(o instanceof AbstractValue))
            return false;
        AbstractValue itv = (AbstractValue) o;
        return (this.lower == itv.lower) && (this.upper == itv.upper);
    }

    @Override
    public int hashCode() {
        return (int) this.lower + (int) this.upper;
    }
}
