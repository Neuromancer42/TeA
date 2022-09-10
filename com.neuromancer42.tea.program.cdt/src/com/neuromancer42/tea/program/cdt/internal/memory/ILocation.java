package com.neuromancer42.tea.program.cdt.internal.memory;

import com.neuromancer42.tea.core.analyses.IDebuggable;
import org.eclipse.cdt.core.dom.ast.IType;

public interface ILocation extends IDebuggable {
    boolean isStatic();
    IType getType();
    int[] getParameters();
}
