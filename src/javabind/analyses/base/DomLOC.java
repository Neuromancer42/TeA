package javabind.analyses.base;

import chord.analyses.ProgramDom;
import javabind.program.binddefs.BindUtils;
import soot.Unit;

public class DomLOC extends ProgramDom<Unit> {
    @Override
    public String toUniqueString(Unit u) {
        return BindUtils.unitToString(u);
    }
}
