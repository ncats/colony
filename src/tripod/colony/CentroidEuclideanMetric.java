package tripod.colony;

import java.awt.Shape;

public class CentroidEuclideanMetric<T extends Shape> implements Metric<T> {
    public CentroidEuclideanMetric () {}

    public double evaluate (T s0, T s1) {
        return GeomUtil.centroidDistance(s0, s1);
    }
}
