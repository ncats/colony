package tripod.colony;

import java.io.Serializable;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.util.List;
import java.util.ArrayList;
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

    public static double R = 0.299;
    public static double G = 0.587;
    public static double B = 0.114;

    interface PixelSource {
        int get (int x, int y);
    }
    
    public static class Channel implements Serializable {
        private static final long serialVersionUID = 0x12345l;
        
        final public int width, height;
        final public int[] histogram;
        final public double[] pmf;
        final short[] pixels;
        final public int pmin, pmax;

        Channel (int width, int height, PixelSource source) {
            this.width = width;
            this.height = height;
            pixels = new short[width*height];
            
            histogram = new int[256];
            int p, s, min = 256, max = 0;
            for (int y = 0; y < height; ++y) {
                s = y*width;
                for (int x = 0; x < width; ++x) {
                    p = pixels[s+x] = (short)(source.get(x, y) & 0xff);
                    if (p > max) max = p;
                    if (p < min) min = p;
                    ++histogram[p];
                }
            }
            pmin = min;
            pmax = max;

            pmf = new double[256];
            double mass = 0.;
            for (int i = 0; i < histogram.length; ++i)
                mass += histogram[i];
            for (int i = 0; i < histogram.length; ++i)
                pmf[i] = histogram[i]/mass;
        }
        
        public int get (int x, int y) { return pixels[y*width+x]; }
        public Raster raster () {
            WritableRaster raster = createByteRaster (width, height);
            for (int y = 0, s; y < height; ++y) {
                s = y*width;
                for (int x = 0; x < width; ++x)
                    raster.setSample(x, y, 0, pixels[s+x]);
            }
            return raster;
        }
        
        public BufferedImage image () {
            Raster raster = raster ();
            BufferedImage img = new BufferedImage
                (raster.getWidth(), raster.getHeight(), 
                 BufferedImage.TYPE_BYTE_GRAY);
            img.setData(raster);
            return img;
        }

        public void write (File out) throws IOException {
            write (new FileOutputStream (out));
        }
        
        public void write (OutputStream out) throws IOException {
            ImageIO.write(image (), "png", out);
        }

        public String toString () {
            return getClass().getName()+"{width="+width+",height="+height+"}";
        }
    }

    static abstract class RasterPixelSource implements PixelSource {
        final Raster raster;
        final double[] sample;
        RasterPixelSource (Raster raster) {
            this.raster = raster;
            sample = new double[raster.getNumBands()];
        }

        public abstract int get (int x, int y);
    }

    public static class ChannelRGB extends Channel {
        ChannelRGB (Raster raster) {
            super (raster.getWidth(), raster.getHeight(),
                   new RasterPixelSource (raster) {
                       public int get (int x, int y) {
                           return grayscale (raster.getPixel
                                             (x, y, sample)) & 0xff;
                       }
                   });
        }
    }

    public static class ChannelR extends Channel {
        ChannelR (Raster raster) {
            super (raster.getWidth(), raster.getHeight(),
                   new RasterPixelSource (raster) {
                       public int get (int x, int y) {
                           raster.getPixel(x, y, sample);
                           return (int)(sample[0]+0.5) & 0xff;
                       }
                   });
        }
    }

    public static class ChannelG extends Channel {
        ChannelG (Raster raster) {
            super (raster.getWidth(), raster.getHeight(),
                   new RasterPixelSource (raster) {
                       public int get (int x, int y) {
                           raster.getPixel(x, y, sample);
                           return (int)(sample[1]+0.5) & 0xff;
                       }
                   });
        }
    }

    public static class ChannelB extends Channel {
        ChannelB (Raster raster) {
            super (raster.getWidth(), raster.getHeight(),
                   new RasterPixelSource (raster) {
                       public int get (int x, int y) {
                           raster.getPixel(x, y, sample);
                           return (int)(sample[2]+0.5) & 0xff;
                       }
                   });
        }
    }
    
    private Channel[] channels;
    private Raster raster;

    public Grayscale () {
    }

    public Grayscale (Raster raster) {
        setRaster (raster);
    }

    public void setRaster (Raster raster) {
        if (raster == null || raster.getNumBands() == 0) {
            throw new IllegalArgumentException ("Input raster is bogus!");
        }

        List<Channel> channels = new ArrayList<>();
        channels.add(new ChannelRGB (raster));
        if (raster.getNumBands() > 1) {
            channels.add(new ChannelR (raster));
            channels.add(new ChannelG (raster));
            channels.add(new ChannelB (raster));
        }
        this.channels = channels.toArray(new Channel[0]);
        this.raster = raster;
    }

    public Raster getRaster () { return raster; }
    public int width () { return raster != null ? raster.getWidth() : -1; }
    public int height () { return raster != null ? raster.getHeight() : -1; }
    
    public int getNumChannels () {
        return channels != null ? channels.length : 0;
    }
    
    public Channel getChannel (int channel) {
        return channel < channels.length ? channels[channel] : null;
    }
    
    // default channel
    public Channel getChannel () {
        return channels != null ? channels[0] : null;
    }

    public Channel getChannel (String name) {
        if (channels != null) {
            for (int i = 0; i < channels.length; ++i) {
                if (name.equals(channels[i].getClass().getName()))
                    return channels[i];
            }
        }
        return null;
    }

    public Channel getChannel (Class<? extends Channel> cls) {
        if (channels != null) {
            for (int i = 0; i < channels.length; ++i)
                if (cls.isAssignableFrom(channels[i].getClass()))
                    return channels[i];
        }
        return null;
    }

    static WritableRaster createByteRaster (int width, int height) {
        return Raster.createWritableRaster
            (new BandedSampleModel
             (DataBuffer.TYPE_BYTE, width, height, 1), null);
    }

    static BufferedImage createByteImage (Raster raster) {
        BufferedImage img = new BufferedImage
            (raster.getWidth(), raster.getHeight(), 
             BufferedImage.TYPE_BYTE_GRAY);
        img.setData(raster);
        return img;
    }

    public static int grayscale (double[] p) {
        if (p.length == 1)
            return (int)(p[0] + 0.5);
        return (int) (R * p[0] + G * p[1] + B * p[2] + .5);
    }
}
