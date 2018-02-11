package tripod.colony;

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.color.*;
import java.awt.image.*;
import java.awt.event.*;
import javax.swing.*;
import java.awt.geom.*;
import javax.imageio.*;

import java.util.logging.Logger;
import java.util.logging.Level;
import static tripod.colony.ColonyAnalysis.Type.*;

public class ColonyImagePane extends JPanel
    implements MouseMotionListener, MouseListener {
    static final Logger logger = Logger.getLogger
        (ColonyImagePane.class.getName());

    static final Color FILL_COLOR = new Color (0x9e, 0xe6, 0xcd, 120);
    static final int PAD_X = 5; // padding on each side

    public static final long FLAG_IMAGE = 1L<<0;
    public static final long FLAG_GRAY = 1L<<1;
    public static final long FLAG_BINARY = 1L<<2;
    public static final long FLAG_SKELETON = 1L<<3;
    public static final long FLAG_SEGMENT = 1L<<4;
    public static final long FLAG_POLYGON = 1L<<5;
    public static final long FLAG_MASKS = 1L<<6;

    protected long flags = FLAG_IMAGE|FLAG_POLYGON|FLAG_SEGMENT|FLAG_MASKS;
    protected BufferedImage image;
    protected ColonyAnalysis colony = new ColonyAnalysis ();
    protected int width, height; // original raster width & height
    protected double scale = 1.;
    protected Point pt = new Point ();
    
    protected ArrayList<Nucleus> nuclei = new ArrayList<>();
    protected NucleiAnalysis.Model model;
    IntersectionOverUnion iou;
    
    public ColonyImagePane () {
        addMouseMotionListener (this);
        addMouseListener (this);
    }

    public void mouseEntered (MouseEvent e) {
        setCursor (Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
    }
    public void mouseClicked (MouseEvent e) {}
    public void mouseExited (MouseEvent e) {}
    public void mousePressed (MouseEvent e) {}
    public void mouseReleased (MouseEvent e) {}
    
    public void mouseDragged (MouseEvent e) {}
    public void mouseMoved (MouseEvent e) {
        pt.x = e.getX();
        pt.y = e.getY();
        repaint ();
    }

    public void show (long flag) {
        flags |= flag;
        if (image != null) {
            image = createMosaic ();
        }
        repaint ();
    }
    
    public void hide (long flag) {
        flags &=~flag;
        if (image != null) {
            image = createMosaic ();
        }
        repaint ();
    }

    public void setScale (double scale) {
        this.scale = scale;
        resizeAndRepaint ();
    }

    public void setRaster (Raster raster) {
        if (raster != null) {
            width = raster.getWidth();
            height = raster.getHeight();
            
            colony.setRaster(raster);
            colony.analyze();
            //image = colony.getImage(ColonyAnalysis.Type.Threshold);
            image = createMosaic ();
        }
        else {
            width = 0;
            height = 0;
            image = null;
            nuclei.clear();
        }
        resizeAndRepaint ();
    }

    public void setImage (BufferedImage image) {
        this.image = image;
        resizeAndRepaint ();
    }

    protected void resizeAndRepaint () {
        if (image != null) {
            setPreferredSize (new Dimension
                              ((int)(scale*image.getWidth()+.5)+2*PAD_X,
                               (int)(scale*image.getHeight()+.5)));
        }
        revalidate ();
        repaint ();
    }

    public RenderedImage getImage () { return image; }
    public ColonyAnalysis getColony () { return colony; }
    public void setThreshold (int threshold) {
        colony.setThreshold(threshold);
        colony.analyze();
        if (iou != null) {
            logger.info("=====> precision "+iou.precision(colony.getBitmap()));
        }
        image = createMosaic ();
        resizeAndRepaint ();
    }
    public int getThreshold () { return colony.getThreshold(); }

    @Override
    protected void paintComponent (Graphics g) {
        g.setColor(Color.white);
        g.fillRect(0, 0, getWidth(), getHeight());
        
        if (image == null) {
            return;
        }

        Rectangle bounds = getBounds ();
        Graphics2D g2 = (Graphics2D)g;
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, 
                            RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                            RenderingHints.VALUE_ANTIALIAS_ON);

        //draw (g2);
        int x = (int)((bounds.getWidth()
                       - scale*image.getWidth()+PAD_X)/2.+0.5);
        int y = (int)((bounds.getHeight() - scale*image.getHeight())/2. + 0.5);
        g2.translate(x, y);
        g2.scale(scale, scale);        
        g2.drawImage(image, null, 0, 0);
        drawMasks (g2);

        g2.setPaint(Color.white);        
        x = pt.x - x;
        y = pt.y - y;
        int w = (int)(width*scale+0.5), h = (int)(height*scale+0.5);
        if (x >= 0 && y >= 0 && y <= h) {
            g2.scale(1./scale, 1./scale);
            g2.setXORMode(Color.black);
            int dx = (int)(5/scale +0.5);
            if (x + dx*10 > bounds.getWidth())
                dx = -dx*10;
            int dy = 15;
            if (y + dy > h)
                dy = -dy;
            // convert to image coordinate
            int p = (int)(x/scale + 0.5);
            int q = (int)(y/scale + 0.5);
            g2.drawString("("+(p%width)+","+q+")", x+dx, y+dy);
        }
    }

    void drawMasks (Graphics2D g2) {
        if ((flags & FLAG_MASKS) == FLAG_MASKS) {
            for (Nucleus n : nuclei) {
                // draw fill
                g2.setPaint(Color.green);
                for (Line2D l : n.lines)
                    g2.draw(l);            
            }
            
            for (Nucleus n : nuclei) {
                // draw bounding polygon
                g2.setPaint(Color.red);
                g2.draw(n);
            }
        }
    }

    public void snapshot (String file) throws IOException {
        if (image == null) {
            throw new IllegalArgumentException ("Nothing to take snapshot of");
        }
        BufferedImage img = new BufferedImage 
            (image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        draw (g2);
        g2.dispose();
        ImageIO.write(img, "png", new File (file));
    }

    public void save (String file) throws IOException {
        save (new File (file));
    }
    
    public void save (File file) throws IOException {
        if (image == null)
            throw new RuntimeException ("No image available!");
        BufferedImage img = new BufferedImage 
            (image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, 
                            RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawImage(image, null, 0, 0);
        drawMasks (g2);
        g2.dispose();
        ImageIO.write(img, "png", file);
    }

    BufferedImage createMosaic () {
        BufferedImage raster = colony.getImage(Raster);
        
        BufferedImage img = new BufferedImage 
            (raster.getWidth()*3, 2*raster.getHeight(),
             BufferedImage.TYPE_INT_ARGB);
        
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, 
                            RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                            RenderingHints.VALUE_ANTIALIAS_ON);
        // first row
        g2.drawRenderedImage(raster, null);
        AffineTransform tx = AffineTransform.getTranslateInstance
            ((double)raster.getWidth(), 0.);
        g2.transform(tx);
        g2.drawRenderedImage(colony.getImage(Rescaled), null);
        // draw masks (if any)
        if ((flags & FLAG_MASKS) == FLAG_MASKS) {
            g2.setPaint(Color.green);
            for (Nucleus n : nuclei)
                g2.draw(n);
            drawPolygons (g2);
        }

        tx = AffineTransform.getTranslateInstance
            ((double)raster.getWidth(), 0.);
        g2.transform(tx);        
        g2.drawRenderedImage(colony.getImage(Threshold), null);
        g2.setPaint(Color.black);
        g2.drawRect(0, 0, raster.getWidth()-1, raster.getHeight()-1);
        drawPolygons (g2);

        // second row
        Grayscale grayscale = colony.getGrayscale();
        if (grayscale.getNumChannels() > 1) {
            Grayscale.Channel channel =
                grayscale.getChannel(Grayscale.ChannelR.class);
            if (channel != null) {
                tx = AffineTransform.getTranslateInstance
                    (-2*raster.getWidth(), (double)raster.getHeight());
                g2.transform(tx);
                g2.drawRenderedImage(channel.image(), null);
                g2.setPaint(Color.red);
                g2.drawRect(0, 0, raster.getWidth()-1, raster.getHeight()-1);
            }
            
            channel = grayscale.getChannel(Grayscale.ChannelG.class);
            if (channel != null) {
                tx = AffineTransform.getTranslateInstance
                    ((double)raster.getWidth(), 0.);
                g2.transform(tx);
                g2.drawRenderedImage(channel.image(), null);
                g2.setPaint(Color.green);
                g2.drawRect(0, 0, raster.getWidth()-1, raster.getHeight()-1);
            }

            channel = grayscale.getChannel(Grayscale.ChannelB.class);
            if (channel != null) {
                tx = AffineTransform.getTranslateInstance
                    ((double)raster.getWidth(), 0.);
                g2.transform(tx);
                g2.drawRenderedImage(channel.image(), null);
                g2.setPaint(Color.blue);
                g2.drawRect(0, 0, raster.getWidth()-1, raster.getHeight()-1);
            }
        }
        g2.dispose();
        
        return img;
    }

    void draw (Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, 
                            RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                            RenderingHints.VALUE_ANTIALIAS_ON);

        g2.scale(scale, scale);
        g2.drawRenderedImage(image, null);

        if ((flags & FLAG_POLYGON) != 0)
            drawPolygons (g2);

        if ((flags & FLAG_SEGMENT) != 0)
            drawSegments (g2);
    }

    void drawPolygons (Graphics2D g2) {
        g2.setPaint(Color.red);
        for (Shape a : colony.getPolygons()) {
            g2.draw(a);
        }
    }

    void drawColony (Graphics2D g2, Colony colony) {
        if (colony.hasChildren()) {
            for (Enumeration<Colony> en = colony.children();
                 en.hasMoreElements(); ) {
                drawColony (g2, en.nextElement());
            }
            g2.setPaint(FILL_COLOR);
            g2.fill(colony.getBounds());
        }
        else {
            g2.setPaint(Color.red);
            g2.draw(colony.getBounds());
        }
    }

    void drawColonies (Graphics2D g2) {
        Collection<Colony> colonies = colony.getColonies();
        if (colonies != null) {
            for (Colony c : colonies) {
                drawColony (g2, c);
            }
        }
    }

    void drawSegments (Graphics2D g2) {
        Collection<Path2D> segments = colony.getSegments();
        float[] seg = new float[6];
        int toggle = 0;
        float x = 0, y = 0;
        for (Path2D p : segments) {
            PathIterator iter = p.getPathIterator(null);
            int lines = 0;
            while (!iter.isDone()) {
                int type = iter.currentSegment(seg);

                switch (type) {
                case PathIterator.SEG_LINETO:
                    g2.setPaint(toggle == 0 ? Color.blue : Color.green);
                    g2.drawLine((int)x, (int)y, (int)seg[0], (int)seg[1]);
                    toggle ^= 1;
                    // fall through

                case PathIterator.SEG_MOVETO:
                    x = seg[0];
                    y = seg[1];
                    g2.setPaint(Color.red);
                    g2.fillOval((int)x, (int)y, 2, 2);
                }
                iter.next();
            }
        }
    }

    void createNuclei (Collection<RLE.Run[]> segments) {
        nuclei.clear();
        for (RLE.Run[] runs : segments)
            nuclei.add(new Nucleus (runs));
        
        image = createMosaic (); // regenerate the mosaic
        repaint ();
    }

    public void loadMasks (String name, String file) throws Exception {
        loadMasks (name, new File (file));
    }
    
    public void loadMasks (String name, File file) throws IOException {
        if (height == 0) {
            throw new IllegalArgumentException ("No image loaded!");
        }
        
        logger.info("loading masks from \""+file+"\"...");
        java.util.List<RLE.Run[]> masks = NucleiAnalysis.parseMasks
            (name, height, new FileInputStream (file));

        //DEBUG
        { File mask = new File ("masks");
            mask.mkdirs();
            RLE codec = new RLE (new Bitmap (width, height));
            for (RLE.Run[] r : masks) 
                codec.decode(r);
            
            iou = new IntersectionOverUnion (width, height, masks);
            logger.info("=====> precision "+iou.precision(codec.getBitmap())
                        +" threshold "+iou.precision(colony.getBitmap()));
            /*
            BufferedImage bitmap = codec.getBitmap().createBufferedImage();
            ImageIO.write(bitmap, "png",
                          new FileOutputStream (new File (mask, name+".png")));
            */
        }        
        
        createNuclei (masks);
        
        //DEBUG
        //NucleiAnalysis na = new NucleiAnalysis (colony.getRaster(), masks);
        //model = na.threshold();
    }

    public String load (File imfile, String mask) throws IOException {
        return load (imfile, new File (mask));
    }
    
    public String load (File imfile, File mask) throws IOException {
        String title = null;        
        RenderedImage image = null;        
        try {
            logger.info("Trying to decode as tif...");
            image = TIFFCodec.decode(imfile);
        }
        catch (Exception ex) {
            image = ImageIO.read(imfile);
        }

        if (image != null) {
            ColorModel model = image.getColorModel();
            logger.info(imfile.getName()+": width="+image.getWidth()+" height="
                        +image.getHeight()+" components="
                       +model.getNumComponents()
                        +" model="+model.getClass());
            String name = imfile.getName();
            int pos = name.lastIndexOf('.');
            if (pos > 0)
                name = name.substring(0, pos);

            setRaster (image.getData());
            if (mask != null)
                loadMasks(name, mask);
            
            title = imfile.getName()+": "
                +image.getWidth()+"x"+image.getHeight();
        }
        else {
            logger.log(Level.SEVERE, imfile.getName()
                       +": not a valid image format!");
        }
        
        return title;
    }
    
    static void load (String[] argv) throws Exception {
        File imfile = new File (argv[0]);
        
        ColonyImagePane cip = new ColonyImagePane ();
        String mask = null;
        for (int i = 1; i < argv.length; ++i) {
            int pos = argv[i].indexOf('=');
            if (pos > 0) {
                String param = argv[i].substring(0, pos);
                String value = argv[i].substring(pos+1);
                if (param.equalsIgnoreCase("mask")) {
                    mask = value;
                }
                else if (param.equalsIgnoreCase("scale")) {
                    cip.setScale(Double.parseDouble(value));
                }
            }
        }
        
        String title = cip.load(imfile, mask);

        JToolBar toolbar = new JToolBar ();
        JCheckBox cb = new JCheckBox ("Show mask");
        cb.setSelected(true);
        cb.addActionListener(new ActionListener () {
                public void actionPerformed (ActionEvent e) {
                    JCheckBox cb = (JCheckBox)e.getSource();
                    if (cb.isSelected())
                        cip.show(FLAG_MASKS);
                    else
                        cip.hide(FLAG_MASKS);
                }
            });
        toolbar.add(cb);
        
        final JFrame f = new JFrame ();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel pane = new JPanel (new BorderLayout (0, 0));
        pane.add(toolbar, BorderLayout.NORTH);
        pane.add(new JScrollPane (cip));

        f.getContentPane().add(pane);
        f.pack();
        f.setSize(800, 600);
        f.setVisible(true);
        f.setTitle(title);
    }
        
    public static void main (final String[] argv) throws Exception {
        if (argv.length == 0) {
            System.out.println
                ("Usage: ColonyImagePane IMAGE [mask=FILE|scale=1.0]");
            System.exit(1);
        }

        SwingUtilities.invokeLater(new Runnable () {
                public void run () {
                    try {
                        load (argv);
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
    }
}
