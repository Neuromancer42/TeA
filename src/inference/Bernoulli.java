package inference;

import org.apache.commons.math3.distribution.EnumeratedIntegerDistribution;

public class Bernoulli {
    private EnumeratedIntegerDistribution dist;

    public Bernoulli() {
        int[] supports = {0,1};
        dist = new EnumeratedIntegerDistribution(supports);
    }

    public Bernoulli(double p) {
        int[] supports = {0,1};
        double[] probs = {1-p, p};
        dist = new EnumeratedIntegerDistribution(supports, probs);
    }

    public Bernoulli(double[] weights) {
        int[] supports = {0,1};
        dist = new EnumeratedIntegerDistribution(supports, weights);
    }

    public Bernoulli(Bernoulli other) {
        dist = new EnumeratedIntegerDistribution(other.getSupports(), other.getProbabilities());
    }

    public String toString() {
        StringBuilder sb =  new StringBuilder();
        sb.append("Bernoulli-");
        sb.append(String.format("%.2f", dist.probability(1)));
        return sb.toString();
    }

    public int[] getSupports() {
        return new int[]{0,1};
    }

    public double[] getProbabilities() {
        return new double[]{dist.probability(0), dist.probability(1)};
    }
    public void updateProbs(double p) {
        int[] supports = {0,1};
        double[] probs = {1-p, p};
        dist = new EnumeratedIntegerDistribution(supports, probs);
    }

    public void updateProbs(double[] probs) {
        int[] supports = {0,1};
        dist = new EnumeratedIntegerDistribution(supports, probs);
    }

    public double probability(int x) {
        assert x == 0 || x == 1;
        return dist.probability(x);
    }
}
