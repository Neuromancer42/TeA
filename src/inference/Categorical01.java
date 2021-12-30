package inference;

import chord.project.Messages;
import org.apache.commons.math3.distribution.EnumeratedRealDistribution;

import java.util.*;

public class Categorical01 extends EnumeratedRealDistribution {
    protected List<Double> supports;

    public Categorical01(double[] values, double[] weights) {
        super(values, weights);
        Set<Double> supportValues = new HashSet<>();
        for (double v : values) {
            if (v < 0 || v > 1) {
                Messages.fatal("Support values out of range [0,1]!");
            }
            supportValues.add(v);
        }
        supports = new ArrayList<>(supportValues);
        Collections.sort(supports);
    }

    public Categorical01(double[] values) {
        super(values);
        Set<Double> supportValues = new HashSet<>();
        for (double v : values) {
            if (v < 0 || v > 1) {
                Messages.fatal("Support values out of range [0,1]!");
            }
            supportValues.add(v);
        }
        supports = new ArrayList<>(supportValues);
        Collections.sort(supports);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Cat[");
        for (int i = 0; i < supports.size(); i++) {
            if (i > 0) sb.append(";");
            double v = supports.get(i);
            sb.append(v);
            sb.append(":");
            sb.append(String.format("%.2f", probability(v)));
        }
        sb.append("]");
        return sb.toString();
    }

    List<Double> getSupports() { return supports; }
}
