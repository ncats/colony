package tripod.colony;

import java.util.*;
import java.awt.Shape;
import java.awt.Polygon;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.geom.*;
import static java.awt.geom.PathIterator.*;
import java.io.*;
import java.util.logging.Logger;
import java.util.logging.Level;


public class GeomUtil {
    private static final Logger logger = 
        Logger.getLogger (GeomUtil.class.getName ());

    private static final boolean DEBUG;
    public static final double EPS = 0.0001;

    static {
        boolean debug = false;
        try {
            debug = Boolean.getBoolean ("geomutil.debug");
        } catch (Exception ex) {
        }
        DEBUG = debug;
    }

    public static double ccw (Point2D p1, Point2D p2, Point2D p3) {
        return (p2.getX () - p1.getX ()) * (p3.getY () - p1.getY ())
            - (p2.getY () - p1.getY ()) * (p3.getX () - p1.getX ());
    }

    public static double angle (Point2D p0, Point2D p1) {
        return angle (p0.getX(), p0.getY(), p1.getX(), p1.getY());
    }

    /**
     * calculate angle between (x0,y0) and (x1,y1) in radian
     */
    public static double angle (double x0, double y0, double x1, double y1) {
        double dx = x1 - x0, dy = y1 - y0;
        if (dx > 0 && dy > 0) {
            return Math.atan (dy / dx);
        }
        else if (dx > 0 && dy == 0) {
            return 0.;
        }
        else if (dx < 0 && dy > 0) {
            return Math.PI - Math.atan(-1. * dy / dx);
        }
        else if (dx < 0 && dy == 0) {
            return Math.PI;
        }
        else if (dx == 0 && dy > 0) {
            return Math.PI / 2;
        }
        else if (dx == 0 && dy < 0) {
            return 3 * Math.PI / 2;
        }
        else if (dx < 0 && dy < 0) {
            return 3 * Math.PI / 2 - Math.atan(dy / dx);
        }
        else if (dx > 0 && dy < 0) {
            return 2 * Math.PI - Math.atan(-1. * dy / dx);
        }
        return 0.;
    }


    /**
     * Graham scan algorithm for convex hull
     */
    public static Polygon convexHull (Point2D... pts) {
        return convexHull (null, pts);
    }

    public static Polygon convexHull (List<Point2D> concave, Point2D... pts) {
        if (pts.length < 3) {
            Polygon poly = new Polygon ();
            for (Point2D pt : pts) {
                poly.addPoint ((int) (pt.getX() + .5), (int) (pt.getY() + .5));
            }
            return poly;
        }

        Point2D anchor = null;
        for (Point2D pt : pts) {
            if (anchor == null || pt.getY() < anchor.getY()) {
                anchor = pt;
            } 
            else if (pt.getY() == anchor.getY()
                     && pt.getX() < anchor.getX()) {
                anchor = pt;
            }
        }

        final Point2D p0 = anchor;
        Arrays.sort(pts, new Comparator<Point2D> () {
                public int compare (Point2D a, Point2D b) {
                    double a0 = angle (p0, a), a1 = angle (p0, b);
                    if (a0 < a1) return -1;
                    if (a0 > a1) return 1;
                    
                    double d0 = a.distance(p0), d1 = b.distance(p0);
                    if (d0 < d1) return -1;
                    if (d0 > d1) return 1;
                    return 0;
                }
            });

        if (DEBUG) {
            logger.info ("Starting point: " + p0);
            logger.info ("Points..." + pts.length);
            for (int i = 0; i < pts.length; ++i) {
                System.err.println (i + ": " + pts[i]);
            }
        }

        LinkedList<Point2D> stack = new LinkedList<> ();
        stack.push(pts[0]);
        stack.push(pts[1]);
        stack.push(pts[2]);
        for (int i = 3; i < pts.length; ++i) {
            Point2D pi = pts[i];
            while (true) {
                Point2D p2 = stack.pop();
                if (!stack.isEmpty()) {
                    Point2D p1 = stack.peek();
                    double dir = ccw (p1, p2, pi);
                    if (DEBUG) {
                        System.err.println
                            ("p1=(" + p1.getX() + "," + p1.getY() + ") p2=("
                             + p2.getX() + "," + p2.getY() + ") p"
                             + i + "=(" + pi.getX() + "," + pi.getY()
                             + ") ccw=" + dir);
                    }
                    
                    if (dir >= 0.) { // push back
                        stack.push(p2);
                        break;
                    }
                    
                    if (DEBUG) {
                        logger.info ("removing " + p2);
                    }
                    
                    if (concave != null)
                        concave.add(p2);
                }
                else
                    break;
            }

            stack.push(pi);
        }

        if (DEBUG) {
            logger.info ("Convex hull: " + stack.size());
        }

        Polygon hull = new Polygon ();
        for (ListIterator<Point2D> it = stack.listIterator (stack.size());
             it.hasPrevious(); ) {
            Point2D pt = it.previous();
            // should prune collinear points
            if (DEBUG) {
                System.err.println (" " + pt);
            }
            hull.addPoint ((int)(pt.getX() + .5), (int)(pt.getY() + .5));
        }

        return hull;
    }

