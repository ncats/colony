package tripod.colony;

public interface Metric<T> {
    double evaluate (T arg0, T arg1);
}
