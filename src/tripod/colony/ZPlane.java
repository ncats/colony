package tripod.colony;

import java.beans.PropertyChangeSupport;
import java.beans.PropertyChangeListener;

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.color.*;
import java.awt.image.*;
import java.awt.event.*;
import javax.swing.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.PathIterator;
import java.awt.geom.NoninvertibleTransformException;
import javax.imageio.*;

import java.util.logging.Logger;
import java.util.logging.Level;

public class ZPlane {
    static final Logger logger = Logger.getLogger(ZPlane.class.getName());
    public static final int MIN_POLYGON_SIZE = 5;

    protected String name;
    protected RenderedImage image; // original image
    protected Raster raster; // original raster

    protected BufferedImage display; // visible image
    protected Bitmap bitmap; // thresholded image
    protected ArrayList<Shape> polygons = new ArrayList<Shape>();
    protected int minPolygonSize = MIN_POLYGON_SIZE;
    protected Map params = new TreeMap ();

    // in terms of um; assume x- and y-resolution are the same
    protected Float resolution;
    protected PropertyChangeSupport pcs = new PropertyChangeSupport (this);
    
    public ZPlane () {
    }
    public ZPlane (String name) {
        this.name = name;
    }

    public ZPlane (File file) throws Exception {
        setImage (file);
    }

    public ZPlane (RenderedImage image) {
        setImage (image);
    }

    public void setName (String name) { this.name = name; }
    public String getName () { return name; }

    public void setMinPolygonSize (int minPolygonSize) {
        this.minPolygonSize = minPolygonSize;
    }
    public int getMinPolygonSize () { return minPolygonSize; }
    public void setResolution (Float resolution) {
        this.resolution = resolution;
    }
    public Float getResolution () { return resolution; }

    public void setImage (File file) throws Exception {
        Map params = new HashMap ();
        RenderedImage image = TIFFCodec.decode(file, params);

        Float xres = (Float)params.get(TIFFTags.TAG_XRESOLUTION);
        Float yres = (Float)params.get(TIFFTags.TAG_YRESOLUTION);

        String unit = (String)params.get(TIFFTags.TAG_RESOLUTIONUNIT);
        logger.info("## resolution: "+xres+" x "+yres+" pixel(s) per "+unit);

        float res = 1f;
        if (unit.equals("cm")) {
            res = 1e4f; // 1cm = 10^4 um
        }
        else if (unit.equals("in")) {
            res = 2.54e4f; // 1in = 2.54cm
        }

        setResolution (Math.min(xres, yres) / res);
        setImage (image);
        name = file.getName(); // default name

        this.params.clear();
        this.params.put("Resolution", 
                        String.format("%1$.3f um", getResolution ()));
        this.params.put("Date", params.get(TIFFTags.TAG_DATETIME));
        this.params.put("Document", params.get(TIFFTags.TAG_DOCUMENTNAME));
        this.params.put("Width", image.getWidth());
        this.params.put("Height", image.getHeight());
        this.params.put("Polygons", polygons.size());
        this.params.put("Total Area", 
                        String.format("%1$.1f um^2", getTotalArea ()));
    }

    public Map getParams () { return params; }

    public void setImage (RenderedImage image) {
        setImage (image, true);
    }

    public void setImage (RenderedImage image, boolean generateBitmap) {
        polygons.clear();
        RenderedImage old = this.image;
        if (image != null) {
            if (image.getSampleModel().getNumBands() != 1) {
                throw new IllegalArgumentException
                    ("Can't handle image with "
                     +image.getSampleModel().getNumBands()+" bands!");
            }
            raster = image.getData();

            RasterStats stats = new RasterStats (raster);
            if (generateBitmap) {
                bitmap = Util.threshold
                    (raster, (int)(stats.getMeanValue()+0.5), false);
                for (Shape s : bitmap.polyConnectedComponents()) {
                    Rectangle r = s.getBounds();
                    if (r.width > minPolygonSize 
                        && r.height > minPolygonSize) {
                        polygons.add(s);
                    }
                    else {
                        logger.warning("Polygon "
                                       +s.getBounds()+" is too small!");
                    }
                }
            }

            logger.info("## "+polygons.size()+" polygons found!");
            setDisplay (Util.rescale(raster, stats));
        }
        else {
            setDisplay (display);
            raster = null;
            bitmap = null;
        }
        this.image = image;
        pcs.firePropertyChange("image", old, image);
    }

    /**
     * Return total area of this z-plane. If the resolution is specified 
     * (as pixels per um), then the area is defined in terms of um. If 
     * no resolution is set, then the area is in terms of pixels.
     */
    public double getTotalArea () {
        double total = 0.;
        for (Shape s : polygons) {
            total += Util.area(s);
        }

        if (resolution != null) {
            total /= resolution;
        }
        return total;
    }

    public int get (int x, int y) {
        return raster.getSample(x, y, 0);
    }
    public int getWidth () { 
        return image != null ? image.getWidth() : 0; 
    }
    public int getHeight () { 
        return image != null ? image.getHeight() : 0; 
    }
    public BufferedImage getDisplay () { return display; }
    public void setDisplay (BufferedImage display) {
        BufferedImage old = this.display;
        this.display = display;
        pcs.firePropertyChange("display", old, display);
    }

    public Collection<Shape> getPolygons () { 
        return Collections.unmodifiableCollection(polygons); 
    }
    public int getPolygonCount () { return polygons.size(); }

    public Bitmap getBitmap () { return bitmap; }
    public RenderedImage getImage () { return image; }


    public void addPropertyChangeListener (PropertyChangeListener l) {
        pcs.addPropertyChangeListener(l);
    }
    public void addPropertyChangeListener (String property, 
                                           PropertyChangeListener l) {
        pcs.addPropertyChangeListener(property, l);
    }
    public void removePropertyChangeListener (PropertyChangeListener l) {
        pcs.removePropertyChangeListener(l);
    }
    public void removePropertyChangeListener (String property, 
                                              PropertyChangeListener l) {
        pcs.removePropertyChangeListener(property, l);
    }

    public String toString () { return getName (); }
}
