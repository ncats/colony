package tripod.colony;

import java.util.*;
import java.util.logging.*;
import java.awt.image.*;
import java.awt.Rectangle;
import java.awt.Shape;

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
public class CodecRunLength {
    static final Logger logger =
        Logger.getLogger(CodecRunLength.class.getName());
    
    public static class Run {
        int index; // 1-based 
        int len;
        Run (int index, int len) {
            this.index = index;
            this.len = len;
        }
        
        public int index () { return index; }
        public int len () { return len; }
        
        public String toString () {
            return index+" "+len;
        }
    }

    final Bitmap bitmap;

    public CodecRunLength (Bitmap bitmap) {
        if (bitmap == null)
            throw new IllegalArgumentException ("Bitmap is null");
        this.bitmap = bitmap;
    }

    public Bitmap getBitmap () { return bitmap; }
    
    public Collection<Run[]> encode () {
        List<Run[]> runlens = new ArrayList<>();
        for (Shape s : bitmap.connectedComponents()) {
            Rectangle rect = s.getBounds();
            logger.info("** component "+rect+ " **");
            List<Run> runs = new ArrayList<>();
            for (int x0 = rect.x, x1 = x0+rect.width; x0 < x1; ++x0) {
                Run r = null;
                for (int y0 = rect.y, y1 = y0+rect.height; y0 < y1; ++y0) {
                    if (bitmap.isOn(x0, y0)) {
                        if (r != null)
                            ++r.len;
                        else
                            r = new Run (x0*bitmap.height()+y0+1, 1);
                    }
                    else if (r != null) {
                        runs.add(r);
                        r = null;
                    }
                }
                
                if (r != null)
                    runs.add(r);
            }
            
            if (!runs.isEmpty()) {
                runlens.add(runs.toArray(new Run[0]));
            }
        }
        return runlens;
    }

    // call this as often as needed; this assumes bitmap has been sized
    // appropriatedly
    public void decode (int index, int len) {
        int x = (index - 1) / bitmap.height();
        int y = (index - 1) % bitmap.height();
        if (len > 1) {
            int y1 = y + (len-1);
            while (y < y1)
                bitmap.set(x, y++, true);
        }
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
