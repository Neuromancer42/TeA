package javabind.analyses.interval;

import chord.analyses.ProgramDom;

/**
 * Domain of intervals
 *
 * @author Yifan Chen
 */
public class DomITV extends ProgramDom<AbstractValue> {
    @Override
    public String toUniqueString(AbstractValue itv) {
        return itv.toString();
    }
}
