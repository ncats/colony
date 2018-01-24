package tripod.colony;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.awt.image.Raster;
import java.awt.image.WritableRaster;
import java.awt.image.BandedSampleModel;
import java.awt.image.RescaleOp;

import javax.imageio.ImageIO;


/**
 * 256 gray scale 
 */
public class Grayscale {
    static final Logger logger = Logger.getLogger(Grayscale.class.getName());

    private Raster grayscale;
    private int[] histogram = new int[256];
    private double mean, stddev;
    private int max, min;
    private boolean inverted;

    public Grayscale () {
    }

    public Grayscale (Raster raster) {
        setRaster (raster);
    }

    public void setRaster (Raster raster) {
        if (raster == null) {
            throw new IllegalArgumentException ("Input raster is null");
        }
        grayscale = createRaster (raster);
    }

    public Raster getRaster () { 
        return grayscale; 
    }

    /*
     * convert rgb raster into grayscale
     */
    protected Raster createRaster (Raster raster) {
        int height = raster.getHeight();
        int width = raster.getWidth();

        max = 0;
        min = Integer.MAX_VALUE;
        for (int i = 0; i < histogram.length; ++i)
            histogram[i] = 0;

        WritableRaster outRaster = Raster.createWritableRaster
                (new BandedSampleModel
                 (DataBuffer.TYPE_BYTE, width, height, 1), null);
        double[] sample = new double[raster.getNumBands()];
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                int s = grayscale (raster.getPixel(x, y, sample)) & 0xff;
                if (s > max) max = s;
                if (s < min) min = s;
                
                outRaster.setSample (x, y, 0, s);
                ++histogram[s];
            }
        }
        
        raster = outRaster;

        mean = 0;
        stddev = 0;
        int cnt = 0, m = 0, n = 0;
        for (int i = 0; i < histogram.length; ++i) {
            int p = histogram[i];
            if (p > 0) {
                mean += p;
                ++cnt;
            }
            if (p > m) {
                m = p;
                n = i;
            }
        }

        if (cnt > 0) {
            mean /= cnt;
            for (int i = 0; i < histogram.length; ++i) {
                int p = histogram[i];
                if (p > 0) {
                    double x = p - mean;
                    stddev += x*x;
                }
            }
            stddev = Math.sqrt(stddev/cnt);
        }

        inverted = n-min > max - n;
        logger.info("## bands="+raster.getNumBands()+" range="
                    +(max-min)+", min="+min+", max="+max
                    +", max("+n+")="+m+" inverted="+inverted);

        return raster;
    }

    public boolean inverted () { return inverted; }

    public BufferedImage getImage () {
        if (grayscale == null) {
            throw new IllegalStateException ("No buffer available");
        }

        BufferedImage img = new BufferedImage
            (grayscale.getWidth(), grayscale.getHeight(), 
             BufferedImage.TYPE_BYTE_GRAY);
        img.setData(grayscale);
        return img;
    }

    public int[] histogram () { return histogram; }
    public double mean () { return mean; }
    public double stddev () { return stddev; }
    public int min () { return min; }
    public int max () { return max; }

    public void write (OutputStream out) throws IOException {
        ImageIO.write(getImage (), "png", out);
    }

    public static int grayscale (double[] p) {
        int i = 0;
        if (p.length == 4) ++i; // skip alpha channel
        if (p.length >= 3)
            return (int) (0.299 * p[i] + 0.587 * p[i+1]
                          + 0.114 * p[i+2] + .5);
        return (int)(p[0] + 0.5);
    }
}
