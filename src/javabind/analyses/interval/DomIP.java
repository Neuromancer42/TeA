package javabind.analyses.interval;

import chord.analyses.ProgramDom;

/**
 * Domain of interval predicates
 * prototypically use string to represent
 */
public class DomIP extends ProgramDom<ItvPredicate> {
    @Override
    public String toUniqueString(ItvPredicate ip) {
        return ip.toString();
    }
}