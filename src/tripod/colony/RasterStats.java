package tripod.colony;

import java.util.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.awt.image.Raster;

public class RasterStats {
    private static final Logger logger = 
        Logger.getLogger(RasterStats.class.getName());

    protected Map<Integer, Integer> histogram = 
        new TreeMap<Integer, Integer>();
    protected int min, max;
    protected double mean;

    public RasterStats () {
    }

    public RasterStats (Raster raster) {
        setRaster (raster);
    }

    public RasterStats setRaster (Raster raster) {
        if (raster.getNumBands() > 1) {
            logger.warning("Input raster contains "+raster.getNumBands()
                           +" samples per pixel; only use one sample!");
        }

        histogram.clear();
        min = Integer.MAX_VALUE;
        max = 0;
        mean = 0.;
        for (int i = 0; i < raster.getWidth(); ++i)
            for (int j = 0; j < raster.getHeight(); ++j) {
                int pix = raster.getSample(i, j, 0);
                if (pix > max) max = pix;
                if (pix < min) min = pix;
                mean += pix;
                Integer cnt = histogram.get(pix);
                histogram.put(pix, cnt != null ? (cnt+1) : 1);
            }
        mean /= raster.getWidth()*raster.getHeight();
        
        return this;
    }

    public int getMinValue () { return min; }
    public int getMaxValue () { return max; }
    public double getMeanValue () { return mean; }
    public int getRange () { return max - min; }

    public int getCount (int pixel) {
        Integer cnt = histogram.get(pixel);
        return cnt != null ? cnt : -1;
    }

    public int[] getValues () {
        Integer[] values = histogram.keySet().toArray(new Integer[0]);
        int[] v = new int[values.length];
        for (int i = 0; i < v.length; ++i)
            v[i] = values[i];
        return v;
    }

    public String toString () {
        return "{min="+min+",max="+max+",mean="
            +mean+",range="+getRange ()+"}";
    }
}