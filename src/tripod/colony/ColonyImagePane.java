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

    static class Mask {
        public ArrayList<int[]> runs = new ArrayList<>();
    }

    protected long flags = FLAG_IMAGE|FLAG_POLYGON|FLAG_SEGMENT|FLAG_MASKS;
    protected BufferedImage image;
    protected ColonyAnalysis colony = new ColonyAnalysis ();
    protected int width, height; // original raster width & height
    protected double scale = 1.;
    protected Point pt = new Point ();
    
    protected ArrayList<Shape> masks = new ArrayList<>();
    protected ArrayList<Shape> fills = new ArrayList<>();
    
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
        repaint ();
    }
    
    public void hide (long flag) {
        flags &=~flag;
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
            masks.clear();
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
            // draw fill
            g2.setPaint(Color.green);
            for (Shape m : fills)
                g2.draw(m);
            
            // draw bounding polygon
            g2.setPaint(Color.red);
            for (Shape m : masks)
                g2.draw(m);
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
            (raster.getWidth()*3, raster.getHeight(),
             BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, 
                            RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
                            RenderingHints.VALUE_ANTIALIAS_ON);
        g2.drawRenderedImage(raster, null);
        AffineTransform tx = AffineTransform.getTranslateInstance
            ((double)raster.getWidth(), 0.);
        g2.transform(tx);
        g2.drawRenderedImage(colony.getImage(Rescaled), null);
        drawPolygons (g2);
        tx = AffineTransform.getTranslateInstance
            ((double)raster.getWidth(), 0.);
        g2.transform(tx);        
        g2.drawRenderedImage(colony.getImage(Threshold), null);
        g2.setPaint(Color.black);
        g2.drawRect(0, 0, raster.getWidth()-1, raster.getHeight()-1);
        drawPolygons (g2);
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

    void masksToFills (Collection<Mask> runs) {
        fills.clear();
        masks.clear();
        for (Mask m : runs) {
            ArrayList<Point2D> pts = new ArrayList<>();
            for (int[] r : m.runs) {
                double x0 = (double)(r[0]-1) / height;
                double y0 = (double)((r[0]-1) % height);
                pts.add(new Point2D.Double(x0, y0));
                if (r[1] > 1) {
                    double x1 = x0, y1 = y0+(r[1]-1);
                    fills.add(new Line2D.Double(x0, y0, x1, y1));
                    pts.add(new Point2D.Double(x1, y1));
                }
                else {
                    fills.add(new Line2D.Double(x0, y0, x0, y0));
                }
            }
            
            if (!pts.isEmpty())
                masks.add(GeomUtil.convexHull(pts.toArray(new Point2D[0])));
        }
    }

    public void loadMasks (String name, String file) throws Exception {
        loadMasks (name, new File (file));
    }
    
    public void loadMasks (String name, File file) throws IOException {
        if (height == 0) {
            throw new IllegalArgumentException ("No image loaded!");
        }
        
        logger.info("loading masks from \""+file+"\"...");
        BufferedReader br = new BufferedReader (new FileReader (file));
        br.readLine(); // skip header

        CodecRunLength codec = new CodecRunLength (new Bitmap (width, height));

        ArrayList<Mask> masks = new ArrayList<>();
        for (String line; (line = br.readLine()) != null; ) {
            String[] toks = line.split(",");
            if (toks.length == 2 && toks[0].equals(name)) {
                String[] runlen = toks[1].split("\\s+");
                if (runlen.length % 2 == 0) {
                    Mask mask = new Mask ();
                    for (int i = 0; i < runlen.length; i+=2) {
                        int index = Integer.parseInt(runlen[i]);
                        int len = Integer.parseInt(runlen[i+1]);
                        codec.decode(index, len);
                        mask.runs.add(new int[]{index,len});
                    }
                    
                    Collections.sort(mask.runs, new Comparator<int[]>() {
                            public int compare (int[] a, int[] b) {
                                return a[0] - b[0];
                            }
                        });
                    masks.add(mask);
                }
                else {
                    logger.warning
                        ("Bad run length; not even number of tokens:\n"
                         +toks[1]);
                }
            }
        }
        br.close();
        masksToFills (masks);

        //DEBUG
        BufferedImage bitmap = codec.getBitmap().createBufferedImage();
        ImageIO.write(bitmap, "png",
                      new FileOutputStream (name+"_mask.png"));
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
                loadMasks (name, mask);
            
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
