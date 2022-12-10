package com.neuromancer42.tea.memmodel.object;

import java.util.HashMap;
import java.util.Map;

public class StackObj implements IMemObj {
    private final String accessPath;
    private final String type;
    private static final Map<String, Integer> nameCount = new HashMap<>();

    public StackObj(String access, String type) {
        this.type = type;
        if (nameCount.containsKey(access)) {
            int id = nameCount.get(access);
            this.accessPath = access + "#" + id;
            nameCount.put(access, id + 1);
        } else {
            this.accessPath = access;
            nameCount.put(access, 1);
        }
    }

    @Override
    public String getObjectType() {
        return type;
    }

    @Override
    public String toString() {
        return type + ":" + accessPath;
    }
}
