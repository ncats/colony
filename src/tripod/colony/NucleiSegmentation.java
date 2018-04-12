package tripod.colony;

import java.io.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.awt.Shape;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.*;
import javax.imageio.*;
import java.awt.geom.*;
import javax.swing.tree.*;

import static tripod.colony.Grayscale.Channel;
import org.apache.commons.math3.stat.regression.SimpleRegression;
import org.apache.commons.math3.stat.regression.RegressionResults;


public class NucleiSegmentation extends DefaultTreeModel {
    private static final Logger logger = Logger.getLogger
        (NucleiSegmentation.class.getName());

    /*
     * minimum area for a region
     */
    static final int MINAREA = 1;
    static final int MINPATH = 5;
    static final double MAXERR = 30.0;
    static final double AMS = 1.5; // absoluyte min score

    public static class Region implements Shape, Comparable<Region> {
        public final double meanp; // pixel mean
        public final double varp; // pixel variance
        public final int area;
        public final Shape geom;
        public final double snr;

        Region (Raster raster, Shape geom) {
            Rectangle r = geom.getBounds();
            int x = r.x+r.width, y = r.y+r.height, k = 0;
            double m = 0., v = 0., m0;
            int min = Integer.MAX_VALUE, max = 0;
            for (int i = r.x; i < x; ++i) {
                for (int j = r.y; j < y; ++j) {
                    if (geom.contains(i, j)) {
                        int z = raster.getSample(i, j, 0) & 0xff;
                        m0 = m;
                        m += (z - m) / ++k;
                        v += (z - m0) * (z - m);
                    }
                }
            }
            
            if (k > 0)
                v /= k;

            area = k;
            meanp = m;
            varp = v;
            snr = v > 0. ? (m / Math.sqrt(v)) : 0.;
            
            this.geom = geom;
        }

        public boolean equals (Region r) {
            return geom.getBounds().equals(r.geom.getBounds());
        }

        public int compareTo (Region r) {
            Rectangle r1 = getBounds ();
            Rectangle r2 = r.getBounds();
            int d = r1.x - r2.x;
            if (d == 0)
                d = r1.y - r2.y;
            if (d == 0)
                d = r1.width - r2.width;
            if (d == 0)
                d = r1.height - r2.height;
            return d;
        }

        public boolean contains (Region r) {
            Rectangle r0 = geom.getBounds(), r1 = r.geom.getBounds();
            boolean contains = false;
            if (r0.x <= r1.x && r0.y <= r1.y
                && (r0.x+r0.width) >= (r1.x+r1.width)
                && (r0.y+r0.height) >= (r1.y+r1.height)) {
                Area area = new Area (geom);
                area.intersect(new Area (r.geom));
                contains = area.getBounds().equals(r1);
            }
            return contains;
        }
        
        public boolean _contains (Region r) {
            PathIterator pi = r.geom.getPathIterator(null);
            double[] coord = new double[6];

            int np = 0;
            while (!pi.isDone()) {
                switch (pi.currentSegment(coord)) {
                case PathIterator.SEG_CUBICTO:
                    if (!geom.contains(coord[4], coord[5]))
                        return false;
                    // fall through
                    
                case PathIterator.SEG_QUADTO:
                    if (!geom.contains(coord[2], coord[3]))
                        return false;
                    // fall through
                    
                case PathIterator.SEG_MOVETO:
                case PathIterator.SEG_LINETO:
                    if (!geom.contains(coord[0], coord[1]))
                        return false;
                }
                pi.next();
                ++np;
            }
            
            return np > 0;
        }

