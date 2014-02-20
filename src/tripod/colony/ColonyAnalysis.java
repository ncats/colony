package tripod.colony;

import java.io.*;
import java.beans.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.awt.Shape;
import java.awt.RenderingHints;
import java.awt.image.*;
import javax.imageio.*;
import java.awt.geom.*;

public class ColonyAnalysis {
    private static final Logger logger = Logger.getLogger
        (ColonyAnalysis.class.getName());

    /**
     * Image type
     */
    public enum Type {
        Raster, // original input raster
            Rescaled, // 8-bit grayscale rescaled based on desired range
            Threshold, // binary image apply threshold to Normalized
            Skeleton // thinned image
            ;
    }


    public static final int DEFAULT_RANGE = 6000;
    public static final int DEFAULT_MAX_RANGE = 10000;
    public static final int DEFAULT_THRESHOLD = 160;

    protected PropertyChangeSupport pcs = new PropertyChangeSupport (this);
    protected Map<Type, BufferedImage> imageStack = 
        new HashMap<Type, BufferedImage>();

    protected Bitmap bitmap; // binary image as a result of thresholding
    protected Bitmap skeleton; // skeleton of bitmap
    protected Raster raster; // current raster


    /** 
     * Analysis parameters
     */
    protected int range = DEFAULT_RANGE; // dynamic range of grayscale
    protected int threshold = DEFAULT_THRESHOLD; // grayscale threshold
    protected RasterStats stats;

    protected List<Shape> polygons = new ArrayList<Shape>();
    protected List<Path2D> segments = new ArrayList<Path2D>();
    protected Collection<Colony> colonies;

    /**
     * analysis progress
     */ 
    protected volatile int progress;

    public ColonyAnalysis () {
    }

    public void setRange (int range) {
        this.range = range;
    }
    public int getRange () { return range; }

    public void setThreshold (int threshold) { this.threshold = threshold; }
    public int getThreshold () { return threshold; }

    public void setRaster (Raster raster) {
        if (raster == null) {
            throw new IllegalArgumentException ("Input raster is null");
        }
        if (raster.getNumBands() > 1) {
            logger.warning("Input raster has "+raster.getNumBands()
                           +" samples per pixel; using only first sample!");
        }

        this.raster = raster;
        this.stats = new RasterStats (raster);

        // create a grayscale of the input raster
        BufferedImage img = new BufferedImage 
            (raster.getWidth(), raster.getHeight(), 
             BufferedImage.TYPE_USHORT_GRAY);
        img.setData(raster);
        BufferedImage old = imageStack.put(Type.Raster, img);
        firePropertyChange ("raster", old, img);
    }
    public Raster getRaster () { return raster; }

    public void clear () {
        bitmap = null;
        skeleton = null;
        imageStack.clear();
    }

    /**
     * perform colony analysis; ideally should be done in the 
     * background?
     */
    public ColonyAnalysis analyze () {
        if (getRaster () == null) {
            throw new IllegalStateException ("No raster available!");
        }

        applyRescale (); // rescale input raster to 8-bit grayscale
        applyThreshold (); // thresold to generate binary image
        applyThinning (); // 
        extractPolygons ();
        extractSegments ();
        extractColonies ();

        return this;
    }

    protected void applyRescale () {
        long start = System.currentTimeMillis();
        BufferedImage img = Util.rescale(getRaster (), stats, range);
        BufferedImage old = imageStack.put(Type.Rescaled, img);
        logger.info("## image rescale in "
                    +String.format("%1$.3fs", 
                                   (System.currentTimeMillis()-start)*1e-3));
        firePropertyChange ("rescale", old, img);
    }

    protected void applyThreshold () {
        BufferedImage rescaled = imageStack.get(Type.Rescaled);
        if (rescaled == null) {
            throw new IllegalStateException
                ("No rescaled image available to apply threshold!");
        }
        long start = System.currentTimeMillis();
        bitmap = Util.threshold(rescaled.getData(), threshold);
        BufferedImage img = bitmap.createBufferedImage();
        BufferedImage old = imageStack.put(Type.Threshold, img);
        logger.info("## image thresholding ("+threshold+") in "
                    +String.format("%1$.3fs", 
                                   (System.currentTimeMillis()-start)*1e-3));
        firePropertyChange ("threshold", old, img);
    }

    
    protected void applyThinning () {
        if (bitmap == null) {
            throw new IllegalStateException
                ("No bitmap available to apply thinning!");
        }
        long start = System.currentTimeMillis();
        skeleton = bitmap.thin();
        BufferedImage img = skeleton.createBufferedImage();
        BufferedImage old = imageStack.put(Type.Skeleton, img);
        logger.info("## image thinning in "
                    +String.format("%1$.3fs", 
                                   (System.currentTimeMillis()-start)*1e-3));
        firePropertyChange ("skeleton", old, img);
    }

    protected void extractPolygons () {
        if (bitmap == null) {
            throw new IllegalStateException
                ("No bitmap available to extract polygons!");
        }
        List<Shape> old = polygons;
        long start = System.currentTimeMillis();
        polygons = bitmap.connectedComponents(Bitmap.Bbox.Polygon);
        logger.info("## "+polygons.size()+" connected components in "
                    +String.format("%1$.3fs", 
                                   (System.currentTimeMillis()-start)*1e-3));
        firePropertyChange ("polygons", old, polygons);
    }

    protected void extractSegments () {
        if (skeleton == null) {
            throw new IllegalStateException
                ("No skeleton available to extract segemnts!");
        }
        List<Path2D> old = segments;
        long start = System.currentTimeMillis();
        segments = skeleton.segments();
        logger.info("## "+segments.size()+" segments in "
                    +String.format("%1$.3fs", 
                                   (System.currentTimeMillis()-start)*1e-3));
        firePropertyChange ("segments", old, segments);
    }
    
    /**
     * This is a top-down segmentation approach that uses histograms
     * to identify the plates then connected components are colonies.
     */
    protected void extractColonies () {
        if (polygons == null || polygons.isEmpty()) {
            throw new IllegalStateException
                ("No connected components available to extract colonies!");
        }

        Collection<Colony> old = colonies;
        /*
        TransitiveClosure tc = new TransitiveClosure ();
        colonies = tc.closure(polygons);
        */
        long start = System.currentTimeMillis();
        Segmentation seg = new Segmentation ();
        Shape[] roi = seg.regionsOfInterest
            (bitmap.horizontalHistogram(), bitmap.verticalHistogram());
        colonies = new ArrayList<Colony>();
        for (Shape r : roi) {
            Colony colony = new Colony ();
            for (Shape p : polygons) 
                if (r.intersects(p.getBounds()))
                    colony.add(new Colony (p));
            colonies.add(colony);
        }
        logger.info("## "+colonies.size()+" colonies in "
                    +String.format("%1$.3fs", 
                                   (System.currentTimeMillis()-start)*1e-3));
        firePropertyChange ("colonies", old, colonies);
    }

    public void applyThreshold (int threshold) {
        
    }

    public BufferedImage getImage () {
        return getImage (Type.Raster);
    }
    public BufferedImage getImage (Type type) {
        return imageStack.get(type);
    }

    public Collection<Shape> getPolygons () { return polygons; }
    public Collection<Colony> getColonies () { return colonies; }
    public Collection<Path2D> getSegments () { return segments; }

    public void addPropertyChangeListener (PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }

    public void removePropertyChangeListener (PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }

    protected void firePropertyChange 
        (String property, Object oldVal, Object newVal) {
        pcs.firePropertyChange(property, oldVal, newVal);
    }

    public static void main (String[] argv) throws Exception {
    }
}
