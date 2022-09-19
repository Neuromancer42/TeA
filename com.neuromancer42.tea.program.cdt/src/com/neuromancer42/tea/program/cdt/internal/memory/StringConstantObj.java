package com.neuromancer42.tea.program.cdt.internal.memory;

import org.eclipse.cdt.core.dom.ast.IBasicType;
import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.internal.core.dom.parser.c.CBasicType;
import org.eclipse.cdt.internal.core.dom.parser.c.CPointerType;

public class StringConstantObj implements IMemObj {
    private final String str;

    public StringConstantObj(String str) {
        this.str = str;
    }

    @Override
    public String toDebugString() {
        return "StringConstant:\"" + str + "\"";
    }

    @Override
    public IType getObjectType() {
        return new CBasicType(IBasicType.Kind.eChar, 0);
    }

    @Override
    public boolean isStatic() {
        return true;
    }
}
