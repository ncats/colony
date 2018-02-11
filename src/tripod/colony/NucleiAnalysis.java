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

import static tripod.colony.Grayscale.Channel;
import static tripod.colony.RLE.Run;


public class NucleiAnalysis {
    private static final Logger logger = Logger.getLogger
        (NucleiAnalysis.class.getName());

    static class Layer {
        public int lo, hi;
        public Bitmap bitmap;
        public double precision;

        Layer (double precision, int lo, int hi, Bitmap bitmap) {
            this.precision = precision;
            this.lo = lo;
            this.hi = hi;
            this.bitmap = (Bitmap) bitmap.clone();
        }
    }

    public interface Model {
        Channel channel ();
        Bitmap bitmap ();
        Collection<Run[]> masks ();
        double precision ();
        double similarity (Channel channel);
    }

    static public abstract class AbstractModel implements Model, Serializable {
        public Channel channel;
        public double precision;
        
        AbstractModel (Channel channel, double precision) {
            this.channel = channel;
            this.precision = precision;
        }
    }
    
    static public class ThresholdModel implements Model, Serializable {
        private static final long serialVersionUID = 0x123l;

        public Channel channel;
        public double precision;
        public int threshold;

        ThresholdModel (Channel channel, double precision, int threshold) {
            this.channel = channel;
            this.precision = precision;
            this.threshold = threshold;
        }
        
        public Bitmap bitmap () {
            return Util.threshold(channel.raster(), threshold);
        }

        public Collection<Run[]> masks () {
            RLE rle = new RLE (bitmap ());
            return rle.encode();
        }

        public double precision () { return precision; }
        public Channel channel () { return channel; }
        public double similarity (Channel channel) {
            double x = 0., y = 0., xy = 0.;
            for (int i = 0; i < channel.pmf.length; ++i) {
                x += channel.pmf[i] * channel.pmf[i];
                y += this.channel.pmf[i] * this.channel.pmf[i];
                xy += channel.pmf[i] * this.channel.pmf[i];
            }
            return xy/(Math.sqrt(x) * Math.sqrt(y));
        }
        
        public String toString () {
            return getClass().getSimpleName()+"{channel="
                +channel.getClass().getSimpleName()+",precision="
                +String.format("%1$.5f", precision)
                +",threshold="+threshold+"}";
        }
    }

    final Grayscale grayscale;
    final IntersectionOverUnion iou;
    
    int rlow, rhigh, pmin, pmax; 
    Nucleus[] nuclei;
    
    /**
     * for a given raster image and annotated nuclei, we perform the following
     *  + convert the raster image into grayscale if necessary
     *  + for each nucleus, determine the pixel intensity range [low, high]
     *    that spans the nucleus
     *  + over all nuclei, we merge all intersecting intensity ranges
     *  + we store the grayscale histogram (pmf) along with the pixel 
     *    intensity ranges as model
     */
    public NucleiAnalysis (Raster raster, Collection<Run[]> masks) {
        if (raster == null)
            throw new IllegalArgumentException ("Raster image can't be null!");
        
        if (masks == null)
            throw new IllegalArgumentException ("Masks can't be null!");
        
        iou = new IntersectionOverUnion
            (raster.getWidth(), raster.getHeight(), masks);

        grayscale = new Grayscale (raster);
        //initNuclei (masks, grayscale.getChannel());
    }

    void initNuclei (Collection<Run[]> masks, Channel raster) {
        nuclei = new Nucleus[masks.size()];
        
        rlow = pmin = Integer.MAX_VALUE;
        rhigh = pmax = 0;
        
        int i = 0;
        for (Run[] r : masks) {
            int min = Integer.MAX_VALUE, max = 0;
            for (int j = 0; j < r.length; ++j) {
                int x = r[j].x, y = r[j].y0, y1 = r[j].y1;
                while (y < y1) {
                    int p = raster.get(x, y++);
                    if (p < min) min = p;
                    if (p > max) max = p;
                }
            }
            Nucleus n = new Nucleus (r);
            nuclei[i++] = n;
            /*
            logger.info("## nucleus "+i+": "
                        +n.getBounds()+" min="+min+" max="+max+" area="+n.area);
                        I*/
            
            if (min < pmin) pmin = min;
            if (max > pmax) pmax = max;
            int range = max - min;
            if (range < rlow) rlow = range;
            if (range > rhigh) rhigh = range;
        }
        logger.info("## "+masks.size()+" nuclei: range "+rlow+" to "+rhigh);
    }
    
