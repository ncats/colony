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

    public interface Model {
        Channel channel ();
        Bitmap apply (Raster raster);
        double precision ();
        double similarity (Channel channel);
    }

    static public abstract class AbstractModel implements Model, Serializable {
        private static final long serialVersionUID = 0x12l;        
        public Channel channel;
        public double precision;
        
        AbstractModel (Channel channel, double precision) {
            this.channel = channel;
            this.precision = precision;
        }

        public abstract Bitmap apply (Raster raster);
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
                +String.format("%1$.5f", precision)+"}";
        }
    }
    
    static public class ThresholdModel extends AbstractModel {
        private static final long serialVersionUID = 0x123l;
        public int threshold;
        public double[] tmf; // threshold mass function

        ThresholdModel (Channel channel, double precision, int threshold) {
            super (channel, precision);
            this.threshold = threshold;
        }

        @Override
        public Bitmap apply (Raster raster) {
            return Util.threshold(raster, threshold);
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
    }


    public ThresholdModel threshold () {
        ThresholdModel model = null;
        double[][] tmf = new double[grayscale.getNumChannels()][256];
        for (int i = 0; i < grayscale.getNumChannels(); ++i) {
            Channel channel = grayscale.getChannel(i);
            /*
            logger.info("=========== "+channel.getClass().getName()
                        +" ["+channel.pmin+","+channel.pmax+"] ===========");
            */
            Raster raster = channel.raster();
            double total = raster.getWidth()*raster.getHeight();
            for (int t = channel.pmin+1; t < channel.pmax; ++t) {
                Bitmap b = Util.threshold(raster, t);
                double p = iou.precision(b);
                if (model == null) {
                    model = new ThresholdModel (channel, p, t);
                    model.tmf = tmf[i];
                }
                else if (p > model.precision) {
                    model.channel = channel;
                    model.precision = p;
                    model.threshold = t;
                    model.tmf = tmf[i];
                    //logger.info("++ threshold = "+t+" precision = "+p);
                }
                tmf[i][t] = b.area()/total;
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
        }
        
        return model;
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

    static public class TMF {
        public static void main (String[] argv) throws Exception {
            if (argv.length == 0) {
                System.err.println("Usage: "+NucleiAnalysis.TMF.class.getName()
                                   +" IMAGE");
                System.exit(1);
            }
            RenderedImage image = ImageIO.read(new File (argv[0]));
            Grayscale gs = new Grayscale (image.getData());
            Channel channel = gs.getChannel();
            double total = image.getWidth()*image.getHeight();
            for (int t = channel.pmin+1; t < channel.pmax; ++t) {
                Bitmap b = Util.threshold(channel.raster(), t);
                //System.out.println(t+" "+(b.area()/total));
                List<Shape> cc = b.connectedComponents();
                //System.out.println(t+" "+cc.size());
                double avgcc = 0;
                for (Shape s : cc) {
                    java.awt.Rectangle r = s.getBounds();
                    avgcc += Math.sqrt(r.width*r.width + r.height * r.height);
                }
                avgcc /= cc.size();
                System.out.println(t+" "+avgcc+" "+cc.size());
            }
        }
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
        public final Channel channel;
        public final String name;
        public final Model model;
        public final double similarity;
        public final double score;

        Candidate (Channel channel, String name, Model model) {
            this.channel = channel;
            this.name = name;
            this.model = model;
            similarity = model.similarity(channel);
            score = similarity * model.precision();
        }

        public int compareTo (Candidate c) {
            double x = similarity;//score;
            double y = c.similarity;//c.score;
            if (y > x) return 1;
            if (y < x) return -1;
            return name.compareTo(c.name);
        }
    }

    static public class Predict {
        File dir;
        
        public Predict (String dir) {
            this (new File (dir));
        }
             
        public Predict (File dir) {
            if (!dir.isDirectory())
                throw new IllegalArgumentException
                    (dir.getName()+" is not a directory!");
            this.dir = dir;
        }

        List<Candidate> predict (Raster raster) {
            Grayscale grayscale = new Grayscale (raster);
            List<Candidate> candidates = new ArrayList<>();
            for (int i = 0; i < grayscale.getNumChannels(); ++i) {
                Channel channel = grayscale.getChannel(i);
                predict (candidates, channel);
            }

            Collections.sort(candidates);
            return candidates;
        }

        void predict (List<Candidate> candidates, Channel channel) {
            for (File f : dir.listFiles()) {
                try {
                    ObjectInputStream ois = new ObjectInputStream
                        (new FileInputStream (f));
                    Model model = (Model)ois.readObject();
                    if (model.channel().getClass().equals(channel.getClass())) {
                        candidates.add(new Candidate
                                       (channel, f.getName().substring(0, 5),
                                        model));
                    }
                }
                catch (Exception ex) {
                    logger.log(Level.SEVERE, f.getName()
                               +": "+ex.getMessage(), ex);
                }
            }
        }

        public void predict (File input, OutputStream out) throws Exception {
            String name = input.getName();
            int pos = name.lastIndexOf('.');
            if (pos > 0)
                name = name.substring(0, pos);
                
            //logger.info(">>>>>>> "+name);
            
            RenderedImage image = ImageIO.read(input);
            List<Candidate> candidates = predict (image.getData());
            
            int N = Integer.getInteger("candidate-size", 5);
            for (int i = 0; i < N; ++i) {
                Candidate c = candidates.get(i);
                /*
                logger.info(c.name+": score="
                            +String.format("%1$.5f", c.score)
                            +" sim="+String.format("%1$.5f", c.similarity)
                            +" "+c.model);
                */
            }

            Candidate cand = candidates.get(0); // most similar
            Bitmap mask = null;
            if (cand.similarity < 0.8) { // can't trust this
                double t = 0.;
                logger.info(">>>>>>> "+input.getName());                
                for (int i = 0; i < N; ++i) {
                    Candidate c = candidates.get(i);
                    logger.info(c.name+": score="
                                +String.format("%1$.5f", c.score)
                                +" sim="+String.format("%1$.5f", c.similarity)
                                +" "+c.model);
                    ThresholdModel tm = (ThresholdModel)c.model;
                    t += tm.threshold;
                }
                t /= N;
                logger.info(">> threshold = "+t);
            }
            else {
                mask = cand.model.apply(image.getData());
                //logger.info(">> threshold = "+((ThresholdModel)cand.model).threshold);
            }

            if (mask != null) {
                try {
                    mask.write(new File (name.substring(0, 5)+".png"));
                }
                catch (IOException ex) {
                    ex.printStackTrace();
                }
                new RLE (mask).encode(name, out);
            }
            else {
                new PrintStream(out).println(name+",");
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
            FileOutputStream fos = new FileOutputStream ("predictions.csv");
            new PrintStream(fos).println("ImageId,EncodedPixels");
            for (int i = 1; i < argv.length; ++i) {
                File file = new File (argv[i]);
                if (file.isDirectory()) {
                    for (File f : file.listFiles())
                        pred.predict(f, fos);
                }
                else {
                    pred.predict(file, fos);
                }
            }
            fos.close();
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
