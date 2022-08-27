package com.neuromancer42.tea.program.cdt.internal.memory;

import org.eclipse.cdt.core.dom.ast.IType;

public interface ILocation {
    boolean isStatic();
    String toDebugString();
    IType getType();
}
