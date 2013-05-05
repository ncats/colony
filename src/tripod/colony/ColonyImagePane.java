package tripod.colony;

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.color.*;
import java.awt.image.*;
import java.awt.event.*;
import javax.swing.*;

import javax.imageio.*;

import java.util.logging.Logger;
import java.util.logging.Level;


public class ColonyImagePane extends JPanel implements MouseMotionListener {
    static final Logger logger = Logger.getLogger
        (ColonyImagePane.class.getName());

    static final Color FILL_COLOR = new Color (0x9e, 0xe6, 0xcd, 120);


    protected BufferedImage image;
    protected ColonyAnalysis colony = new ColonyAnalysis ();

    public ColonyImagePane () {
        addMouseMotionListener (this);
    }

    public void mouseDragged (MouseEvent e) {}
    public void mouseMoved (MouseEvent e) {
        int x = e.getX(), y = e.getY();
        //int pixel = image.getData().getSample(x, y, 0);
        //System.out.println("("+x+","+y+") = "+pixel);
    }

    public void setRaster (Raster raster) {
        if (raster != null) {
            setPreferredSize (new Dimension
                              (raster.getWidth(), raster.getHeight()));
            colony.setRaster(raster);
            colony.analyze();
            image = colony.getImage(ColonyAnalysis.Type.Rescaled);
            try {
                snapshot ("snapshot.png");
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        else {
            image = null;
        }
        repaint ();
    }

    public void setImage (BufferedImage image) {
        this.image = image;
        if (image != null) {
            setPreferredSize (new Dimension
                              (image.getWidth(), image.getHeight()));
        }
        repaint ();
    }

    public RenderedImage getImage () { return image; }


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
        g2.drawImage(image, 0, 0, null);

	g2.setRenderingHint(RenderingHints.KEY_RENDERING, 
			    RenderingHints.VALUE_RENDER_QUALITY);
	g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
			    RenderingHints.VALUE_ANTIALIAS_ON);

        drawColonies (g2);
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

    static void load (String file) throws Exception {
        ColonyImagePane cip = new ColonyImagePane ();
        cip.setRaster(TIFFCodec.decode(new File (file)));
        TIFFCodec.encode("colony.tif", cip.getImage());

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