    double eval (Bitmap b, int lo, int hi, Channel raster) {
        b.clear();
        for (int x = 0; x < b.width(); ++x) {
            for (int y = 0; y < b.height(); ++y) {
                int p = raster.get(x, y);
                //b.set(x, y, p < (lo+(hi-lo)/2));
                b.set(x, y, p <= hi && p >= lo);
            }
        }
        
        return iou.precision(b);
    }

    static Layer findOverlap (List<Layer> layers, int lo, int hi) {
        for (Layer l : layers) {
            if ((lo >= l.lo && lo <= l.hi)
                || (hi >= l.lo && hi <= l.hi)
                || (l.lo >= lo && l.lo <= hi))
                return l;
        }
        return null;
    }

    public Model threshold () {
        ThresholdModel model = null;
        for (int i = 0; i < grayscale.getNumChannels(); ++i) {
            Channel channel = grayscale.getChannel(i);
            /*
            logger.info("=========== "+channel.getClass().getName()
                        +" ["+channel.pmin+","+channel.pmax+"] ===========");
            */
            Raster raster = channel.raster();
            for (int t = channel.pmin+1; t < channel.pmax; ++t) {
                Bitmap b = Util.threshold(raster, t);
                double p = iou.precision(b);
                if (model == null) {
                    model = new ThresholdModel (channel, p, t);
                }
                else if (p > model.precision) {
                    model.channel = channel;
                    model.precision = p;
                    model.threshold = t;
                    //logger.info("++ threshold = "+t+" precision = "+p);
                }
            }

            if (false) {
                try {
                    channel.write(new File ("raster-"
                                            +channel.getClass().getSimpleName()
                                            +".png"));
                }
                catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        
        if (model != null) {
            logger.info("###### Best channel="+model.channel
                        .getClass().getSimpleName()
                        +" precision="+model.precision+" threshold="
                        +model.threshold);
            if (false) {
                try {
                    model.channel.write(new File ("best-grayscale.png"));
                    model.bitmap().write(new File ("best-masks.png"));
                }
                catch (IOException ex) {
                    logger.log(Level.SEVERE, "Can't export bitmap!", ex);
                }
            }
        }
        
        return model;
    }
    
    public void window () {
        Bitmap b = new Bitmap (grayscale.width(), grayscale.height());

        Channel channel = grayscale.getChannel();
        // running a window of size range over the grayscale histogram
        List<Layer> layers = new ArrayList<>();
        for (int range = rlow; range <= rhigh; ++range) {
            for (int i = pmin; i < pmax; ++i) {
                int j = Math.min(pmax, i+range);
                double p = eval (b, i, j, channel);
                Layer l = findOverlap (layers, i, j);
                if (l == null) {
                    layers.add(new Layer (p, i, j, b));
                    logger.info("new window ["+i+","+j+"] "+p);
                }
                else if (l.precision < p) {
                    logger.info("["+i+","+j+"] => window ["+l.lo+","+l.hi+"] "
                                +l.precision+" => "+p);
                    l.precision = p;
                    l.bitmap = (Bitmap) b.clone();
                    l.lo = Math.min(l.lo, i);
                    l.hi = Math.max(l.hi, j);
                }
            }
        }
        
        logger.info("===> best "+layers.size()+" layer(s) found!");
        int i = 1;
        for (Layer l : layers) {
            logger.info(".. layer ["+l.lo+","+l.hi+"] precision="+l.precision);
            try {
                ImageIO.write(l.bitmap.createBufferedImage(),
                              "png", new FileOutputStream
                              ("nuclei-"+i+".png"));
                ++i;
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static List<Run[]> parseMasks
        (String name, int size, InputStream is) throws IOException {
        BufferedReader br = new BufferedReader (new InputStreamReader (is));
        br.readLine(); // skip header

        List<Run[]> masks = new ArrayList<>();
        for (String line; (line = br.readLine()) != null; ) {
            String[] toks = line.split(",");
            if (toks.length == 2 && toks[0].equals(name)) {
                String[] runlen = toks[1].split("\\s+");
                if (runlen.length % 2 == 0) {
                    ArrayList<Run> runs = new ArrayList<>();
                    for (int i = 0; i < runlen.length; i+=2) {
                        int index = Integer.parseInt(runlen[i]);
                        int len = Integer.parseInt(runlen[i+1]);
                        runs.add(new Run(size, index, len));
                    }
                    Collections.sort(runs);
                    masks.add(runs.toArray(new Run[0]));
                }
                else {
                    logger.warning
                        ("Bad run length; not even number of tokens:\n"
                         +toks[1]);
                }
            }
        }
        
        return masks;
    }

    static public class Train {
        public static void main (String[] argv) throws Exception {
            if (argv.length < 3) {
                System.err.println("Usage: "
                                   +NucleiAnalysis.Train.class.getName()
                                   +" MASK_FILE IMAGE_FILE OUTDIR");
                System.exit(1);
            }

            File outdir = new File (argv[2]);
            outdir.mkdirs();

            BufferedReader br = new BufferedReader (new FileReader (argv[1]));
            for (String line; (line = br.readLine()) != null; ) {
                File file = new File (line);
                RenderedImage image = ImageIO.read(file);
                
                String name = file.getName();
                int pos = name.lastIndexOf('.');
                if (pos > 0) {
                    name = name.substring(0, pos);
                }

                List<Run[]> masks = parseMasks
                    (name, image.getHeight(), new FileInputStream (argv[0]));
                logger.info(name+": "+masks.size()+" nuclei!");
                NucleiAnalysis na = new NucleiAnalysis (image.getData(), masks);
                Model model = na.threshold();
                
                File out = new File (outdir, name);
                ObjectOutputStream oos = new ObjectOutputStream
                    (new FileOutputStream (out));
                oos.writeObject(model);
                oos.close();
            }
        }
    }

    static class Candidate implements Comparable<Candidate> {
        public final double similarity;
        public final String name;
        public final Model model;

        Candidate (double similarity, String name, Model model) {
            this.similarity = similarity;
            this.name = name;
            this.model = model;
        }

        public int compareTo (Candidate c) {
            double x = score ();
            double y = c.score();
            if (y > x) return 1;
            if (y < x) return -1;
            return name.compareTo(c.name);
        }

        public double score () {
            return similarity * model.precision();
        }
    }

    static public class Predict {
        File dir;
        
        Predict (String dir) {
            this (new File (dir));
        }
             
        Predict (File dir) {
            if (!dir.isDirectory())
                throw new IllegalArgumentException
                    (dir.getName()+" is not a directory!");
            this.dir = dir;
        }

        void predict (Raster raster) {
            Grayscale grayscale = new Grayscale (raster);
            List<Candidate> candidates = new ArrayList<>();
            for (int i = 0; i < grayscale.getNumChannels(); ++i) {
                Channel channel = grayscale.getChannel(i);
                predict (candidates, channel);
            }

            Collections.sort(candidates);
            for (Candidate c : candidates) {
                System.out.println(c.name+": score="
                                   +String.format("%1$.5f", c.score())
                                   +" sim="
                                   +String.format("%1$.5f", c.similarity)
                                   +" "+c.model);
            }
        }

        void predict (List<Candidate> candidates, Channel channel) {
            for (File f : dir.listFiles()) {
                try {
                    ObjectInputStream ois = new ObjectInputStream
                        (new FileInputStream (f));
                    Model model = (Model)ois.readObject();
                    if (model.channel().getClass().equals(channel.getClass())) {
                        double sim = model.similarity(channel);
                        candidates.add(new Candidate
                                       (sim, f.getName().substring(0, 5),
                                        model));
                    }
                }
                catch (Exception ex) {
                    logger.log(Level.SEVERE, f.getName()
                               +": "+ex.getMessage(), ex);
                }
            }
        }
        
        public static void main (String[] argv) throws Exception {
            if (argv.length < 2) {
                System.err.println("Usage: "
                                   +NucleiAnalysis.Predict.class.getName()
                                   +" MODEL_DIR IMAGES...");
                System.exit(1);
            }

            Predict pred = new Predict (argv[0]);
            for (int i = 1; i < argv.length; ++i) {
                File file = new File (argv[i]);
                System.out.println(">>>>>>> "+file.getName());
                RenderedImage image = ImageIO.read(file);
                pred.predict(image.getData());
            }
        }
    }

    public static void main (String[] argv) throws Exception {
        if (argv.length < 2) {
            System.err.println("Usage: "+NucleiAnalysis.class.getName()
                               +" MASK IMAGES...");
            System.exit(1);
        }

        try {
            for (int i = 1; i < argv.length; ++i) {
                RenderedImage image = ImageIO.read(new File (argv[i]));
                
                String name = new File(argv[i]).getName();
                int pos = name.lastIndexOf('.');
                if (pos > 0) {
                    name = name.substring(0, pos);
                }

                List<Run[]> masks = parseMasks
                    (name, image.getHeight(), new FileInputStream (argv[0]));
                
                logger.info(name+": "+masks.size()+" nuclei!");
                if (masks.isEmpty()) {
                    logger.warning(name+": can't locate in file "
                                   +new File (argv[1]));
                }
                else {
                    NucleiAnalysis na =
                        new NucleiAnalysis (image.getData(), masks);
                    //na.window();
                    na.threshold();
                }
            }
        }
        catch (Exception ex) {
            logger.log(Level.SEVERE, "Can't parse image: "+argv[0], ex);
        }
    }
}
