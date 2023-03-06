package com.neuromancer42.tea.commons.inference;


import com.neuromancer42.tea.commons.configs.Messages;
import org.apache.commons.math3.distribution.EnumeratedRealDistribution;

import java.util.*;

public class Categorical01 {
    private static final double epsilon = 1.0/32;
    private static final double stride = 1.0/16;
    protected final double[] supports;
    private EnumeratedRealDistribution dist;

    public Categorical01(double[] values, double[] weights) {
        dist = new EnumeratedRealDistribution(values, weights);
        Set<Double> supportValues = new HashSet<>();
        for (double v : values) {
            if (v < 0 || v > 1) {
                Messages.fatal("Support value %f out of range [0,1]!", v);
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

    public Categorical01(double ... values) {
        dist = new EnumeratedRealDistribution(values);
        Set<Double> supportValues = new HashSet<>();
        for (double v : values) {
            if (v < 0 || v > 1) {
                Messages.fatal("Support value %f out of range [0,1]!", v);
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

    public double[] getSupports() { return supports.clone(); }

    public double[] getProbabilitis() {
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


    public static Categorical01 revMultiply(Categorical01 prev, Categorical01 post) {
        if (prev == null)
            return post;
        if (post == null)
            return prev;
        Map<Double, Double> probMap = new HashMap<>();
        for (double p : prev.supports) {
            for (double q : prev.supports) {
                double pq = 1 - (1- p) * (1- q);
                if (pq < 1 && pq > 1 - epsilon)
                    pq = 1 - epsilon;
                else if (pq >= 1 - stride)
                    pq = 1 - stride;
                else
                    pq = Math.round(pq / stride) * stride;
                if (pq > 1 || pq < 0)
                    Messages.error("Categorical01: computed support %f = 1-(1-%f)*(1-%f) out of range", pq, p, q);
                if (pq > 1) pq = 1;
                if (pq < 0) pq = 0;
                double w = prev.probability(p) * prev.probability(q);
                probMap.compute(pq, (k, v) -> (v == null ? w : v + w));
            }
        }
        double[] newSupports = new double[probMap.size()];
        double[] newProbs = new double[probMap.size()];
        int i = 0;
        for (var entry : probMap.entrySet()) {
            newSupports[i] = entry.getKey();
            double newProb = entry.getValue();
            if (newProb < epsilon)
                newProb = epsilon;
            else if (newProb >  1 - epsilon)
                newProb = 1 - epsilon;
            else
                newProb = Math.round(newProb / stride) * stride;
            newProbs[i] = newProb;
            i++;
        }
        return new Categorical01(newSupports, newProbs);
    }

    public static Categorical01 multiplyDist(Categorical01 prev, Categorical01 post) {
        if (prev == null)
            return post;
        if (post == null)
            return prev;
        Map<Double, Double> probMap = new HashMap<>();
        for (double p : prev.supports) {
            for (double q : prev.supports) {
                double pq = p * q;
                if (pq > 0 && pq < epsilon)
                    pq = epsilon;
                else if (pq <= stride)
                    pq = stride;
                else
                    pq = Math.round(pq / stride) * stride;
                if (pq > 1 || pq < 0)
                    Messages.error("Categorical01: computed support %f = %f*%f out of range", pq, p, q);
                if (pq > 1) pq = 1;
                if (pq < 0) pq = 0;
                double w = prev.probability(p) * prev.probability(q);
                probMap.compute(pq, (k, v) -> (v == null ? w : v + w));
            }
        }
        double[] newSupports = new double[probMap.size()];
        double[] newProbs = new double[probMap.size()];
        int i = 0;
        for (var entry : probMap.entrySet()) {
            newSupports[i] = entry.getKey();
            double newProb = entry.getValue();
            if (newProb < epsilon)
                newProb = epsilon;
            else if (newProb >  1 - epsilon)
                newProb = 1 - epsilon;
            else
                newProb = Math.round(newProb / stride) * stride;
            newProbs[i] = newProb;
            i++;
        }
        return new Categorical01(newSupports, newProbs);
    }
}