    public static boolean isNeighbor (Point p1, Point p2) {
        return (Math.abs(p1.x - p2.x) <= 1 && Math.abs(p1.y - p2.y) <= 1);
    }

    public static Polygon toPolygon (Shape shape) {
        if (shape instanceof Polygon) {
            return (Polygon)shape;
        }

        return convexHull (vertices (shape));
    }

    public static Polygon toPolygon (Point2D... pts) {
        Polygon pgon = new Polygon ();
        for (Point2D pt : pts)
            pgon.addPoint((int)(pt.getX()+0.5), (int)(pt.getY()+0.5));
        return pgon;
    }

    // create a new convex hull shape from two given shapes
    public static Polygon add (Shape s1, Shape s2) {
        ArrayList<Point2D> pts = new ArrayList<Point2D> ();
        for (Point2D p : vertices (s1)) {
            pts.add (p);
        }
        for (Point2D p : vertices (s2)) {
            pts.add (p);
        }
        return convexHull (pts.toArray (new Point2D[0]));
    }

    public static Point2D[] vertices (Shape shape) {
        return vertices (shape, null);
    }

    public static Point2D[] vertices (Shape shape, AffineTransform afx) {
        PathIterator p = shape.getPathIterator (afx);
        List<Point2D> vertices = new ArrayList<Point2D> ();
        double[] coord = new double[6];
        while (!p.isDone ()) {
            int type = p.currentSegment (coord);
            if (type != PathIterator.SEG_CLOSE) {
                vertices.add (new Point2D.Double (coord[0], coord[1]));
            }
            p.next ();
        }

        return vertices.toArray (new Point2D[0]);
    }

    public static Line2D[] lines (Shape shape) {
        return lines (shape, null);
    }

    public static Line2D[] lines (Shape shape, AffineTransform afx) {
        List<Line2D> lines = new ArrayList<Line2D> ();

        double[] coord = new double[6];
        Point2D start = null, prev = null;

        PathIterator p = shape.getPathIterator (afx);
        while (!p.isDone ()) {
            int type = p.currentSegment (coord);
            if (type == PathIterator.SEG_MOVETO) {
                start = prev = new Point2D.Double (coord[0], coord[1]);
            } 
            else if (type == PathIterator.SEG_CLOSE) {
                if (prev == null || start == null) {
                    logger.warning ("Unexpected state while "
                                    + "iterating over shape!");
                } 
                else {
                    lines.add (new Line2D.Double (prev, start));
                }
            } 
            else {
                Point2D pt = new Point2D.Double (coord[0], coord[1]);
                lines.add (new Line2D.Double (prev, pt));
                prev = pt;
            }
            p.next ();
        }

        return lines.toArray (new Line2D[0]);
    }

    /**
     * return the intersection point (if any) between two lines
     */
    public static Point2D intersection (Line2D l1, Line2D l2) {
        Point2D p1 = l1.getP1 (), p2 = l1.getP2 ();
        Point2D p3 = l2.getP1 (), p4 = l2.getP2 ();

        double c = (p1.getX () - p2.getX ()) * (p3.getY () - p4.getY ())
            - (p1.getY () - p2.getY ()) * (p3.getX () - p4.getX ());

        if (Math.abs (c) < 0.0001)
            return null;


        double x = (p1.getX () * p2.getY () - p1.getY () * p2.getX ())
            * (p3.getX () - p4.getX ()) - (p1.getX () - p2.getX ())
            * (p3.getX () * p4.getY () - p3.getY () * p4.getX ());
        double y = (p1.getX () * p2.getY () - p1.getY () * p2.getX ())
            * (p3.getY () - p4.getY ()) - (p1.getY () - p2.getY ())
            * (p3.getX () * p4.getY () - p3.getY () * p4.getX ());

        return new Point2D.Double (x / c, y / c);
    }

