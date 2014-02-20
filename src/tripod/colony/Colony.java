package tripod.colony;

import java.util.logging.Logger;
import java.util.List;
import java.util.Collection;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.awt.Shape;
import java.awt.geom.Path2D;
import java.awt.geom.Area;
import java.awt.geom.Point2D;

/**
 * A colony can only belong to one and only one well. It's possible
 * for a colony to have sub colonies. This is particular the case
 * when colonies are clumped together.
 */
public class Colony {
    private static final Logger logger = 
        Logger.getLogger(Colony.class.getName());

    /* When the colony has children, its bounding polygon is the
     * union of all the child polygons. For colony without children
     * the bounding polygon must be specified via the constructor.
     */
    protected Shape bounds;
    protected Area area;
    protected Collection<Path2D> segments = new ArrayList<Path2D>();
    protected List<Colony> children; // sub colonies (if any)
    private boolean isDirty = false;

    public Colony () {
        children = new ArrayList<Colony>();
        area = new Area ();
    }

    public Colony (Shape bounds) {
        if (bounds == null) {
            throw new IllegalArgumentException
                ("Can't have colony with no bounds!");
        }
        this.bounds = bounds;
        this.area = new Area (bounds);
    }

    public Colony (Colony... children) {
        this.children = new ArrayList<Colony>();
        bounds = new Area ();
        for (Colony c : children) 
            addChild (c);
    }

    public Area getArea () { return area; }
    public Shape getBounds () { 
        if (!isDirty && bounds != null)
            ;
        else {
            List<Point2D> points = new ArrayList<Point2D>();
            for (Colony c : getTerminalColonies ()) {
                for (Point2D pt : GeomUtil.vertices(c.getBounds()))
                    points.add(pt);
            }
            bounds = GeomUtil.convexHull(points.toArray(new Point2D[0]));
            isDirty = false;
        }
        return bounds;
    }
    public int getChildCount () { 
        return children != null ? children.size() : 0; 
    }
    public boolean hasChildren () { return getChildCount () > 0; }
    public void add (Colony child) {
        if (children == null) { // 
            throw new IllegalStateException
                ("This colony can't have any children!");
        }

        if (children.indexOf(child) < 0) {
            addChild (child);
        }
    }

    public void setSegments (Collection<Path2D> segments) {
        Shape bounds = getBounds ();
        segments.clear();
    }
    public Collection<Path2D> getSegments () { return segments; }

    public List<Colony> getTerminalColonies () {
        if (children == null) {
            return Arrays.asList(this);
        }

        List<Colony> leafs = new ArrayList<Colony>();
        depthFirstTraversal (this, leafs);

        return leafs;
    }

    protected static void depthFirstTraversal 
        (Colony colony, List<Colony> leafs) {
        if (colony.children == null) { // leaf
            leafs.add(colony);
        }
        else {
            for (Colony child : colony.children) {
                depthFirstTraversal (child, leafs);
            }
        }
    }

    protected void addChild (Colony child) {
        children.add(child);
        area.add(child.area);
        isDirty = true;
    }

    public void remove (Colony child) {
        if (children == null) { // 
            throw new IllegalStateException
                ("This colony doesn't have children!");
        }

        if (children.remove(child)) {
            area.subtract(child.area);
            isDirty = true;
        }
    }
    public Colony getChild (int index) { 
        if (children == null) {
            throw new IllegalStateException ("This colonly has no children!");
        }
        return children.get(index); 
    }
    public boolean hasChild (Colony child) { 
        if (children == null) {
            throw new IllegalStateException ("This colonly has no children!");
        }
        return children.contains(child);
    }
    public Enumeration<Colony> children () {
        if (children == null) {
            throw new IllegalStateException ("This colonly has no children!");
        }
        return Collections.enumeration(children);
    }

    public boolean overlaps (Colony colony) {
        return bounds.intersects(colony.getBounds().getBounds2D());
    }

    public String toString () {
        return "Colony{bounds="+bounds.getBounds()+",children="
            +(children != null ? children.size() : "none")+"}";
    }
}
