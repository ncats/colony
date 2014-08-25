package tripod.colony;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.util.*;
import java.io.*;
import java.awt.image.*;
import java.awt.*;
import java.awt.color.*;

public class ZStack extends ZPlane implements FilenameFilter {
    static final Logger logger = Logger.getLogger(ZStack.class.getName());

    protected LinkedList<ZPlane> stack = new LinkedList<ZPlane>();
    // distance between consecutive ZPlanes (in um)
    protected Float distance = 5f; 
    protected BufferedImage fused;
    private boolean dirty;

    public ZStack () {
    }

    public ZStack (String name) {
        super (name);
    }

    public ZStack (File file) throws Exception {
        if (file.exists()) {
            if (file.isDirectory()) {
                for (File f : file.listFiles(this)) {
                    add (f);
                }
            }
            else {
                add (file);
            }
        }
    }

    public boolean accept (File dir, String name) {
        int pos = name.lastIndexOf('.');
        if (pos > 0) {
            String ext = name.substring(pos);
            return ext.equalsIgnoreCase("tif") 
                || ext.equalsIgnoreCase("tiff");
        }
        return false;
    }

    public void setDistance (Float distance) {
        this.distance = distance;
    }
    public Float getDistance () { return distance; }

    public Iterator<ZPlane> iterator () { 
        return Collections.unmodifiableList(stack).iterator();
    }
    public ZPlane get (int nth) { return stack.get(nth); }
    public ZPlane add (File file) throws Exception {
        return add (new ZPlane (file));
    }
    public ZPlane add (RenderedImage image) {
        return add (new ZPlane (image));
    }
    public ZPlane add (ZPlane zp) {
        if (fused == null) {
            WritableRaster raster = 
                zp.getImage().getData().createCompatibleWritableRaster();

            logger.info("## creating fused image stack "+getName()); 
            fused = new BufferedImage 
                (zp.getImage().getColorModel(), raster, true, null);
            params.clear();
            params.putAll(zp.getParams());
            // override some properties here
            params.put("Document", "Fused image for image stack "+getName());
            if (distance != null)
                params.put("ZPlaneSpacing", 
                           String.format("%1$.1f [um]", distance));
        }
        else if (zp.getWidth() != fused.getWidth()
                 || zp.getHeight() != fused.getHeight())
            throw new IllegalArgumentException
                ("Input plane doesn't have the same dimension!");
        else {
            WritableRaster ras = fused.getRaster();
            int[] src = new int[ras.getWidth()];
            int[] des = new int[ras.getWidth()];
            for (int y = 0; y < ras.getHeight(); ++y) {
                ras.getPixels(0, y, ras.getWidth(), 1, src);
                zp.raster.getPixels(0, y, ras.getWidth(), 1, des);
                int changes = 0;
                for (int x = 0; x < src.length; ++x) {
                    if (src[x] < des[x]) { // use max
                        src[x] = des[x];
                        ++changes;
                    }
                }

                if (changes > 0) {
                    // update pixels
                    ras.setPixels(0, y, ras.getWidth(), 1, src);
                }
            }

            src = null;
            des = null;
        }
        dirty = true;
        stack.add(zp);

        return zp;
    }

    public void remove (ZPlane zplane) {
        stack.remove(zplane);
    }

    public boolean contains (String name) {
        for (ZPlane zp : stack)
            if (name.equals(zp.getName()))
                return true;
        return false;
    }

    public void fuse () {
        setImage (fused); // generate a fused image
    }
    public int size () { return stack.size(); }
    public void clear () { 
        stack.clear();
        fused = null;
        setImage (fused);
    }
    public ZPlane[] toArray () {
        return stack.toArray(new ZPlane[0]);
    }
}