    // calculate line parameters (slope & intercept) of a line
    public static double[] calcLineParams (Line2D line) {
        Point2D p1 = line.getP1 (), p2 = line.getP2 ();
        double intercept = 0, slope = 0;
        if (p1.getX () != p2.getX ()) {
            intercept = (p2.getX () * p1.getY () - p1.getX () * p2.getY ())
                / (p2.getX () - p1.getX ());

            slope = (p1.getY () - intercept) / p1.getX ();
        }

        return new double[]{slope, intercept};
    }

    public static boolean intersects (Line2D line, Point2D pt) {
        return line.ptSegDist (pt) < 0.0001;
    }

    public static boolean intersects (Shape s1, Shape s2) {
        Line2D[] lines1 = lines (s1);
        Line2D[] lines2 = lines (s2);
        for (Line2D l1 : lines1) {
            for (Line2D l2 : lines2) {
                Point2D pt = intersection (l1, l2);
                if (pt != null && intersects (l1, pt) && intersects (l2, pt)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Test whether s2 is contained within s1
     */
    public static boolean contains (Shape s1, Shape s2) {
        Point2D[] vertices = vertices (s2);
        int k = 0;
        for (Point2D p : vertices) {
            if (s1.contains (p))
                ++k;
        }
        return k == vertices.length;
    }

    /**
     * Return two closest vertices between two shapes
     */
    public static Point2D[] nearestNeighborVertices (Shape s1, Shape s2) {
        Point2D[] pts1 = vertices (s1);
        Point2D[] pts2 = vertices (s2);

        double minDist = Double.MAX_VALUE;
        Point2D p1 = null, p2 = null;
        for (int i = 0; i < pts1.length; ++i) {
            for (int j = 0; j < pts2.length; ++j) {
                double dist = length (pts1[i], pts2[j]);
                if (dist < minDist) {
                    p1 = pts1[i];
                    p2 = pts2[j];
                    minDist = dist;
                }
            }
        }

        return new Point2D[]{p1, p2};
    }

    /**
     * Euclidean distance between two points
     */
    public static double length (Point2D pt1, Point2D pt2) {
        double dx = pt1.getX () - pt2.getX (), dy = pt1.getY () - pt2.getY ();
        return Math.sqrt (dx * dx + dy * dy);
    }

    /**
     * Euclidean between two polygons based on nearest vertices distance
     */
    public static double distance (Shape s1, Shape s2) {
        Point2D[] vertex = nearestNeighborVertices (s1, s2);
        return length (vertex[0], vertex[1]);
    }

    public static double centroidDistance (Shape s0, Shape s1) {
        double x0 = s0.getBounds2D().getCenterX();
        double y0 = s0.getBounds2D().getCenterY();
        double x1 = s1.getBounds2D().getCenterX();
        double y1 = s1.getBounds2D().getCenterY();
        double xx = x1 - x0, yy = y1 - y0;
        return Math.sqrt(xx*xx + yy*yy);
    }

    /*
     * return a subset of points that are concave (if any)
     */
    public static Point2D[] concavity (Point2D... pts) {
        List<Point2D> concave = new ArrayList<>();
        // identify all concave points (if any)
        convexHull (concave, pts);
        return concave.toArray(new Point2D[0]);
    }

    /*
     * decompose a given shape into its individual constituent shapes based
     * on the path iterator
     */
    public static Shape[] decompose (Shape shape) {
        PathIterator piter = shape.getPathIterator(null);
        List<Shape> shapes = new ArrayList<>();
        Polygon pgon = null;
        for (double[] coords = new double[6]; !piter.isDone(); ) {
            int type = piter.currentSegment(coords);
            switch (type) {
            case SEG_MOVETO:
                shapes.add(pgon = new Polygon ());
                pgon.addPoint((int)(coords[0]+0.5), (int)(coords[1]+0.5));
                break;
                
            case SEG_CLOSE:
                break;
                
            case SEG_LINETO:
                pgon.addPoint((int)(coords[0]+0.5), (int)(coords[1]+0.5));
                break;
                
            default:
                logger.warning("Unsupport segment type: "+type);
            }
            piter.next();
        }
        
        return shapes.toArray(new Shape[0]);
    }
}
