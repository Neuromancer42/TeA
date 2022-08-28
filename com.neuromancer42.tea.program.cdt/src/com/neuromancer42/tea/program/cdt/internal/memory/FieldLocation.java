package com.neuromancer42.tea.program.cdt.internal.memory;

import org.eclipse.cdt.core.dom.ast.IField;
import org.eclipse.cdt.core.dom.ast.IType;

// a memory location that lie along base location with offset indicated by field
public class FieldLocation implements ILocation {
    private final ILocation base;
    private final IField field;

    public FieldLocation(ILocation base, IField field) {
        this.base = base;
        this.field = field;
    }

    public ILocation getBaseLocation() {
        return base;
    }

    public IField getField() {
        return field;
    }

    @Override
    public IType getType() {
        return field.getType();
    }

    @Override
    public boolean isStatic() {
        return base.isStatic();
    }

    @Override
    public String toDebugString() {
        return base.toDebugString() + "_" +field.getName();
    }

    @Override
    public int[] getParameters() {
        return base.getParameters();
    }
}
