package com.neuromancer42.tea.core.inference;

public interface ICausalDriverFactory {
    AbstractCausalDriver createCausalDriver(String algorithm, String name, CausalGraph<String> causalGraph);
    String getName();
    String[] getAlgorithms();
}
