package tripod.colony;

import java.io.Serializable;
import java.util.*;
import java.util.logging.*;
import java.awt.image.*;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.*;
import java.io.OutputStream;
import java.io.PrintStream;


/* encode run-length format based on kaggle data science bowl 2018:
   In order to reduce the submission file size, our metric uses 
   run-length encoding on the pixel values. Instead of submitting an
   exhaustive list of indices for your segmentation, you will submit 
   pairs of values that contain a start position and a run length. 
   E.g. '1 3' implies starting at pixel 1 and running a total of 3 
   pixels (1,2,3). 
   
   The competition format requires a space delimited list of pairs. 
   For example, '1 3 10 5' implies pixels 1,2,3,10,11,12,13,14 are 
   to be included in the mask. The pixels are one-indexed and numbered 
   from top to bottom, then left to right: 1 is pixel (1,1), 2 is 
   pixel (2,1), etc.
*/
public class RLE {
    static final Logger logger =
        Logger.getLogger(RLE.class.getName());
    
    public static class Run implements Comparable<Run>, Serializable {
        public int index; // 1-based 
        public int len, stride;
        public int x, y0, y1;
        
        public Run (int stride, int index, int len) {
            this.index = index;
            this.len = len;
            this.stride = stride;
            update ();
        }

        protected void update () {
            x = (index-1) / stride;
            y0 = (index-1) % stride;
            y1 = y0 + (len-1);
        }

        public Line2D line () {
            return new Line2D.Double((double)x, (double)y0,
                                     (double)x, (double)y1);
        }

        public int compareTo (Run r) {
            int d = index - r.index;
            if (d == 0)
                d = len - r.len;
            return d;
        }
        
        public String toString () {
            return index+" "+len;
        }
    }

    final Bitmap bitmap;

    public RLE (Bitmap bitmap) {
        if (bitmap == null)
            throw new IllegalArgumentException ("Bitmap is null");
        this.bitmap = bitmap;
    }

    public Bitmap getBitmap () { return bitmap; }

    public static List<Run[]> encode
        (Collection<Shape> components, Bitmap bitmap) {
        List<Run[]> runlens = new ArrayList<>();
        for (Shape s : components) {
            Rectangle rect = s.getBounds();
            //logger.info("** component "+rect+ " **");
            List<Run> runs = new ArrayList<>();
            for (int x0 = rect.x, x1 = x0+rect.width; x0 < x1; ++x0) {
                Run r = null;
                for (int y0 = rect.y, y1 = y0+rect.height; y0 < y1; ++y0) {
                    if (bitmap.isOn(x0, y0) && s.contains(x0, y0)) {
                        if (r != null)
                            ++r.len;
                        else
                            r = new Run (bitmap.height(),
                                         x0*bitmap.height()+y0+1, 1);
                    }
                    else if (r != null) {
                        r.update();
                        runs.add(r);
                        r = null;
                    }
                }
                
                if (r != null) {
                    r.update();
                    runs.add(r);
                }
            }

            if (!runs.isEmpty()) {
                runlens.add(runs.toArray(new Run[0]));
            }
        }
        return runlens;
    }

    public List<Run[]> encode (Collection<Shape> components) {
        return encode (components, bitmap);
    }
    
    public List<Run[]> encode () {
        List<Shape> merged = new ArrayList<>();
        for (Shape p : bitmap.polyConnectedComponents()) {
            Area a = new Area (p);
            Rectangle r = p.getBounds();
            List<Shape> remove = new ArrayList<>();
            for (Shape s : merged) {
                Area b = new Area (s);
                if (b.intersects(r)
                    || b.contains(r) || a.contains(s.getBounds())) {
                    a.add(b);
                    remove.add(s);
                }
            }
            merged.add(a);
            for (Shape s : remove)
                merged.remove(s);
        }
        
        return encode (merged, bitmap);
    }

    public void encode (String name, OutputStream os) {
        encode (name, os, 5);
    }
    
    public void encode (String name, OutputStream os, int minsize) {
        PrintStream ps = new PrintStream (os);
        for (Run[] runs : encode ()) {
            int n = 0;
            for (int i = 0; i < runs.length; ++i)
                n += runs[i].len;

            if (n > minsize) {
                ps.print(name+","+runs[0]);
                for (int i = 1; i < runs.length; ++i)
                    ps.print(" "+runs[i]);
                ps.println();
            }
        }
    }

    // call this as often as needed; this assumes bitmap has been sized
    // appropriatedly
    public void decode (int index, int len) {
        int x = (index - 1) / bitmap.height();
        int y = (index - 1) % bitmap.height();
        if (len > 1) {
            int y1 = y + (len-1);
            while (y < y1) {
                /*if (bitmap.isOn(x, y)) {
                    logger.warning("Pixel ("+x+","+y+") is already encoded!");
                }
                else*/
                    bitmap.set(x, y++, true);
            }
        }
        /*
        else if (bitmap.isOn(x, y)) {
            logger.warning("Pixel ("+x+","+y+") is already encoded!");
            }
        */
        else
            bitmap.set(x, y, true);
    }
    
    public void decode (Run r) {
        decode (r.index, r.len);
    }

    public void decode (Run... runs) {
        for (Run r : runs)
            decode (r);
    }
}
