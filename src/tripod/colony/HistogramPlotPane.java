package tripod.colony;

import java.util.*;
import java.io.*;
import java.net.URL;
import java.util.logging.Logger;
import java.util.logging.Level;

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.*;
import javax.swing.*;
import javax.swing.event.*;

import org.jfree.chart.*;
import org.jfree.chart.plot.*;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.DefaultXYDataset;
import org.jfree.data.xy.XYBarDataset;
import org.jfree.data.xy.DefaultIntervalXYDataset;
import org.jfree.chart.renderer.xy.*;
import org.jfree.data.statistics.SimpleHistogramDataset;
import org.jfree.data.statistics.SimpleHistogramBin;

public class HistogramPlotPane extends JPanel {
    static private final Logger logger = Logger.getLogger
	(HistogramPlotPane.class.getName());

    private ChartPanel chartPanel;
    private XYPlot xyplot;
    private JFreeChart chart;

    public HistogramPlotPane () {
	setOpaque (true);
	setBackground(Color.white);

	chartPanel = new ChartPanel 
	    (chart = ChartFactory.createXYBarChart
	     (null, null, false, null, null, PlotOrientation.VERTICAL, 
	      false, false, false));

	//chart.setBorderPaint(Color.white);
	chart.setBackgroundPaint(Color.white);
	chart.setAntiAlias(true);
	chart.setTextAntiAlias(true);

	chart.getPlot().setBackgroundAlpha(.5f);
	
	xyplot = chart.getXYPlot();
	xyplot.setOutlineVisible(false);
	xyplot.setRangeGridlinesVisible(false);
	xyplot.setDomainCrosshairVisible(true);
	xyplot.setDomainGridlinesVisible(false);
	xyplot.getDomainAxis().setVisible(true);
	xyplot.getRangeAxis().setVisible(false);
	xyplot.setRangeZeroBaselineVisible(false);
	xyplot.setDomainZeroBaselineVisible(false);
	XYBarRenderer renderer = new XYBarRenderer ();
	renderer.setSeriesPaint(0, Color.blue);
	renderer.setSeriesPaint(1, Color.red);
	xyplot.setRenderer(renderer);
	xyplot.setNoDataMessage("No data");
	
	setLayout (new BorderLayout ());
	setBorder (BorderFactory.createEmptyBorder(2,2,2,2));
	setBackground (Color.white);
	add (chartPanel);
    }

    public XYPlot getPlot () { return xyplot; }

    @Override
    public void setBackground (Color c) {
	if (chart != null) {
	    chart.setBackgroundPaint(c);
	}
	else {
	    super.setBackground(c);
	}
    }

    public void setColor (Color color) {
	((XYBarRenderer)xyplot.getRenderer()).setSeriesPaint(0, color);
    }

    public void setLabel (String label) {
	xyplot.getDomainAxis().setLabel(label);
    }
    public String getLabel () { 
	return xyplot.getDomainAxis().getLabel(); 
    }

    public void setTitle (String title) {
	chart.setTitle(title);
    }

    public void setData (String key, Histogram hist) {
	if (hist == null) {
	    return;
	}

	SimpleHistogramDataset ds = new SimpleHistogramDataset (key);
	double[] ranges = hist.getRanges();
	for (int i = 1; i < ranges.length; ++i) {
	    try {
		SimpleHistogramBin bin = new SimpleHistogramBin
		    (ranges[i-1], ranges[i], true, false);
		bin.setItemCount((int)hist.get(i-1));
		ds.addBin(bin);
	    }
	    catch (Exception ex) {
		logger.log(Level.SEVERE, "Bad histogram bin: ["+ranges[i-1]
			   +","+ranges[i]+") => "+hist, ex);
	    }
	}

	xyplot.setDataset(ds);
    }

    public void setData (Histogram... hist) {
	if (hist == null) {
	    return;
	}

	int parity = 0;
	DefaultIntervalXYDataset ds = new DefaultIntervalXYDataset ();
	for (int i = 0; i < hist.length; ++i) {
	    Histogram h = hist[i];
	    if (h != null) {
		double[] ranges = h.getRanges();
		double[][] data = new double[6][ranges.length-1];
		double sum = 0.;
		for (int j = 1, k = 0; j < ranges.length; ++j, ++k) {
		    double x0 = ranges[j-1];
		    double x1 = ranges[j];
		    double x = (x1-x0)/2.;
		    data[0][k] = x;
		    data[1][k] = x0;
		    data[2][k] = x1;
		    sum += h.get(k);
		    data[3][k] = data[4][k] = data[5][k] 
			= (parity == 0 ? 1 : -1) * h.get(k);
		}

		// normalize
		for (int j = 0; j < ranges.length-1; ++j) {
		    data[3][j] /= sum;
		    data[4][j] /= sum;
		    data[5][j] /= sum;
		}
		ds.addSeries("Series-"+i, data);
	    }
	    parity ^= 1;
	}

	xyplot.setDataset(ds);
    }

    public void clear () {
	xyplot.setDataset(null);
	repaint ();
    }
}
