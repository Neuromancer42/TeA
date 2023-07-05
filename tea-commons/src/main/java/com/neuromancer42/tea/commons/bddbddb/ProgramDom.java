package com.neuromancer42.tea.commons.bddbddb;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;

import com.neuromancer42.tea.commons.configs.Messages;

public class ProgramDom extends Dom<String> {
    private String cacheLocation;

    public ProgramDom(String domName) {
        super();
        super.setName(domName);
    }

    public void save(String location) {
        Messages.debug("ProgramDom %s: SAVING dom size: %d", name, size());
        try {
            super.save(location, true);
        } catch (IOException e) {
            Messages.error("ProgramDom %s: cannot save dom map to %s", name, location);
            Messages.fatal(e);
        }
        this.cacheLocation = location;
    }

    public void load(String location) {
        try {
            if (size() != 0 || cacheLocation != null) {
                Messages.error("ProgramDom %s: override existing dom elements in: %s", Objects.toString(cacheLocation, "null"));
            }
            clear();
            cacheLocation = location;
            List<String> vals = Files.readAllLines(Paths.get(location, name + ".map"));
            addAll(vals);
        } catch (IOException e) {
            Messages.error("ProgramDom %s: cannot read dom map from %s", name, location);
            Messages.fatal(e);
        }
    }

    @Override
    public String toString() {
        return name;
    }

    public String getLocation() {
        return cacheLocation;
    }

    public void init() {
    }
}
