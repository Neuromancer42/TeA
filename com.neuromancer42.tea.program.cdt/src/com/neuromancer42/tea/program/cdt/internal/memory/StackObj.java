package com.neuromancer42.tea.program.cdt.internal.memory;

import org.eclipse.cdt.core.dom.ast.IType;
import org.eclipse.cdt.core.dom.ast.IVariable;

import java.util.HashMap;
import java.util.Map;

public class StackObj implements IMemObj {
    private final IType type;
    private final String name;
    private final IVariable variable;
    private static final Map<String, Integer> nameCount = new HashMap<>();

    public StackObj(IType type) {
        this("_anon_", type, null);
    }

    public StackObj(String name, IType type) {
        this(name, type, null);
    }

    public StackObj(IVariable var) {
        this(var.getName(), var.getType(), var);
    }

    private StackObj(String name, IType type, IVariable variable) {
        this.type = type;
        if (nameCount.containsKey(name)) {
            int id = nameCount.get(name);
            this.name = name + "#" + id;
            nameCount.put(name, id + 1);
        } else {
            this.name = name;
            nameCount.put(name, 1);
        }
        this.variable = variable;
    }

    @Override
    public String toDebugString() {
        return "alloca:" + (type == null ? "unknown" : type) + "$" + (name == null ? ("anon" + hashCode()) : name);
    }

    @Override
    public IType getObjectType() {
        return type;
    }

    public boolean observable() {
        return (variable != null);
    }
}
