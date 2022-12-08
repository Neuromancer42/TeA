package com.neuromancer42.tea.model.inference;

public interface ICausalDriverFactory {
    AbstractCausalDriver createCausalDriver(String algorithm, String name, CausalGraph<String> causalGraph);
    String getName();
    String[] getAlgorithms();
}
