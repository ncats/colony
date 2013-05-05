package tripod.colony;

import java.io.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Collections;
import java.awt.Shape;
import java.awt.Rectangle;

/**
 * perform top-down segmentation based on vertical and horizontal
 * histograms
 */
public class Segmentation {
    private static final Logger logger = 
        Logger.getLogger(Segmentation.class.getName());

    static class Interval {
        int start, end;
        Interval () {}
        Interval (int start) { this.start = start; }
        Interval (int start, int end) { 
            this.start = start;
            this.end = end;
        }
        public String toString () {
            return "Interval{start="+start+",end="+end+"}";
        }
    }

    protected int window; // default window size
    protected double threshold = 1.;

    public Segmentation () {
        this (10);
    }

    public Segmentation (int window) {
        this.window = window;
    }

    public void setWindow (int window) { this.window = window; }
    public int getWindow () { return window; }

    public void setThreshold (double threshold) { this.threshold = threshold; }
    public double getThreshold () { return threshold; }

    protected double[] smooth (int[] signal, double[] stats) {
        double[] smoothed = new double[signal.length];
        double mean = 0, left, right;

        // first, smooth out the signal
        for (int i = 0, j, k; i < signal.length; ++i) {
            // left
            k = Math.max(i - window, 0);
            left = 0.;
            for (j = k; j < i; ++j) 
                left += signal[j];
            if (j > k)
                left /= (j - k);

            // right
            k = Math.min(i+window+1, signal.length);
            right = 0.;
            for (j = i +1; j < k; ++j)
                right += signal[j];
            if (j > i+1)
                right /= (j - i -1);

            smoothed[i] = (left+right)/2.;
            mean += smoothed[i];
        }
        mean /= signal.length;

        double std = 0.;
        for (int i = 0; i < smoothed.length; ++i) {
            double x = smoothed[i] - mean;
            std += x*x;
        }
        std = Math.sqrt(std/smoothed.length);

        if (stats != null) {
            stats[0] = mean;
            stats[1] = std;
        }

        return smoothed;
    }

    protected Interval[] getIntervals (double[] signal) {
        if (signal.length < 2) {
            throw new IllegalArgumentException ("Signal is too small!");
        }

        LinkedList<Interval> intervals = new LinkedList<Interval>();
        for (int i = 1; i < signal.length; ++i) {
            if (signal[i] > threshold && signal[i-1] <= threshold) {
                intervals.push(new Interval (i));
            }
            else if (signal[i] <= threshold && signal[i-1] > threshold) {
                intervals.peek().end = i;
            }
        }
        Collections.reverse(intervals);

        return intervals.toArray(new Interval[0]);
    }

    /**
     * identify the regions of interest based on horizontal & vertical 
     * histograms
     */
    public Shape[] regionsOfInterest 
        (int[] horizontal, int[] vertical) {
        double[] stats = new double[2];
        double[] hz = smooth (horizontal, stats);
        Interval[] hruns = getIntervals (hz);
        /*
        logger.info("horizontal: mean="+stats[0]+" std="
                    +stats[1] + " intervals="+hruns.length);
        for (Interval iv : hruns ) 
            System.err.println(iv);    
        dump ("horizontal_s.txt", hz);
        */

        double[] vt = smooth (vertical, stats);
        Interval[] vruns = getIntervals (vt);
        /*
        logger.info("vertical: mean="+stats[0]+" std="+stats[1]
                    +" intervals="+vruns.length);
        for (Interval iv : vruns ) 
            System.err.println(iv);
        dump ("vertical_s.txt", vt);
        */

        // regions of interest is
        Shape[] roi = new Shape[hruns.length * vruns.length];
        int k = 0;
        for (int i = 0; i < vruns.length; ++i)
            for (int j = 0; j < hruns.length; ++j) {
                roi[k++] = new Rectangle 
                    (vruns[i].start, hruns[j].start, 
                     vruns[i].end - vruns[i].start, 
                     hruns[j].end - hruns[j].start);
            }

        return roi;
    }

    void dump (String name, double[] hist) {
        try {
            PrintWriter pw = new PrintWriter (new FileWriter (name));
            for (int i = 0; i < hist.length; ++i) {
                pw.println(i+" "+hist[i]);
            }
            pw.close();
        }
        catch (IOException ex) { ex.printStackTrace(); }
    }
}
