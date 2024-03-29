package com.neuromancer42.tea.commons.inference;

public interface ICausalDriverFactory {
    AbstractCausalDriver createCausalDriver(String algorithm, String name, CausalGraph causalGraph);
    String getName();
    String[] getAlgorithms();
}