        /*
         * Shape interface
         */
        public boolean contains (double x, double y) {
            return geom.contains(x, y);
        }
        public boolean contains (double x, double y, double w, double h) {
            return geom.contains(x, y, w, h);
        }
        public boolean contains (Point2D p) { return geom.contains(p); }
        public boolean contains (Rectangle2D r) {
            return geom.contains(r);
        }
        public Rectangle getBounds () { return geom.getBounds(); }
        public Rectangle2D getBounds2D () { return geom.getBounds2D(); }
        public PathIterator getPathIterator (AffineTransform at) {
            return geom.getPathIterator(at);
        }
        public PathIterator getPathIterator (AffineTransform at,
                                             double flatness) {
            return geom.getPathIterator(at, flatness);
        }
        public boolean intersects (double x, double y, double w, double h) {
            return geom.intersects(x, y, w, h);
        }
        public boolean intersects (Rectangle2D r) {
            return geom.intersects(r);
        }
        
        public String toString () {
            return "Region{mean="+meanp+",var="+varp+",bounds="+geom+"}";
        }
    }

    public static class Layer {
        public final Bitmap bitmap;
        public final int threshold;
        public final Region[] regions;
        public final double meanr;
        public final double varr;

        Layer (Raster raster, int threshold) {
            bitmap = Util.threshold(raster, threshold);
            List<Shape> cc = bitmap.polyConnectedComponents();
            int k = 0;
            double m = 0., m0, v = 0., maxr = 0.;
            regions = new Region[cc.size()];
            for (Shape s : cc) {
                Region r = new Region (raster, s);
                m0 = m;
                m += (r.area - m)/(k+1);
                v += (r.area - m0)*(r.area - m);
                regions[k++] = r;
            }
            
            if (k > 0)
                v /= k;

            meanr = m;
            varr = v;
            
            this.threshold = threshold;
        }
        
        public String toString () {
            return "Layer{threshold="+threshold+",regions="+regions.length
                +",mean="+meanr+",var="+varr+"}";
        }
    }

    public static class Segment implements TreeNode, Comparable<Segment> {
        public Segment parent;
        public int depth;
        public Region region; // region
        public List<Segment> children = new ArrayList<>();
        public final Layer layer;
        public final Bitmap bitmap;

        Segment (Layer layer, Region region) {
            this.layer = layer;
            this.region = region;
            bitmap = layer != null ? layer.bitmap.crop(region) : null;
        }

        Segment (Segment copy) {
            this.layer = copy.layer;
            this.region = copy.region;
            this.depth = copy.depth;
            this.bitmap = copy.bitmap;
        }

        public int threshold () {
            return layer != null ? layer.threshold : -1;
        }
        public int area () { return region.area; }
        public void add (Segment child) {
            child.parent = this;
            children.add(child);
            updateDepth (child, depth + 1);
        }

        static void updateDepth (Segment seg, int depth) {
            seg.depth = depth;
            for (Segment child : seg.children)
                updateDepth (child, depth + 1);
        }
        
        public void remove (Segment child) {
            children.remove(child);
        }
        
        public void remove () {
            if (parent != null)
                parent.remove(this);
        }

        public int compareTo (Segment seg) {
            int d = region.area - seg.region.area;

            if (d == 0)
                d = region.compareTo(seg.region);
            
            if (d == 0)
                d = threshold() - seg.threshold();

            return d;
        }
        
        public boolean contains (Segment seg) {
            return region.contains(seg.region);
        }

        public boolean contains (double x, double y) {
            return region.getBounds().contains(x, y);
        }

        public List<Segment> getTerminalSegments () {
            return NucleiSegmentation.getTerminalSegments(this);
        }

        public List<Segment> getSingletonSegments () {
            List<Segment> singletons = new ArrayList<>();
            for (Segment seg : NucleiSegmentation.getTerminalSegments(this)) {
                if (seg.parent == null || seg.parent.getChildCount() == 1)
                    singletons.add(seg);
            }
            return singletons;
        }
        
        public Enumeration children () {
            return Collections.enumeration(children);
        }

        public boolean getAllowsChildren () { return true; }
        public TreeNode getChildAt (int child) {
            return children.get(child);
        }
        public int getChildCount () { return children.size(); }
        public int getIndex (TreeNode child) {
            return children.indexOf(child);
        }
        public TreeNode getParent () { return parent; }
        public boolean isLeaf () { return children.isEmpty(); }
        public boolean isRoot () { return parent == null; }

