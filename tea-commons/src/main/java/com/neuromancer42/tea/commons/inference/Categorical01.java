package com.neuromancer42.tea.commons.inference;


import com.neuromancer42.tea.commons.configs.Messages;
import org.apache.commons.math3.distribution.EnumeratedRealDistribution;

import java.util.*;

public class Categorical01 {
    private static final double epsilon = 1.0/1024;
    private static final int num_slots = 64;
    private static final double stride = 1.0/num_slots;
    protected final double[] supports;
    protected final double[] weights;

    public Categorical01(double[] values, double[] weights) {
        Set<Double> supportValues = new HashSet<>();
        for (double v : values) {
            if (v < 0 || v > 1) {
                Messages.fatal("Support value %f out of range [0,1]!", v);
            }
            supportValues.add(v);
        }
        List<Double> sorted = new ArrayList<>(supportValues);
        Collections.sort(sorted);
        this.supports = new double[sorted.size()];
        this.weights = new double[sorted.size()];
        EnumeratedRealDistribution dist = new EnumeratedRealDistribution(values, weights);
        for (int i =  0; i  < sorted.size(); i++) {
            double p = sorted.get(i);
            this.supports[i] = p;
            this.weights[i] = dist.probability(p);
        }
    }

    public Categorical01(double ... values) {
        Set<Double> supportValues = new HashSet<>();
        for (double v : values) {
            if (v < 0 || v > 1) {
                Messages.fatal("Support value %f out of range [0,1]!", v);
            }
            supportValues.add(v);
        }
        List<Double> sorted = new ArrayList<>(supportValues);
        Collections.sort(sorted);
        this.supports = new double[sorted.size()];
        this.weights = new double[sorted.size()];
        EnumeratedRealDistribution dist = new EnumeratedRealDistribution(values);
        for (int i =  0; i  < sorted.size(); i++) {
            double p = sorted.get(i);
            this.supports[i] = p;
            this.weights[i] = dist.probability(p);
        }
    }

    public Categorical01(Categorical01 other) {
        this.supports = other.supports.clone();
        this.weights = other.weights.clone();
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Cat[");
        for (int i = 0; i < supports.length; i++) {
            if (i > 0) sb.append(";");
            sb.append(supports[i]);
            sb.append(":");
            sb.append(String.format("%.2f", weights[i]));
        }
        sb.append("]");
        return sb.toString();
    }

    public double[] getSupports() { return supports.clone(); }

    public double[] getProbabilitis() {
        double[] probs = new double[supports.length];
        EnumeratedRealDistribution dist = new EnumeratedRealDistribution(supports, weights);
        for (int i = 0; i < supports.length; i++) {
            probs[i] = dist.probability(supports[i]);
        }
        return probs;
    }

    public void updateProbs(double[] newWeights) {
        assert (newWeights.length == supports.length);
        EnumeratedRealDistribution dist = new EnumeratedRealDistribution(supports, newWeights);
        for (int i = 0; i < supports.length; ++i) {
            weights[i] = dist.probability(supports[i]);
        }
    }

    public double probability(double x) {
        EnumeratedRealDistribution dist = new EnumeratedRealDistribution(supports, weights);
        return dist.probability(x);
    }


    public static Categorical01 revMultiply(Categorical01 prev, Categorical01 post) {
        if (prev == null)
            return post;
        if (post == null)
            return prev;
        double[] probSlots = new double[num_slots+1];
        double[] weightSlots = new double[num_slots+1];
        for (int i = 0; i < prev.supports.length; ++i) {
            double p = prev.supports[i];
            double wp = prev.weights[i];
            for (int j = 0; j < post.supports.length; ++j) {
                double q = post.supports[j];
                double wq = post.weights[j];
                double pq = 1 - (1- p) * (1- q);
//                if (pq < 1 && pq >= 1 - epsilon)
//                    pq = 1 - epsilon;
//                else if (pq >= 1 - stride)
//                    pq = 1 - stride;
//                else
//                    pq = Math.round(pq / stride) * stride;
                if (pq > 1 || pq < 0)
                    Messages.error("Categorical01: computed support %f = 1-(1-%f)*(1-%f) out of range", pq, p, q);
                if (pq > 1) pq = 1;
                if (pq < 0) pq = 0;
                double w = wp * wq;
                int s = (int) Math.ceil(pq / stride);
                probSlots[s] += pq * w;
                weightSlots[s] += w;
            }
        }
        Map<Double, Double> probMap = new HashMap<>();
        for (int s = 0; s < num_slots + 1; ++s) {
            double weight = weightSlots[s];
            if (weight != 0) {
                double prob = probSlots[s] / weight;
                probMap.put(prob, weight);
            }
        }
        double[] newSupports = new double[probMap.size()];
        double[] newProbs = new double[probMap.size()];
        int i = 0;
        for (var entry : probMap.entrySet()) {
            newSupports[i] = entry.getKey();
            double newProb = entry.getValue();
            // Note: scale down imbalance of prior distributions
//            if (newProb <= stride)
//                newProb = stride;
//            else if (newProb >= 1 - stride)
//                newProb = 1 - stride;
//            else
//                newProb = Math.round(newProb / stride) * stride;
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
        double[] probSlots = new double[num_slots+1];
        double[] weightSlots = new double[num_slots+1];
        for (int i = 0; i < prev.supports.length; ++i) {
            double p = prev.supports[i];
            double wp = prev.weights[i];
            for (int j = 0; j < post.supports.length; ++j) {
                double q = post.supports[j];
                double wq = post.weights[j];
                double pq = p * q;
//                if (pq > 0 && pq <= epsilon)
//                    pq = epsilon;
//                else if (pq <= stride)
//                    pq = stride;
//                else
//                    pq = Math.round(pq / stride) * stride;
                if (pq > 1 || pq < 0)
                    Messages.error("Categorical01: computed support %f = %f*%f out of range", pq, p, q);
                if (pq > 1) pq = 1;
                if (pq < 0) pq = 0;
                double w = wp * wq;
                int s = (int) Math.ceil(pq / stride);
                probSlots[s] += pq * w;
                weightSlots[s] += w;
            }
        }
        Map<Double, Double> probMap = new HashMap<>();
        for (int s = 0; s < num_slots + 1; ++s) {
            double weight = weightSlots[s];
            if (weight != 0) {
                double prob = probSlots[s] / weight;
                probMap.put(prob, weight);
            }
        }
        double[] newSupports = new double[probMap.size()];
        double[] newProbs = new double[probMap.size()];
        int i = 0;
        for (var entry : probMap.entrySet()) {
            newSupports[i] = entry.getKey();
            double newProb = entry.getValue();
            // Note: scale down imbalance of prior distributions
//            if (newProb <= stride)
//                newProb = stride;
//            else if (newProb >= 1 - stride)
//                newProb = 1 - stride;
//            else
//                newProb = Math.round(newProb / stride) * stride;
            newProbs[i] = newProb;
            i++;
        }
        return new Categorical01(newSupports, newProbs);
    }
}
