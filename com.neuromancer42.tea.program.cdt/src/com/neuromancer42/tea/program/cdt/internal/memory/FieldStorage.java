package com.neuromancer42.tea.program.cdt.internal.memory;

import org.eclipse.cdt.core.dom.ast.IField;
import org.eclipse.cdt.core.dom.ast.IType;

// a memory location that lie along base location with offset indicated by field
public class FieldStorage implements IStorage {
    private final IStorage base;
    private final IField field;

    public FieldStorage(IStorage base, IField field) {
        this.base = base;
        this.field = field;
    }

    public IStorage getBaseLocation() {
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
}