        public Segment[] getRootPath () {
            Segment[] path = new Segment[depth];
            int k = 0;
            for (Segment p = this; p.parent != null; p = p.parent)
                path[k++] = p;
            return path;
        }
        
        /*
         * get the path to the next branch in the hierarchy. a branch is a
         * segment with more than one child.
         */
        public Segment[] getBranchAncestorPath () {
            List<Segment> path = new ArrayList<>();
            path.add(this);
            if (children.size() > 1) {
                // we're at a branch
            }
            else {
                for (Segment p = parent; p != null && !p.isRoot(); ) {
                    path.add(p);
                    if (p.children.size() > 1)
                        break;
                    p = p.parent;
                }
            }
            return path.toArray(new Segment[0]);
        }

        public Point2D[] dominantPoints () {
            return bitmap.trace().dominantPoints().toArray(new Point2D[0]);
        }

        public int getX () { return region.getBounds().x; }
        public int getY () { return region.getBounds().y; }

        public void print (PrintStream ps) {
            if (depth == 0) {
                ps.print("r");
            }
            else {
                for (int i = 0; i < depth; ++i)
                    ps.print(" ");
            }
            
            Rectangle r = region.getBounds();
            ps.println(depth+": t="+layer+" r=[x="+r.x+",y="+r.y+",width="
                       +r.width+",height="+r.height+"]");
            for (Segment s : children)
                s.print(ps);
        }

        public String toString () {
            Rectangle r = region.getBounds();            
            return "("+children.size()+") d:"+depth+" t:"+threshold()
                +" r:[x="+r.x+",y="+r.y+",w="+r.width
                +",h="+r.height+"] a:"+region.area
                +" m:"+String.format("%1$.3f", region.meanp)
                +" v:"+String.format("%1$.3f", region.varp)
                +" snr:"+String.format("%1$.3f", region.snr)
                +(parent!= null ? (" ar:"+String.format
                                   ("%1$.3f", (double)region.area
                                    /parent.region.area)):"");
        }
    }

    static class SegmentFit {
        Segment start, stop;
        Double slope;
        Double err;
        int len;
        SegmentFit () {}
        SegmentFit (Segment seg) { start = seg; }
        
        public Double score () {
            return slope != null ? len / slope : null;
        }
    }

    public Channel channel;
    public Raster raster;
    public final double maxerr; // maximum error to break each segment
    public final int minpath; // required minimum path length 
    public final double ams; // absolute min score

    public NucleiSegmentation () {
        this (MAXERR, MINPATH, AMS);
    }
    
    public NucleiSegmentation (double maxerr, int minpath, double ams) {
        super (null);
        this.maxerr = maxerr;
        this.minpath = minpath;
        this.ams = ams;
    }

    public void segment (Raster raster) {
        segment (new Grayscale (raster).getChannel());
    }
    
    public void segment (Channel channel) {
        this.channel = channel;
        this.raster = channel.raster();

        setRoot (new Segment
                 (null, new Region (raster,
                                    new Rectangle (0, 0, channel.width,
                                                   channel.height))));
        
        int pmin = channel.pmin+1, pmax = channel.pmax-1;
        Segment[] segments;
        { List<Segment> segs = new ArrayList<>();
            for (int t = pmin; t <= pmax; ++t) {
                Layer layer = new Layer (raster, t);                
                logger.info("...processing layer "+t+"/"
                            +pmax+".. "+layer.regions.length);

                for (Region r : layer.regions) {
                    if (r.area > MINAREA)
                        segs.add(new Segment (layer, r));
                }
            }
            Collections.sort(segs);
                
            // now prune identical segments regardless of layers
            List<Segment> remove = new ArrayList<>();
            Segment p = null;
            for (Segment s : segs) {
                if (p != null) {
                    if (p.region.equals(s.region))
                        remove.add(p);
                }
                p = s;
            }
                
            for (Segment s : remove)
                segs.remove(s);
                
            segs.add(getRoot ());
            segments = segs.toArray(new Segment[0]);
        }

        logger.info("... constructing segment hierarchy");
        for (int i = 0; i < segments.length; ++i) {
            for (int j = i+1; j < segments.length; ++j) {
                if (segments[j].contains(segments[i])) {
                    segments[j].add(segments[i]);
                    break;
                }
            }
        }

        logger.info("... pruning segments");
        for (List<Segment> pruned;
             !(pruned = pruneSegments (getRoot (), minpath)).isEmpty(); ) {
            logger.info("..... "+pruned.size()+" segment(s) pruned!");
        }

        analyzeSegments ();

        fireTreeStructureChanged (this, null, null, null);
    }

