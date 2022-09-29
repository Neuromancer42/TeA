package com.neuromancer42.tea.libdai;

import com.neuromancer42.tea.core.inference.AbstractCausalDriver;
import com.neuromancer42.tea.core.inference.CausalGraph;
import com.neuromancer42.tea.core.inference.ICausalDriverFactory;
import com.neuromancer42.tea.core.project.Messages;

public class DAIDriverFactory implements ICausalDriverFactory {
    private static DAIDriverFactory factory = null;
    private static final String name = "libdai";
    private static final String[] algorithms = {"iterating", "oneshot"};

    public static DAIDriverFactory  g() {
        if (factory == null) {
            factory = new DAIDriverFactory();
        }
        return factory;
    }

    @Override
    public AbstractCausalDriver createCausalDriver(String type, String name, CausalGraph<String> causalGraph) {
        if (type.equals("iterating")){
            return new IteratingCausalDriver(name, causalGraph);
        } else if (type.equals("oneshot")) {
            return new OneShotCausalDriver(name, causalGraph);
        } else {
            Messages.error("unknown driver type, use iterating driver by default");
            return new IteratingCausalDriver(name, causalGraph);
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
