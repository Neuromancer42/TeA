package javabind.analyses.base;

import javabind.program.binddefs.BindUtils;
import soot.Unit;
import shord.project.analyses.ProgramDom;

/**
 * Domain of method invocation stmts
 * 
 * @author Saswat Anand
 */
public class DomI extends ProgramDom<Unit> {
    @Override
    public String toUniqueString(Unit u) {
		return BindUtils.unitToString(u);
    }
}