    public NucleiSegmentation filter (double x, double y) {
        NucleiSegmentation nuseg = new NucleiSegmentation ();
        nuseg.channel = channel;
        nuseg.raster = raster;
        
        List<Segment> matched = new ArrayList<>();
        filter (matched, getRoot (), x, y);
        nuseg.setRoot(matched.get(0));
        
        Collections.sort(matched);
        for (int i = 0; i < matched.size(); ++i) {
            Segment si = matched.get(i);
            for (int j = i+1; j < matched.size(); ++j) {
                Segment sj = matched.get(j);
                if (sj.contains(si)) {
                    sj.add(si);
                    break;
                }
            }
        }

        return nuseg;
    }

    static void filter (List<Segment> matched,
                        Segment seg, double x, double y) {
        if (seg.contains(x, y))
            matched.add(new Segment (seg));
        for (Segment s : seg.children)
            filter (matched, s, x, y);
    }

    @Override
    public Segment getRoot () { return (Segment)super.getRoot(); }

    public void debug (String file) {
        try {
            PrintStream ps = new PrintStream
                (new FileOutputStream (file));
            ps.println("#### "+getRoot().getChildCount()+" segments!");
            for (Segment s : ((Segment)getRoot()).children)
                s.print(ps);
            ps.close();
        }
        catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public List<Segment> getTerminalSegments () {
        return getTerminalSegments (getRoot());
    }
    
    public static List<Segment> getTerminalSegments (Segment seg) {
        List<Segment> leafs = new ArrayList<>();
        getTerminalSegments (leafs, seg);
        return leafs;
    }
    
    static void getTerminalSegments (List<Segment> leafs, Segment seg) {
        if (seg.isLeaf()) {
            leafs.add(seg);
        }
        else {
            for (Segment child : seg.children)
                getTerminalSegments (leafs, child);
        }
    }

    public static void debugSegment (Segment seg, double maxerr)
        throws IOException {
        String name = "seg."+seg.depth+"."+seg.threshold();
        FileOutputStream fos = new FileOutputStream (name+".txt");
        PrintStream ps = new PrintStream (fos);
        
        Segment[] path = seg.getRootPath();
        double m = 0.;
        SimpleRegression r = new SimpleRegression ();        
        for (int i = 0, k = 0; i < path.length; ++i) {
            Segment p = path[i];
            m += (p.area() - m)/(i+1);
            //double x = i == 0 ? 0. : (p.region.varp - path[i-1].region.varp);
            ps.print(p.threshold()+" "+p.area()+" "+String.format("%1$.1f", m)
                     +" m:"+String.format("%1$.1f", p.region.meanp)
                     +" v:"+String.format("%1$.1f", p.region.varp)
                     +" snr:"+String.format("%1$.1f", p.region.snr));
            
            if (p.getChildCount() > 1) {
                ps.print(" +"+p.getChildCount());
            }
            
            if (i - k > 1) {
                r.clear();
                for (int j = k; j <= i; ++j)
                    r.addData(path[j].threshold(), path[j].area());
                double y = r.predict(path[i].threshold());
                double err = Math.abs(y - path[i].area());
                ps.print(" s:"+r.getSlope()
                         +" a:"+y+" err:"+err
                         +" mse: "+r.getMeanSquareError()
                         +" sse: "+r.getSumSquaredErrors()
                         +" r^2: "+r.getR()
                         +" N:"+r.getN());
                if (err > maxerr) {
                    k = i;
                    ps.print(" break!");
                }
            }
            ps.println();            
        }
        ps.close();
    }
    
    static SegmentFit extendSegment (Segment seg, double maxerr, int minlen) {
        if (minlen < 2)
            throw new IllegalArgumentException ("minlen must be >= 2");
        
        Segment[] path = seg.getRootPath();
        SegmentFit fit = null;
        if (path.length >= minlen) {
            fit = new SegmentFit (seg);
            SimpleRegression r = new SimpleRegression ();
            
            for (int i = minlen-1; i < path.length; ++i) {
                r.clear();
                for (int j = 0; j <= i; ++j)
                    r.addData(path[j].threshold(), path[j].area());
                fit.stop = path[i-1]; // don't include the current segment
                double y = r.predict(path[i].threshold());
                double err = Math.abs(y - path[i].area());
                fit.slope = r.getSlope();
                fit.err = err;
                if (err > maxerr) {
                    fit.len = i;
                    break;
                }
            }
        }
        
        return fit;
    }

    static List<Segment> pruneSegments (Segment segment, int minpath) {
        List<Segment> pruned = new ArrayList<>();
        List<Segment> leafs = getTerminalSegments (segment);
        
        for (Segment leaf : leafs) {
            Segment[] path = leaf.getBranchAncestorPath();
            if (path.length <= minpath) {
                leaf.remove(); // prune this leaf..
                pruned.add(leaf);
            }
        }
        return pruned;
    }

    void analyzeSegments () {
        logger.info("... candidate segments");

        List<Segment> leafs = getTerminalSegments (getRoot ());
        Collections.sort(leafs, (a, b) -> {
                Segment[] pa = a.getRootPath();
                Segment[] pb = b.getRootPath();
                int d = pb.length - pa.length;
                if (d == 0) {
                    pa = a.getBranchAncestorPath();
                    pb = b.getBranchAncestorPath();
                    d = pb.length - pa.length;
                }
                if (d == 0)
                    d = b.area() - a.area();
                if (d == 0)
                    d = a.getX() - b.getX();
                if (d == 0)
                    d = a.getY() - b.getY();
                return d;
            });

        // do first pass to determine directionality
        int npos = 0, nneg = 0;
        List<SegmentFit> segments = new ArrayList<>();
        for (Segment seg : leafs) {            
            SegmentFit fit = extendSegment (seg, maxerr, minpath);
            if (fit != null) {
                if (fit.slope > 0.) ++npos;
                else if (fit.slope < 0.) ++nneg;
                segments.add(fit);
            }
        }
        
        logger.info("... segments = "+segments.size()
                    +" npos="+npos+" nneg="+nneg);
        int k = 0;
        for (SegmentFit fit : segments) {
            if ((fit.slope > 0 && npos > nneg)
                || (fit.slope < 0 && npos < nneg)) {
                logger.info(String.format("%1$5d: ", ++k)+" "+fit.start);
                Segment seg = fit.start;
                for (SegmentFit sf;
                     (sf = extendSegment
                      (seg, maxerr, minpath)) != null; ) {
                    logger.info("       ["+seg.threshold()+","
                                +sf.stop.threshold()+"]: slope="+sf.slope
                                +" len/slope="+sf.score()
                                +(Math.abs(sf.score()) > AMS ? " **":""));
                    seg = sf.stop.parent;
                }
            }
        }
    }
    
    public static void main (String[] argv) throws Exception {
        if (argv.length == 0) {
            System.err.println("Usage: "+NucleiSegmentation.class.getName()
                               +" IMAGES...");
            System.exit(1);
        }
        
        NucleiSegmentation ns = new NucleiSegmentation ();
        for (int i = 0; i < argv.length; ++i) {
            System.out.println("------ " +argv[i]+" ------");
            RenderedImage image = ImageIO.read(new File (argv[i]));
            ns.segment(image.getData());
        }
    }
}

