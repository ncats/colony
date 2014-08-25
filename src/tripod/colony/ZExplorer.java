package tripod.colony;

import java.util.logging.Logger;
import java.util.logging.Level;

import java.util.*;
import java.io.*;
import java.awt.*;
import java.awt.geom.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import javax.swing.plaf.UIResource;
import javax.swing.tree.*;
import javax.swing.table.*;

import com.jgoodies.looks.plastic.Plastic3DLookAndFeel;
import com.jidesoft.swing.JideTabbedPane;

public class ZExplorer extends JFrame 
    implements ActionListener, Icons {

    static final Logger logger = Logger.getLogger(ZExplorer.class.getName());

    class ZZPanel extends ZPanel {
        Map<Shape, Histogram> cache = new HashMap<Shape, Histogram>();

        @Override
        protected void pick (Point pt, Shape polygon) {
            statusField.setText(String.format
                                ("(%1$d,%2$d,%3$d)", 
                                 pt.x, pt.y, getZPlane().get(pt.x, pt.y)));
            updatePolygon (getZPlane (), polygon);
            if (polygon != getCurrentPolygon ()) {
                Histogram hist = cache.get(polygon);
                if (hist == null) {
                    hist = getZPlane().getHistogram(polygon);
                    cache.put(polygon, hist);
                }
                hpp.setData(zplane.getName(), hist);
            }
        }

        public void clear () {
            cache.clear();
        }
    }

    class ImageStackTreeModel extends DefaultTreeModel {
        ImageStackTreeModel () {
            super (new DefaultMutableTreeNode ("Image Stacks"), true);
        }
    }

    class ImageStackTree extends JTree implements TreeSelectionListener {
        ImageStackTree () {
            super (new ImageStackTreeModel ());
            setExpandsSelectedPaths (true);
            setScrollsOnExpand (true);
            setShowsRootHandles (false);
            expandRow (0);
            addTreeSelectionListener (this);
            setCellRenderer (new ImageStackTreeCellRenderer ());
            setDragEnabled (false);
        }

        void reload () {
            ((ImageStackTreeModel)getModel()).reload();
        }

        void reload (DefaultMutableTreeNode node) {
            ((ImageStackTreeModel)getModel()).reload(node);
        }

        void expand (DefaultMutableTreeNode node) {
            if (node != null) {
                TreePath path = new TreePath (node.getPath());
                expandPath (path);
            }
        }

        DefaultMutableTreeNode getRoot () { 
            return (DefaultMutableTreeNode) getModel().getRoot(); 
        }

        void removeSelections () {
            TreePath[] paths = getSelectionPaths ();
            if (paths != null) {
                Set<DefaultMutableTreeNode> parents = 
                    new HashSet<DefaultMutableTreeNode>();

                for (TreePath p : paths) {
                    DefaultMutableTreeNode node = 
                        (DefaultMutableTreeNode)p.getLastPathComponent();
                    DefaultMutableTreeNode parent = 
                        (DefaultMutableTreeNode)node.getParent();
                    node.removeFromParent();
                    Object obj = parent.getUserObject();
                    if (obj instanceof ZStack) {
                        ((ZStack)obj).remove((ZPlane)node.getUserObject());
                    }
                    node.setUserObject(null);
                    parents.add(parent);
                }
                reload ();
                for (DefaultMutableTreeNode parent : parents)
                    expand (parent);
            }
        }

        public void valueChanged (TreeSelectionEvent e) {
            JTree tree = (JTree)e.getSource();
            TreePath path = e.getPath();
            DefaultMutableTreeNode node = 
                (DefaultMutableTreeNode)path.getLastPathComponent();
            ZExplorer.this.show(node);
        }
    }

    class ImageStackTreeCellRenderer extends DefaultTreeCellRenderer {
        ImageStackTreeCellRenderer () {
        }

        @Override
        public Component getTreeCellRendererComponent
            (JTree tree, Object value, boolean sel, 
             boolean expanded, boolean leaf, int row, boolean hasFocus) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode)value;
            if (node.getParent() == null) { // root
                setClosedIcon (INBOXIMAGE_ICON);
                setOpenIcon (INBOXIMAGE_ICON);
                setLeafIcon (INBOXIMAGE_ICON);
            }
            else {
                ZPlane zplane = (ZPlane)node.getUserObject();
                if (zplane instanceof ZStack) {
                    setClosedIcon (IMAGESTACK_ICON);
                    setOpenIcon (IMAGESTACK_ICON);
                    setLeafIcon (IMAGESTACK_ICON);
                }
                else {
                    setClosedIcon (IMAGE_ICON);
                    setOpenIcon (IMAGE_ICON);
                    setLeafIcon (IMAGE_ICON);
                }
                setText (zplane.getName());
            }
            return super.getTreeCellRendererComponent
                (tree, value, sel, expanded, leaf, row, hasFocus);
        }
    }

    class ImageLoader extends SwingWorker<Integer, ZPlane> {
        DefaultMutableTreeNode parent;
        File[] files;
        volatile int progress;
        int pos;
        
        ImageLoader (DefaultMutableTreeNode parent, int pos, File[] files) {
            this.parent = parent;
            this.pos = pos;
            this.files = files;
            progressBar.setValue(0);
            progressBar.setStringPainted(true);
        }

        @Override
        protected Integer doInBackground () {
            ZStack zstack = (ZStack)parent.getUserObject();
            for (File f : files) {
                String name = f.getName();
                if (!zstack.contains(name)) {
                    try {
                        statusField.setText("Loading "+name+"...");
                        ZPlane zp = new ZPlane (f);
                        TIFFCodec.encode("zplane.tif", zp.getDisplay());

                        zstack.add(zp);
                        publish (zp);
                    }
                    catch (Exception ex) {
                        logger.log(Level.SEVERE, "Can't load file "+name);
                    }
                }
            }
            
            zstack.fuse();
            return progress;
        }

        @Override
        protected void process (java.util.List<ZPlane> chunk) {
            for (ZPlane zp : chunk) {
                DefaultMutableTreeNode node = 
                    new DefaultMutableTreeNode (zp, false);
                if (pos < 0) {
                    parent.add(node);
                }
                else {
                    parent.insert(node, ++pos);
                }
            }
            imageStackTree.reload();
            imageStackTree.expand(parent);

            progressBar.setValue((int)((double)progress
                                       /files.length * 100 + 0.5));
            ++progress;
        }

        @Override
        protected void done () {
            try {
                Integer c = get ();
                progressBar.setValue((int)((double)c
                                           /files.length * 100 + 0.5));

                JOptionPane.showMessageDialog
                    (ZExplorer.this, c+" file(s) loaded to image stack \""
                     +parent.getUserObject()+"\"!", 
                     "Info", JOptionPane.INFORMATION_MESSAGE);
            }
            catch (Exception ex) {
                JOptionPane.showMessageDialog
                    (ZExplorer.this, ex.getMessage(), 
                     "Error", JOptionPane.ERROR_MESSAGE);
            }

            statusField.setText(null);
            progressBar.setValue(0);
            progressBar.setStringPainted(false);
        }
    }

    class PropertiesTableModel extends AbstractTableModel {
        String[] columns = new String[] {
            "Name",
            "Value"
        };
        ArrayList rows = new ArrayList ();
        Map props = new TreeMap ();

        PropertiesTableModel () {}
        PropertiesTableModel (Map props) {
            setProperties (props);
        }

        void setProperties (Map props) {
            rows.clear();
            this.props.clear();
            if (props != null) {
                for (Object key : props.keySet()) {
                    rows.add(key);
                    this.props.put(key, props.get(key));
                }
            }
            fireTableDataChanged ();
        }

        void clear () {
            setProperties (null);
        }

        public String getColumnName (int col) {
            return columns[col];
        }
        public int getColumnCount () { return columns.length; }
        public int getRowCount () { return rows.size(); }
        public Object getValueAt (int row, int col) {
            Object key = rows.get(row);
            if (col == 0) return key;
            return props.get(key);
        }
    }

    JTextField statusField;
    JProgressBar progressBar;
    ZPanel zpanel;
    JFileChooser chooser;
    ImageStackTree imageStackTree;
    JSlider scaleSlider;
    JToolBar toolbar;
    JTable propsTable, polyTable;
    JButton addImageBtn, delImageBtn, delStackBtn;
    HistogramPlotPane hpp = new HistogramPlotPane ();

    public ZExplorer () {
        initUI ();
    }

    protected void initUI () {
        chooser = new JFileChooser (".");
        setJMenuBar (createMenuBar ());

        JSplitPane split = new JSplitPane (JSplitPane.HORIZONTAL_SPLIT);
	split.setDividerSize(5);
	split.setResizeWeight(.20);
        split.setDividerLocation(250);
	//split.setOneTouchExpandable(true);
        split.setLeftComponent(createNavPane ());
        split.setRightComponent(createContentPane ());

        JPanel pane = new JPanel (new BorderLayout (0, 0));
        pane.add(createToolBar (), BorderLayout.NORTH);
        pane.add(split);
        pane.add(createStatusPane (), BorderLayout.SOUTH);

        zpanel.scale(getScale ());
        setTitle ("NCGC ZExplorer");
        getContentPane().add(pane);
        pack ();

        setDefaultCloseOperation (EXIT_ON_CLOSE);
    }

    protected JMenuBar createMenuBar () {
	JMenuBar menubar = new JMenuBar ();
	JMenu menu;
	JMenuItem item;

	menubar.add(menu = new JMenu ("File"));
        //menu.add(item = new JMenuItem ("Open Folder"));
        //item.addActionListener(this);
        //menu.add(item = new JMenuItem ("Open Image"));
        //item.addActionListener(this);
        //menu.addSeparator();

        menu.add(item = new JMenuItem ("Quit"));
        item.addActionListener(this);

        return menubar;
    }

    protected JToolBar createToolBar () {
        JToolBar toolbar = new JToolBar ();
        JButton btn;

        toolbar.add(btn = createToolbarButton (INBOXPLUS_ICON));
        btn.setToolTipText("New image stack");
        btn.setActionCommand("NewImageStack");

        toolbar.add(btn = createToolbarButton (INBOXMINUS_ICON));
        btn.setToolTipText("Remove image stack");
        btn.setActionCommand("RemoveImageStack");
        btn.setEnabled(false);
        delStackBtn = btn;

        toolbar.addSeparator();

        toolbar.add(btn = createToolbarButton (PLUS_ICON));
        btn.setToolTipText("Add image to image stack");
        btn.setActionCommand("AddImage");
        btn.setEnabled(false);
        addImageBtn = btn;

        toolbar.add(btn = createToolbarButton (MINUS_ICON));
        btn.setToolTipText("Remove image from image stack");
        btn.setActionCommand("RemoveImage");
        btn.setEnabled(false);
        delImageBtn = btn;

        toolbar.addSeparator();
        toolbar.add(btn = createToolbarButton (UP_ICON));
        btn.setToolTipText("Move up");
        btn.setActionCommand("MoveUp");

        toolbar.add(btn = createToolbarButton (DOWN_ICON));
        btn.setToolTipText("Move down");
        btn.setActionCommand("MoveDown");

        toolbar.addSeparator();
        toolbar.add(btn = createToolbarButton (TASKSELECT_ICON));
        btn.setToolTipText("Annotation tools");
        btn.setActionCommand("AnnotationTool");

        JPopupMenu popup = new JPopupMenu ();
        JMenuItem item;
        ButtonGroup bg = new ButtonGroup ();
        popup.add(item = new JCheckBoxMenuItem ("Rectangle", SHAPE_ICON));
        item.setActionCommand("RectangleAnnotation");
        item.addActionListener(this);
        item.setSelected(true);
        bg.add(item);

        popup.add(item = new JCheckBoxMenuItem ("Line", LINE_ICON));
        item.setActionCommand("LineAnnotation");
        item.addActionListener(this);
        bg.add(item);
        btn.putClientProperty("PopupMenu", popup);

        popup.add(item = new JCheckBoxMenuItem ("Ellipse", ELLIPSE_ICON));
        item.setActionCommand("EllipseAnnotation");
        item.addActionListener(this);
        bg.add(item);
        btn.putClientProperty("PopupMenu", popup);

        popup.pack();

        /*
        toolbar.addSeparator();
        toolbar.add(btn = createToolbarButton (SORT_ICON));
        btn.setToolTipText("Sort images");
        btn.setActionCommand("SortImages");
        */

        return toolbar;
    }

    protected JButton createToolbarButton (Icon icon) {
        JButton btn = new JButton (icon);
        btn.setBorderPainted(false);
        btn.setContentAreaFilled(false);
        btn.setRolloverEnabled(true);
        btn.addActionListener(this);
        return btn;
    }

    protected Component createNavPane () {
        JSplitPane split = new JSplitPane (JSplitPane.VERTICAL_SPLIT);
        split.setDividerSize(5);
        split.setResizeWeight(.75);

        imageStackTree = new ImageStackTree ();
        split.setTopComponent(new JScrollPane (imageStackTree));
        split.setBottomComponent(createPropsPane ());

        JPanel pane = new JPanel (new BorderLayout ());
        pane.setBorder(BorderFactory.createEmptyBorder(1,1,1,1));
        pane.add(split);
        
        return pane;
    }

    protected void updatePolygon (ZPlane zplane, Shape polygon) {
        PropertiesTableModel model = 
            (PropertiesTableModel)polyTable.getModel();

        if (polygon == null) {
            model.clear();
            hpp.clear();

            return;
        }

        Map props = new TreeMap ();
        Rectangle r = polygon.getBounds();
        props.put("Bounds", String.format("["+r.x+","+r.y+","
                                          +r.width+","+r.height+"]"));
        if (polygon instanceof Line2D) {
            props.put("Length [um]", 0);
        }
        else {
            props.put("Area [um^2]", String.format
                      ("%1$.1f", zplane.getArea(polygon)));
            props.put("Perimeter [um]", String.format
                      ("%1$.1f", zplane.getPerimeter(polygon)));
        }
        model.setProperties(props);
    }

    protected Component createPropsPane () {        
        JTabbedPane tab = new JTabbedPane ();
        propsTable = new JTable (new PropertiesTableModel ());
        tab.addTab("Properties", new JScrollPane (propsTable));
        polyTable = new JTable (new PropertiesTableModel ());
        tab.addTab("Polygons", new JScrollPane (polyTable));
        tab.addTab("Histogram", hpp);

        JPanel pane = new JPanel (new BorderLayout (0, 0));
        pane.setBorder(BorderFactory.createEmptyBorder(1,1,1,1));
        pane.add(tab);

        return pane;
    }

    protected Component createStatusPane () {
	JPanel pane = new JPanel (new BorderLayout (5, 0));
	pane.setBorder(BorderFactory.createCompoundBorder
		       (BorderFactory.createEtchedBorder(),
			BorderFactory.createEmptyBorder(1,1,1,1)));
        
        scaleSlider = new JSlider (1, 100, 50);
	scaleSlider.addChangeListener(new ChangeListener () {
		public void stateChanged (ChangeEvent e) {
		    JSlider slider = (JSlider)e.getSource();
                    zpanel.scale(getScale ());
                    //logger.info("Scale "+getScale ());
		}
	    });
        JPanel p = new JPanel (new BorderLayout (0, 0));
        p.add(new JLabel (MAGNIFIER_ICON), BorderLayout.WEST);
        p.add(scaleSlider);
	pane.add(p, BorderLayout.WEST);

	statusField = new JTextField ();
	statusField.setEditable(false);
	pane.add(statusField);

	progressBar = new JProgressBar (0, 100);
	progressBar.setBorderPainted(true);
	progressBar.setStringPainted(false);
	pane.add(progressBar, BorderLayout.EAST);

	return pane;
    }

    protected double getScale () {
        double scale = (double)
            (scaleSlider.getValue()-scaleSlider.getMinimum())
            /(scaleSlider.getMaximum()-scaleSlider.getMinimum());
        return scale;
    }

    protected Component createContentPane () {
        JPanel pane = new JPanel (new BorderLayout ());
        pane.add(new JScrollPane (zpanel = new ZZPanel ()));
        return pane;
    }

    public void actionPerformed (ActionEvent e) {
        JComponent comp = (JComponent)e.getSource();
        String cmd = e.getActionCommand();
        if ("NewImageStack".equals(cmd)) {
            newImageStack ();
        }
        else if ("RemoveImageStack".equals(cmd)) {
            removeImageStack ();
        }
        else if ("AddImage".equals(cmd)) {
            addImage ();
        }
        else if ("RemoveImage".equals(cmd)) {
            imageStackTree.removeSelections();
        }
        else if ("SortImages".equals(cmd)) {
            sortImages ();
        }
        else if ("MoveUp".equals(cmd)) {
            moveUp ();
        }
        else if ("MoveDown".equals(cmd)) {
            moveDown ();
        }
        else if ("AnnotationTool".equals(cmd)) {
            JPopupMenu popup = (JPopupMenu)comp.getClientProperty("PopupMenu");
            popup.show(comp, 0, comp.getHeight());
        }
        else if ("LineAnnotation".equals(cmd)) {
            zpanel.setAnnotationTool(ZPanel.AnnotationTool.Line);
        }
        else if ("RectangleAnnotation".equals(cmd)) {
            zpanel.setAnnotationTool(ZPanel.AnnotationTool.Rectangle);
        }
        else if ("EllipseAnnotation".equals(cmd)) {
            zpanel.setAnnotationTool(ZPanel.AnnotationTool.Ellipse);
        }
        else if ("quit".equalsIgnoreCase(cmd)) {
            System.exit(0);
        }
    }

    protected void moveUp () {
        TreePath path = imageStackTree.getSelectionPath();
        if (path != null) {
            DefaultMutableTreeNode node = 
                (DefaultMutableTreeNode)path.getLastPathComponent();
            DefaultMutableTreeNode parent = 
                (DefaultMutableTreeNode)node.getParent();
            if (parent != null) {
                int pos = parent.getIndex(node);
                logger.info("node "+node+" position "+pos);
                if (pos > 0) {
                    parent.insert(node, pos -1);
                    imageStackTree.reload(parent);
                    imageStackTree.addSelectionPath
                        (new TreePath (node.getPath()));
                }
            }
        }
    }

    protected void moveDown () {
        TreePath path = imageStackTree.getSelectionPath();
        if (path != null) {
            DefaultMutableTreeNode node = 
                (DefaultMutableTreeNode)path.getLastPathComponent();
            DefaultMutableTreeNode parent = 
                (DefaultMutableTreeNode)node.getParent();
            if (parent != null) {
                int pos = parent.getIndex(node);
                logger.info("node "+node+" position "+pos);
                if (pos + 1 < parent.getChildCount()) {
                    parent.insert(node, pos+1);
                    imageStackTree.reload(parent);
                    imageStackTree.addSelectionPath
                        (new TreePath (node.getPath()));
                }
            }
        }
    }

    protected void show (DefaultMutableTreeNode node) {
        PropertiesTableModel model = 
            (PropertiesTableModel)propsTable.getModel();

        Object obj = node.getUserObject();
        if (obj instanceof ZPlane) {
            boolean isStack = obj instanceof ZStack;
            delStackBtn.setEnabled(isStack);
            addImageBtn.setEnabled(true);
            delImageBtn.setEnabled(!isStack);

            ZPlane zp = (ZPlane)obj;
            zpanel.setZPlane(zp);
            model.setProperties(zp.getParams());
        }
        else {
            addImageBtn.setEnabled(false);
            delImageBtn.setEnabled(false);
            delStackBtn.setEnabled(false);
            zpanel.setZPlane(null);
            hpp.clear();
            model.clear();
            statusField.setText(null);
        }
    }

    protected void sortImages () {
        
    }

    protected void newImageStack () {
        String name = JOptionPane.showInputDialog
            (this, "Please specify image stack name", "New Image Stack",
             JOptionPane.QUESTION_MESSAGE);
        if (name != null) {
            DefaultMutableTreeNode root = 
                (DefaultMutableTreeNode)imageStackTree.getRoot();
            for (Enumeration en = root.children(); en.hasMoreElements();) {
                DefaultMutableTreeNode node = 
                    (DefaultMutableTreeNode)en.nextElement();
                ZStack zstack = (ZStack)node.getUserObject();
                if (name.equals(zstack.getName())) {
                    JOptionPane.showMessageDialog
                        (this, "Image stack \""+name+"\" already exists!", 
                         "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
            
            DefaultMutableTreeNode node = 
                new DefaultMutableTreeNode (new ZStack (name));
            root.add(node);
            imageStackTree.reload();
        }
        logger.info("ImageStack: "+name);
    }

    protected void addImage () {
        TreePath path = imageStackTree.getSelectionPath();
        if (path != null) {
            DefaultMutableTreeNode node = 
                (DefaultMutableTreeNode)path.getLastPathComponent();
            Object obj = node.getUserObject();
            if (!(obj instanceof ZPlane))
                return;
            
            int pos = -1;
            if (!(obj instanceof ZStack)) { // add to parent
                DefaultMutableTreeNode child = node;
                node = (DefaultMutableTreeNode)node.getParent();
                pos = node.getIndex(child);
            }
            
            addImagesToStack (node, pos);
        }
    }

    protected void removeImageStack () {
        TreePath path = imageStackTree.getSelectionPath();
        if (path != null) {
            DefaultMutableTreeNode node = 
                (DefaultMutableTreeNode)path.getLastPathComponent();
            Object obj = node.getUserObject();
            if (obj instanceof ZStack) {
                int ans = JOptionPane.showConfirmDialog
                    (this, "Remove image stack \""+obj
                     +"\"?", "Confirmation", JOptionPane.YES_NO_OPTION);
                if (ans == JOptionPane.YES_OPTION) {
                    logger.info("## removing image stack \""+obj+"\"");
                    node.removeFromParent();
                    node.setUserObject(null);
                    imageStackTree.reload();
                }
            }
        }
    }

    protected void addImagesToStack (DefaultMutableTreeNode parent, int pos) {
        chooser.setDialogTitle("Load image(s)...");
        chooser.setFileSelectionMode
            (JFileChooser.FILES_AND_DIRECTORIES);
        chooser.setMultiSelectionEnabled(true);
        int ans = chooser.showOpenDialog(this);
        if (ans == JFileChooser.APPROVE_OPTION) {
            new ImageLoader (parent, pos, 
                             chooser.getSelectedFiles()).execute();
        }
    }

    public static void main (String[] argv) throws Exception {
        try {
            UIManager.setLookAndFeel(new Plastic3DLookAndFeel());
        } catch (Exception e) {}
	
        EventQueue.invokeLater(new Runnable () {
                public void run () {
                    ZExplorer xe = new ZExplorer();
                    xe.setSize(800, 600);
                    xe.setVisible(true);
                }
            });
    }
}
