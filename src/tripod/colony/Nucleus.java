package tripod.colony;

import java.util.List;
import java.util.ArrayList;
import java.io.Serializable;
import java.awt.Polygon;
import java.awt.Shape;
import java.awt.Rectangle;
import java.awt.geom.*;

public class Nucleus implements Shape, Serializable {
    private static final long serialVersionUID = 0x123l;

    public final String label;
    public final Polygon polygon;
    public final Line2D[] lines;
    public final double area;
    public final Double prob;

    public Nucleus (String label, RLE.Run[] masks) {
        this (label, masks, null);
    }
    
    public Nucleus (RLE.Run[] masks) {
        this (null, masks, null);
    }
    
    public Nucleus (String label, RLE.Run[] masks, Double prob) {
        if (masks == null) {
            throw new IllegalArgumentException
                ("No masks defined for nucleus!");
        }
        this.label = label;
        this.prob = prob;

        this.lines = new Line2D[masks.length];
        double a = 0.;
        List<Point2D> pts = new ArrayList<>();        
        for (int i = 0; i < masks.length; ++i) {
            RLE.Run r  = masks[i];
            Line2D line = r.line();
            lines[i] = line;
            pts.add(line.getP1());
            if (r.len > 1)
                pts.add(line.getP2());
            a += r.len;
        }
        this.area = a;
        this.polygon = GeomUtil.convexHull(pts.toArray(new Point2D[0]));
    }

    /*
     * Shape interface
     */
    public boolean contains (double x, double y) {
        return polygon.contains(x, y);
    }
    public boolean contains (double x, double y, double w, double h) {
        return polygon.contains(x, y, w, h);
    }
    public boolean contains (Point2D p) { return polygon.contains(p); }
    public boolean contains (Rectangle2D r) {
        return polygon.contains(r);
    }
    public Rectangle getBounds () { return polygon.getBounds(); }
    public Rectangle2D getBounds2D () { return polygon.getBounds2D(); }
    public PathIterator getPathIterator (AffineTransform at) {
        return polygon.getPathIterator(at);
    }
    public PathIterator getPathIterator (AffineTransform at, double flatness) {
        return polygon.getPathIterator(at, flatness);
    }
    public boolean intersects (double x, double y, double w, double h) {
        return polygon.intersects(x, y, w, h);
    }
    public boolean intersects (Rectangle2D r) {
        return polygon.intersects(r);
    }
}
