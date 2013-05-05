package tripod.colony;

import java.util.Collection;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.logging.Level;
import java.awt.Shape;

public class TransitiveClosure implements Metric<Colony> {
    private static final Logger logger = 
        Logger.getLogger(TransitiveClosure.class.getName());

    public interface Filter {
        boolean accept (Shape polygon);
    }

    protected Filter filter;
    protected double maxDist;

    public TransitiveClosure () {
    }

    public void setFilter (Filter filter) {
        this.filter = filter;
    }
    public Filter getFilter () { return filter; }

    /**
     * perform transitive closure on a given set of polygons based
     * on nearest neighbor. 
     */
    public Collection<Colony> closure (List<Shape> polygons) {
        NearestNeighbors<Colony> colonies = 
            new NearestNeighbors<Colony>(5, this);

        /*
         * calculate nearest neighbors
         */
        Colony start = null;
        for (Shape poly : polygons) {
            if (filter == null || filter.accept(poly)) {
                Colony colony = new Colony (poly);
                if (start == null) {
                    start = colony;
                }
                colonies.add(colony);
            }
        }

        // get nearest neighbor statistics
        NearestNeighbors.Stats stats = colonies.getNeighborStats();
        maxDist = stats.getMean() + 1.5*Math.sqrt(stats.getVar());
        logger.info("## performing transitive closure on "
                    +colonies.size()+" colonies; stats="+stats);

        /*
         * transitive closure
         */
        List<Colony> visited = new ArrayList<Colony>();
        Colony last = null;
        do {
            last = start;
            transitive (start, visited, colonies);
            for (Colony colony : colonies.entries()) {
                if (visited.indexOf(colony) < 0) {
                    start = colony;
                    //logger.info("## new starting point "+colony);
                }
            }
            //stats = colonies.getNeighborStats();
            //maxDist = stats.getMean() + Math.sqrt(stats.getVar());
        }
        while (last != start);
        logger.info("## "+colonies.size()+" after transitive closure!");

        return colonies.entries();
    }

    protected void transitive 
        (Colony colony, List<Colony> visited, 
         NearestNeighbors<Colony> colonies) {
        visited.add(colony);
        NearestNeighbors.Neighbor<Colony> nb = colonies.neighbor(colony);
        /*
        logger.info("## transitive closure "+colony+"-"
                    +(nb != null 
                      ? (""+nb.getNeighbor()+" "+nb.getValue()):""));
        */
        if (nb != null && (nb.getValue() < maxDist 
                           /*|| colony.overlaps(nb.getNeighbor())*/)) {
            Colony merged = new Colony (colony, nb.getNeighbor());
            colonies.remove(colony);
            colonies.remove(nb.getNeighbor());
            colonies.add(merged);
            //logger.info(" ==> "+merged);
            transitive (merged, visited, colonies);
        }
    }

    /**
     * Metric interface
     */
    public double evaluate (Colony colony1, Colony colony2) {
        List<Colony> leafs1 = colony1.getTerminalColonies();
        List<Colony> leafs2 = colony2.getTerminalColonies();
        double min = Double.MAX_VALUE;
        for (Colony c1 : leafs1)
            for (Colony c2 : leafs2) {
                double dist = GeomUtil.centroidDistance
                    (c1.getBounds(), c2.getBounds());
                if (dist < min) 
                    min = dist;
            }
        return min;
    }
}

