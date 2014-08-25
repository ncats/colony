package tripod.colony;

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.color.*;
import java.awt.image.*;
import java.awt.event.*;
import javax.swing.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.PathIterator;
import javax.imageio.*;

import java.util.logging.Logger;
import java.util.logging.Level;


public class ColonyImagePane extends JPanel implements MouseMotionListener {
    static final Logger logger = Logger.getLogger
        (ColonyImagePane.class.getName());

    static final Color FILL_COLOR = new Color (0x9e, 0xe6, 0xcd, 120);

    public static final long FLAG_IMAGE = 1L<<0;
    public static final long FLAG_GRAY = 1L<<1;
    public static final long FLAG_BINARY = 1L<<2;
    public static final long FLAG_SKELETON = 1L<<3;
    public static final long FLAG_SEGMENT = 1L<<4;
    public static final long FLAG_POLYGON = 1L<<5;


    protected long flags = FLAG_IMAGE | FLAG_POLYGON| FLAG_SEGMENT;
    protected BufferedImage image;
    protected ColonyAnalysis colony = new ColonyAnalysis ();
    protected AffineTransform tx = AffineTransform.getScaleInstance(1., 1.);

    public ColonyImagePane () {
        addMouseMotionListener (this);
    }

    public void mouseDragged (MouseEvent e) {}
    public void mouseMoved (MouseEvent e) {
        int x = e.getX(), y = e.getY();
        //int pixel = image.getData().getSample(x, y, 0);
        //System.out.println("("+x+","+y+") = "+pixel);
    }

    public void setScale (double scale) {
        tx.setToScale(scale, scale);
        resizeAndRepaint ();
    }

    public void setRaster (Raster raster) {
        if (raster != null) {
            colony.setRaster(raster);
            colony.analyze();
            image = colony.getImage(ColonyAnalysis.Type.Rescaled);
            /*
            try {
                snapshot ("snapshot.png");
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
            */
        }
        else {
            image = null;
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
                              ((int)(tx.getScaleX()*image.getWidth()+.5), 
                               (int)(tx.getScaleY()*image.getHeight()+.5)));
        }
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
        draw (g2);
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

    void draw (Graphics2D g2) {
	g2.setRenderingHint(RenderingHints.KEY_RENDERING, 
			    RenderingHints.VALUE_RENDER_QUALITY);
	g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
			    RenderingHints.VALUE_ANTIALIAS_ON);

        g2.transform(tx);
        g2.drawRenderedImage(image, null);

        if ((flags & FLAG_POLYGON) != 0)
            drawColonies (g2);

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

            /*
            g2.setPaint(Color.blue);
            g2.draw(p);
            Point2D pt = p.getCurrentPoint();
            if (pt != null) {
                g2.setPaint(Color.red);
                g2.drawString(String.valueOf(lines), (int)pt.getX(), (int)pt.getY());
            }
            */
        }
    }

    static void load (String file) throws Exception {
        ColonyImagePane cip = new ColonyImagePane ();
        cip.setScale(2.);
        RenderedImage image = TIFFCodec.decode(new File (file));
        if (image != null) {
            cip.setRaster(image.getData());
            TIFFCodec.encode("threshold.tif", cip.getColony().getImage(ColonyAnalysis.Type.Threshold));
        }
        
        final JFrame f = new JFrame ();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(new JScrollPane (cip));
        f.pack();
        f.setSize(800, 600);
        f.setVisible(true);
    }
        
    public static void main (final String[] argv) throws Exception {
        if (argv.length == 0) {
            System.out.println("Usage: ColonyImagePane IMAGE.TIF");
            System.exit(1);
        }

        SwingUtilities.invokeLater(new Runnable () {
                public void run () {
                    try {
                        load (argv[0]);
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
    }
}
