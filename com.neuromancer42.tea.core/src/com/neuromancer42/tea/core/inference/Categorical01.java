package com.neuromancer42.tea.core.inference;


import com.neuromancer42.tea.core.project.Messages;
import org.apache.commons.math3.distribution.EnumeratedRealDistribution;

import java.util.*;

public class Categorical01 {
    protected final double[] supports;
    private EnumeratedRealDistribution dist;

    public Categorical01(double[] values, double[] weights) {
        dist = new EnumeratedRealDistribution(values, weights);
        Set<Double> supportValues = new HashSet<>();
        for (double v : values) {
            if (v < 0 || v > 1) {
                Messages.fatal("Support values out of range [0,1]!");
            }
            supportValues.add(v);
        }
        List<Double> sorted = new ArrayList<>(supportValues);
        Collections.sort(sorted);
        supports = new double[sorted.size()];
        for (int i =  0; i  < sorted.size(); i++) {
            supports[i] = sorted.get(i);
        }
    }

    public Categorical01(double[] values) {
        dist = new EnumeratedRealDistribution(values);
        Set<Double> supportValues = new HashSet<>();
        for (double v : values) {
            if (v < 0 || v > 1) {
                Messages.fatal("Support values out of range [0,1]!");
            }
            supportValues.add(v);
        }
        List<Double> sorted = new ArrayList<>(supportValues);
        Collections.sort(sorted);
        supports = new double[sorted.size()];
        for (int i =  0; i  < sorted.size(); i++) {
            supports[i] = sorted.get(i);
        }
    }

    public Categorical01(Categorical01 other) {
        dist = new EnumeratedRealDistribution(other.getSupports(), other.getProbabilitis());
        this.supports = other.supports.clone();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Cat[");
        for (int i = 0; i < supports.length; i++) {
            if (i > 0) sb.append(";");
            double v = supports[i];
            sb.append(v);
            sb.append(":");
            sb.append(String.format("%.2f", probability(v)));
        }
        sb.append("]");
        return sb.toString();
    }

    double[] getSupports() { return supports.clone(); }

    double[] getProbabilitis() {
        double[] probs = new double[supports.length];
        for (int i = 0; i < supports.length; i++) {
            probs[i] = probability(supports[i]);
        }
        return probs;
    }

    public void updateProbs(double[] newWeights) {
        assert (newWeights.length == supports.length);
        dist = new EnumeratedRealDistribution(supports, newWeights);
    }

    public double probability(double x) {
        return dist.probability(x);
    }
}
