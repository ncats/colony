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
import java.awt.geom.NoninvertibleTransformException;
import javax.imageio.*;

import java.util.logging.Logger;
import java.util.logging.Level;

public class ZPanel extends JPanel 
    implements MouseMotionListener, ComponentListener {
    static final Logger logger = Logger.getLogger
        (ZPanel.class.getName());

    static final Color SPHEROID = new Color (0f, 1f, 0f, .2f);
    static final BasicStroke STROKE = new BasicStroke (2.f);

    ZPlane zplane;
    AffineTransform tx = AffineTransform.getScaleInstance(1., 1.);
    int x, y, width, height; // scaled image width and height

    public ZPanel () {
        setCursor (Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        addMouseMotionListener (this);
        addComponentListener (this);
    }

    public void setZPlane (ZPlane zplane) {
        this.zplane = zplane;
        resizeAndRepaint ();
    }
    public ZPlane getZPlane () { return zplane; }

    public void scale (double sx, double sy) {
        tx.setToScale(sx, sy);
        resizeAndRepaint ();
    }

    public void scale (double scale) {
        scale (scale, scale);
    }

    /**
     * MouseMotionListener
     */
    public void mouseDragged (MouseEvent e) {
        trackMouse (e);
    }

    public void mouseMoved (MouseEvent e) {
        trackMouse (e);
    }

    protected void trackMouse (MouseEvent e) {
        if (zplane != null) {
            try {
                Point pt = new Point (e.getX()-x, e.getY()-y);
                tx.inverseTransform(pt, pt);
                if (pt.x >= 0 && pt.x < zplane.getWidth() 
                    && pt.y >= 0 && pt.y < zplane.getHeight()) {
                    pick (pt);
                }
            }
            catch (NoninvertibleTransformException ex) {
            }
        }
    }

    protected void pick (Point pt) {
    }

    /**
     * ComponentListener
     */
    public void componentHidden (ComponentEvent ev) {
    }
    public void componentMoved (ComponentEvent ev) {
    }
    public void componentResized (ComponentEvent ev) {
        reset ();
    }
    public void componentShown (ComponentEvent ev) {
        resizeAndRepaint ();
    }

    @Override
    protected void paintComponent (Graphics g) {
        if (zplane == null) {
            return;
        }

	g.setColor(Color.white);
	g.fillRect(0, 0, getWidth(), getHeight());

        Graphics2D g2 = (Graphics2D)g;
        draw (g2);
    }

    protected void reset () {
        Rectangle bounds = getBounds ();
        x = Math.max(0, (bounds.width - width)/2);
        y = Math.max(0, (bounds.height - height)/2);
    }

    protected void resizeAndRepaint () {
        if (zplane != null) {
            width = (int)(tx.getScaleX()*zplane.getWidth()+.5);
            height = (int)(tx.getScaleY()*zplane.getHeight()+.5);
            setPreferredSize (new Dimension (width, height));
        }
        reset ();
        revalidate ();
        repaint ();
    }

    protected void draw (Graphics2D g2) {
        Rectangle bounds = getBounds ();

        g2.setRenderingHint(RenderingHints.KEY_RENDERING, 
			    RenderingHints.VALUE_RENDER_QUALITY);
	g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
			    RenderingHints.VALUE_ANTIALIAS_ON);
        
        g2.translate(x, y);
        g2.transform(tx);
        g2.drawRenderedImage(zplane.getDisplay(), null);

        g2.setPaint(Color.white);
        g2.setStroke(STROKE);
        for (Shape s : zplane.getPolygons()) {
            //g2.fill(s);
            g2.draw(s);
        }
    }

    public void load (File file) throws Exception {
        setZPlane (new ZPlane (file));
    }

    static void load (String file) throws Exception {
        ZPanel sp = new ZPanel ();
        sp.scale(.5);
        sp.load(new File (file));

        final JFrame f = new JFrame ();
        f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        f.getContentPane().add(new JScrollPane (sp));
        f.pack();
        f.setSize(800, 600);
        f.setVisible(true);
    }
        
    public static void main (final String[] argv) throws Exception {
        if (argv.length == 0) {
            System.out.println("Usage: ZPanel IMAGE.TIF");
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
