package com.neuromancer42.tea.libdai;

import com.neuromancer42.tea.commons.inference.AbstractCausalDriver;
import com.neuromancer42.tea.commons.inference.CausalGraph;
import com.neuromancer42.tea.commons.inference.ICausalDriverFactory;
import com.neuromancer42.tea.commons.configs.Messages;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class DAIDriverFactory implements ICausalDriverFactory {
    private static final String name = "libdai";
    private static final String[] algorithms = {"iterating", "oneshot"};

    private final Path workPath;

    public DAIDriverFactory(Path path) {
        workPath = path;
    }

    @Override
    public AbstractCausalDriver createCausalDriver(String type, String driverName, CausalGraph causalGraph) {
        Path driverPath;
        try {
            driverPath = Files.createDirectories(workPath.resolve(driverName));
        } catch (IOException e) {
            Messages.error("DAIDriverFactory: failed to create working dir for %s driver %s: %s", type, driverName, e.getMessage());
            return null;
        }
        switch (type) {
            case "dynaboost":
            case "baseline":
                return new DynaboostCausalDriver(driverName, driverPath, causalGraph);
            case "iterating":
                return new IteratingCausalDriver(driverName, driverPath, causalGraph);
            case "oneshot":
                return new OneShotCausalDriver(driverName, driverPath, causalGraph);
            default:
                Messages.error("DAIDriverFactory: unknown driver type, use iterating driver by default");
                return new IteratingCausalDriver(driverName, driverPath, causalGraph);
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String[] getAlgorithms() {
        return algorithms;
    }
}
