package tripod.colony;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.awt.RenderingHints;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.awt.Shape;
import java.awt.geom.PathIterator;
import java.awt.geom.AffineTransform;


public class Util {
    private static final Logger logger = Logger.getLogger
        (Util.class.getName());

    public static final int DEFAULT_RANGE = 6000;
    public static final int DEFAULT_MAX_RANGE = 10000;
    public static final int DEFAULT_THRESHOLD = 160;
    
    private Util () {}

    static public BufferedImage rescale (Raster raster) {
        RasterStats stats = new RasterStats (raster);
        logger.info("## raster stats: "+stats);
        return rescale (raster, stats, stats.getRange());
    }

    static public BufferedImage rescale (Raster raster, int range) {
        RasterStats stats = new RasterStats (raster);
        logger.info("## raster stats: "+stats);
        return rescale (raster, stats, range);
    }

    static public BufferedImage rescale (Raster raster, RasterStats stats) {
        logger.info("## raster stats: "+stats);
        return rescale (raster, stats, stats.getRange());
    }

    static public BufferedImage rescale 
        (Raster raster, RasterStats stats, int range) {
        if (range <= 0) {
            // probably doesn't make sense to the range bigger
            range = Math.min(stats.getRange(), DEFAULT_MAX_RANGE);
        }
        int min = Math.max(0, stats.getMaxValue() - range);

        logger.info("## range="+range+"; min="
                    +min+"["+stats.getCount(min)
                    +"]; max="+stats.getMaxValue()
                    +"["+stats.getCount(stats.getMaxValue())+"]");

        RenderingHints hints = new RenderingHints
            (RenderingHints.KEY_ANTIALIASING, 
             RenderingHints.VALUE_ANTIALIAS_ON);
        hints.put(RenderingHints.KEY_RENDERING, 
                  RenderingHints.VALUE_RENDER_QUALITY);
        hints.put(RenderingHints.KEY_INTERPOLATION, 
                  RenderingHints.VALUE_INTERPOLATION_BICUBIC);

        // now rescaling the raster
        BufferedImage rescaled = new BufferedImage 
            (raster.getWidth(), raster.getHeight(), 
             BufferedImage.TYPE_BYTE_GRAY);

        // rescale to 8-bit
        double scale = 256./(range+1);
        RescaleOp op = new RescaleOp 
            ((float)scale, (float)-scale*min, hints);
        op.filter(raster, rescaled.getRaster());

        return rescaled;
    }

    /**
     * perform (unsigned) dithering from 16 bits to 8
     */
    static public BufferedImage dither8 (Raster raster) {
        // dithered raster
        BufferedImage dither = new BufferedImage 
            (raster.getWidth(), raster.getHeight(), 
             BufferedImage.TYPE_BYTE_GRAY);
        WritableRaster dst = dither.getRaster();
        for (int i = 0; i < raster.getWidth(); ++i)
            for (int j = 0; j < raster.getHeight(); ++j) {
                // unsigned short 
                int sample = raster.getSample(i, j, 0);
                dst.setSample(i, j, 0, sample & 0xff);
            }
        return dither;
    }

    static public Bitmap threshold (Raster raster, int threshold) {
        // binary thresholding
        int low = 0;
        for (int i = 0; i < raster.getWidth(); ++i) 
            for (int j = 0; j < raster.getHeight(); ++j) {
                int p = raster.getSample(i, j, 0);
                if (p < threshold)
                    ++low;
            }
        int majority = raster.getWidth()*raster.getHeight()/3;
        return threshold (raster, threshold, low < majority);
    }

    static public Bitmap threshold (Raster raster, 
                                    int threshold, boolean inverted) {
        Bitmap bitmap = new Bitmap (raster.getWidth(), raster.getHeight()); 

        // binary thresholding
        for (int i = 0; i < raster.getWidth(); ++i) 
            for (int j = 0; j < raster.getHeight(); ++j) {
                int p = raster.getSample(i, j, 0);
                bitmap.set(i, j, inverted ? p < threshold : p > threshold);
            }
        return bitmap;
    }

    static public boolean checkContainment 
        (Shape container, Shape containee, AffineTransform afx) {

        PathIterator it = containee.getPathIterator(afx);
        double[] seg = new double[6];
        while (!it.isDone()) {
            int type = it.currentSegment(seg);
            switch (type) {
            case PathIterator.SEG_LINETO:
                if (!container.contains(seg[0], seg[1]))
                    return false;

                break;

            case PathIterator.SEG_QUADTO:
                if (!container.contains(seg[0], seg[1])
                    || !container.contains(seg[2], seg[3]))
                    return false;
                break;

            case PathIterator.SEG_CUBICTO:
                if (!container.contains(seg[0], seg[1])
                    || !container.contains(seg[2], seg[3])
                    || !container.contains(seg[4], seg[5]))
                    return false;
                break;
            }
            it.next();
        }

        return true;
    }

    /**
     * calculate area of a general (not necessary convex) polygon
     */
    static public double area (Shape shape) {
        PathIterator it = shape.getPathIterator(null);
        double[] coord = new double[6];
        double area = 0., x = 0, y = 0;
        for (int n = 0; !it.isDone(); ++n) {
            int type = it.currentSegment(coord);
            if (PathIterator.SEG_LINETO == type) {
                if (n == 0) {
                    area = 0;
                    //System.out.print("<["+coord[0]+","+coord[1]+"]");
                }
                else {
                    area += (coord[0]+x)*(coord[1]-y);
                    //System.out.print("-["+x+","+y+"]");
                }
                x = coord[0];
                y = coord[1];
            }
            else if (PathIterator.SEG_MOVETO == type) {
                x = coord[0];
                y = coord[1];
                //System.out.print("<["+x+","+y+"]");
            }
            else if (PathIterator.SEG_CLOSE == type) {
                area += (coord[0]+x)*(coord[1]-y);
                x = coord[0];
                y = coord[1];
                //System.out.println("-["+x+","+y+"]>");
            }
            it.next();
        }

        // in case we trace the path in opposite direction
        return Math.abs(area/2); 
    }
}
