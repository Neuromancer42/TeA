package com.neuromancer42.tea.program.cdt.memmodel.object;

import com.neuromancer42.tea.core.analyses.IDebuggable;
import org.eclipse.cdt.core.dom.ast.IType;

public interface IMemObj extends IDebuggable {
    IType getObjectType();
}
