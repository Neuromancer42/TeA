package com.neuromancer42.tea.program.cdt;

import java.util.Objects;

public class Interval {
    public static final int max_bound = 32767;
    public static final int min_bound = -max_bound;
    public static final int min_inf = min_bound - 1;
    public static final int max_inf = max_bound + 1;
    public static final Interval MIN_INF = new Interval(min_inf);
    public static final Interval MAX_INF = new Interval(max_inf);
    public static final Interval EMPTY = new Interval(max_bound, min_bound);

    public final int lower;
    public final int upper;

    public Interval(int l, int r) {
        if (l > r) {
            l = max_bound;
            r = min_bound;
        } else {
            if (l < min_bound) {
                l = min_bound;
                if (r < l) {
                    r = min_inf;
                    l = r;
                }
            }
            if (r > max_bound) {
                r = max_bound;
                if (l > r) {
                    l = max_inf;
                    r = l;
                }
            }
        }
        this.lower = l;
        this.upper = r;
    }

    public Interval(int v) {
        this(v, v);
    }

    boolean contains(int x) {
        if (lower > max_bound)
            return x > max_bound;
        if (upper < min_bound)
            return x < min_bound;
        return (lower <= x) && (x <= upper);
    }

    boolean overlap(int l, int r) {
        if (l > r || lower > upper)
            return false;
        if (lower > max_bound)
            return r > max_bound;
        if (upper < min_bound)
            return l < min_bound;
        return (lower <= r && l <= upper);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if (! (o instanceof Interval))
            return false;
        Interval that = (Interval) o;
        return (this.lower == that.lower) && (this.upper == that.upper);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lower, upper);
    }

    @Override
    public String toString() {
        if (lower > upper) {
            return "Itv:empty";
        }
        if (lower < min_bound) {
            return "Itv:-inf";
        }
        if (upper > max_bound) {
            return "Itv:+inf";
        }
        if (lower == upper) {
            return "Itv:{" + lower + "}";
        }
        return "Itv:[" + lower + "," + upper + "]";
    }
}
