package tripod.colony;

import java.util.*;
import java.util.logging.*;
import java.awt.Shape;
import java.awt.Rectangle;
import java.awt.geom.*;

/**
 * evaluation metric per the description 
 *   https://www.kaggle.com/c/data-science-bowl-2018#evaluation
 */
public class IntersectionOverUnion {
    static final Logger logger =
        Logger.getLogger(IntersectionOverUnion.class.getName());

    static final double[] THRESHOLDS = new double[]{
        0.5,
        0.55,
        0.6,
        0.65,
        0.7,
        0.75,
        0.8,
        0.85,
        0.9,
        0.95
    };
    
    final List<Shape> nuclei;
    final Bitmap truth;
    
    public IntersectionOverUnion (int width, int height,
                                  Collection<RLE.Run[]> runs) {
        truth = new Bitmap (width, height);
        RLE rle = new RLE (truth);
        nuclei = new ArrayList<>();
        for (RLE.Run[] r : runs) {
            rle.decode(r);
            nuclei.add(new Nucleus (r));
        }
    }

    public IntersectionOverUnion (Bitmap truth) {
        this.truth = truth;
        nuclei = truth.polyConnectedComponents();
    }

    public Bitmap getBitmap () { return truth; }
    public Collection<RLE.Run[]> getMasks () {
        return RLE.encode(nuclei, truth);
    }

    public double precision (Bitmap target) {
        List<Shape> cc = target.polyConnectedComponents();
        if (cc == null)
            return 0.;
        
        double p = 0.;
        for (int i = 0; i < THRESHOLDS.length; ++i) {
            p += precision (target, cc, THRESHOLDS[i]);
        }
        
        // average precision over all threshold values
        return p/THRESHOLDS.length;
    }

    protected double precision (Bitmap target,
                                List<Shape> cc, double threshold) {
        int FP = 0, TP = 0, FN = 0;
        Set<Shape> mapped = new HashSet<>();
        
        for (Shape n : nuclei) {
            Rectangle rs = n.getBounds();
            
            Shape matched = null;
            double maxiou = 0.;
            for (Shape c : cc) {
                if (!mapped.contains(c) && // force a one-to-one mapping?
                    n.intersects(c.getBounds2D())) {
                    Rectangle rc = c.getBounds();
                    int x0 = Math.min(rs.x, rc.x);
                    int x1 = x0 + Math.max(rs.width, rc.width);
                    int y0 = Math.min(rs.y, rc.y);
                    int y1 = y0 + Math.max(rs.height, rc.height);
                    int ab = 0, a = 0, b = 0;
                    for (; x0 < x1; ++x0) {
                        for (int y = y0; y < y1; ++y) {
                            boolean inS = truth.isOn(x0, y);
                            boolean inC = target.isOn(x0, y);
                            if (inS && inC) {
                                ++ab;
                            }
                            else if (inS) {
                                ++a;
                            }
                            else if (inC) {
                                ++b;
                            }
                        }
                    }
                    
                    if (ab > 0) {
                        double iou = (double)ab/(ab+a+b);
                        if (matched == null || iou > maxiou) {
                            matched = c;
                            maxiou = iou;
                        }
                    }
                }
            }
            
            if (matched != null) {
                if (maxiou >= threshold)
                    ++TP; // true positive
                mapped.add(matched);
            }
            else {
                ++FN; // false negative
            }
        }

        // now for all connected components in cc that aren't mapped
        for (Shape c : cc) {
            if (!mapped.contains(c))
                ++FP;
        }
        
        return TP > 0 ? ((double)TP/(TP+FP+FN)) : 0.0;
    }
}
