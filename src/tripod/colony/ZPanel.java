package tripod.colony;

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.color.*;
import java.awt.image.*;
import java.awt.event.*;
import javax.swing.*;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.PathIterator;
import java.awt.geom.NoninvertibleTransformException;
import javax.imageio.*;

import java.util.logging.Logger;
import java.util.logging.Level;

public class ZPanel extends JPanel 
    implements MouseListener, MouseMotionListener, ComponentListener {
    static final Logger logger = Logger.getLogger
        (ZPanel.class.getName());

    static final Color SPHEROID = new Color (0f, 1f, 0f, .2f);
    static final BasicStroke STROKE = new BasicStroke (2.f);

    static BufferedImage LOGO;
    static {
        try {
            LOGO = ImageIO.read(ZPanel.class.getResourceAsStream
                                ("resources/ncgc_logo.png"));
        }
        catch (Exception ex) {
            logger.warning("Can't load logo!");
        }
    }

    public enum AnnotationTool {
        Rectangle,
            Ellipse,
            Line
            }

    public static class Annotation {
	String name;
	Shape shape;

	Annotation (String name, Shape shape) {
	    this.name = name;
	    this.shape = shape;
	}

	public String toString () {
	    return "{"+name+":"+shape+"}";
	}
    }

    class AnnotationDialog extends JDialog implements ActionListener {
	JTextField field;

	AnnotationDialog (JFrame parent) {
	    super (parent, true);
	    setTitle ("New Annotation");
	    setDefaultCloseOperation (DO_NOTHING_ON_CLOSE);

	    Box vbox = Box.createVerticalBox();
	    Box box = Box.createHorizontalBox();
	    
	    box.add(new JLabel ("Annotation", JLabel.LEADING));
	    box.add(Box.createHorizontalGlue());
	    vbox.add(box);
	    vbox.add(Box.createVerticalStrut(2));
	    vbox.add(field = new JTextField ());
	    field.addActionListener(this);
	    vbox.add(Box.createVerticalStrut(2));
	    JPanel bp = new JPanel (new GridLayout (1, 2, 5, 0));
	    JButton btn;
	    bp.add(btn = new JButton ("OK"));
	    btn.addActionListener(this);
	    bp.add(btn = new JButton ("Cancel"));
	    btn.addActionListener(this);
	    JPanel bbp = new JPanel ();
	    bbp.add(bp);
	    vbox.add(bbp);
	    vbox.add(Box.createVerticalGlue());

	    JPanel panel = new JPanel (new BorderLayout (0, 2));
	    panel.add(vbox, BorderLayout.NORTH);
	    getContentPane().add(panel);
	    pack ();
	}

	public void actionPerformed (ActionEvent e) {
	    String cmd = e.getActionCommand();
	    if (cmd.equalsIgnoreCase("cancel")) {
		field.setText(null);
	    }
	    setVisible (false);
	}

	void clear () { 
	    field.setText(null);
	}

	public String getInputValue () {
	    clear ();
	    setVisible (true);
	    String label = field.getText();
	    if (label == null || label.equals("")) {
		return null;
	    }
	    return label;
	}
    }

    protected ZPlane zplane;
    protected AffineTransform tx = AffineTransform.getScaleInstance(1., 1.);
    protected int x, y, width, height; // scaled image width and height
    protected AnnotationTool tool = AnnotationTool.Rectangle;

    protected Point currentPt; // current point
    protected Shape currentPolygon; 

    // current drawing shape
    private AffineTransform noAfx = new AffineTransform ();
    private Point start = null, end = null;
    private Rectangle rubberband = new Rectangle ();
    private Line2D line = new Line2D.Double();
    private Ellipse2D ellipse = new Ellipse2D.Double();
    private boolean selection;

    private AnnotationDialog dialog;
    private ArrayList<Annotation> annotations = new ArrayList<Annotation>();

    public ZPanel () {
        setCursor (Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        addMouseMotionListener (this);
        addMouseListener (this);
        addComponentListener (this);
    }

    public void setZPlane (ZPlane zplane) {
        this.zplane = zplane;

        if (zplane != null && currentPt != null) {
            Shape poly = null;
            for (Shape s : zplane.getPolygons())
                if (s.contains(currentPt))
                    poly = s;
            pick (currentPt, poly);
            currentPolygon = poly;
        }
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

    public void setAnnotationTool (AnnotationTool tool) {
        this.tool = tool;
    }
    public AnnotationTool getAnnotationTool () { return tool; }

    protected Rectangle getRubberband () {
	if (start == null  || end == null) {
	    return null;
	}
	int x = Math.min(start.x, end.x);
	int y = Math.min(start.y, end.y);
	int w = Math.max(start.x, end.x) - x + 1;
	int h = Math.max(start.y, end.y) - y + 1;
	rubberband.x = x;
	rubberband.y = y;
	rubberband.width = w;
	rubberband.height = h;
	return rubberband;
    }

    protected Line2D getLine () {
	if (start == null  || end == null) {
	    return null;
	}

        line.setLine(start.x, start.y, end.x, end.y);
        return line;
    }

    protected Ellipse2D getEllipse () {
        if (start == null || end == null)
            return null;
        ellipse.setFrameFromDiagonal(start.x, start.y, end.x, end.y);
        return ellipse;
    }

    protected Shape getAnnotationShape () {
        if (selection)
            return getRubberband ();

        switch (tool) {
        case Rectangle: return getRubberband ();
        case Line: return getLine ();
        case Ellipse: return getEllipse ();
        default: throw new IllegalStateException
                ("Unknown annotation tool "+tool);
        }
    }

    /**
     * MouseListener
     */
    public void mouseClicked (MouseEvent e) {
	clickGesture (e);
    }
    public void mouseEntered (MouseEvent e) {
    }
    public void mouseExited (MouseEvent e) {
    }
    public void mousePressed (MouseEvent e) {
	clickGesture (e);
    }
    public void mouseReleased (MouseEvent e) {
	Shape shape = getAnnotationShape ();
	if (shape != null) {
            /*
	    if (dialog == null) {
		Container c = getParent ();
		while (!(c instanceof JFrame)) {
		    c = c.getParent();
		}
		dialog = new AnnotationDialog ((JFrame)c);
	    }
	    String label = dialog.getInputValue();
	    if (label != null) {
		annotations.add(new Annotation (label, rb));
	    }
            */
	}

	start = null;
	end = null;
	repaint ();
    }

    void clickGesture (MouseEvent e) {
        start = e.getPoint();
        selection = e.isShiftDown();
    }

    // return in image coordinate
    Point getImagePt (Point p) {
        try {
            Point pt = new Point (p.x-x, p.y - y);
            tx.inverseTransform(pt, pt);
            return pt;
        }
        catch (NoninvertibleTransformException ex) {
            ex.printStackTrace();
        }
        return p;
    }

    /**
     * MouseMotionListener
     */
    public void mouseDragged (MouseEvent e) {
	if ((e.getModifiersEx() & MouseEvent.BUTTON1_DOWN_MASK) 
	    == MouseEvent.BUTTON1_DOWN_MASK) {
	    end = e.getPoint();
	}
        trackMouse (e);
    }

    public void mouseMoved (MouseEvent e) {
        trackMouse (e);
    }

    protected void trackMouse (MouseEvent e) {
        if (zplane != null) {
            Point pt = getImagePt (e.getPoint());
            if (pt.x >= 0 && pt.x < zplane.getWidth() 
                && pt.y >= 0 && pt.y < zplane.getHeight()) {
                
                Shape poly = null;
                for (Shape s : zplane.getPolygons()) 
                    if (s.contains(pt))
                        poly = s;
                
                pick (pt, poly);
                currentPt = pt;
                currentPolygon = poly;
            }
        }
        repaint ();
    }

    protected Point getCurrentPt () { return currentPt; }
    protected Shape getCurrentPolygon () { return currentPolygon; }

    /**
     * Override by subclass
     */
    protected void pick (Point pt, Shape polygon) { }

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
	g.setColor(Color.white);
	g.fillRect(0, 0, getWidth(), getHeight());

        Graphics2D g2 = (Graphics2D)g;
        if (zplane == null) {
            drawLogo (g2);
        }
        else {
            draw (g2);
        }
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
        g2.setStroke(STROKE);

        g2.translate(x, y);
        g2.transform(tx);
        g2.drawRenderedImage(zplane.getDisplay(), null);

        Shape anno = getAnnotationShape ();
        for (Shape s : zplane.getPolygons()) {
            //g2.fill(s);
            if ((currentPt != null && s.contains(currentPt))
                || (anno != null && Util.checkContainment
                    (anno, s, g2.getTransform())))
                g2.setPaint(Color.green);
            else
                g2.setPaint(Color.white);
            g2.draw(s);
        }

        g2.setTransform(noAfx);
        if (anno != null) {
            g2.setColor(Color.red);
            g2.draw(anno);
        }
    }

    protected void drawLogo (Graphics2D g2) {
        Rectangle bounds = getBounds ();
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, 
			    RenderingHints.VALUE_RENDER_QUALITY);
	g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
			    RenderingHints.VALUE_ANTIALIAS_ON);

        // scale the log to half the current size of the component
        double scale = (double)bounds.width/(2*LOGO.getWidth());
        AffineTransform afx = new AffineTransform ();
        afx.translate(bounds.width/4., 
                      (bounds.height - LOGO.getHeight()*scale)/2.);
        afx.scale(scale, scale);

        g2.drawRenderedImage(LOGO, afx);
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
