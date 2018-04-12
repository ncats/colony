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
import javax.swing.filechooser.*;
import javax.swing.border.*;

import com.jgoodies.looks.plastic.Plastic3DLookAndFeel;
import com.jidesoft.swing.JideTabbedPane;

import static tripod.colony.NucleiSegmentation.*;
import static tripod.colony.ColonyImagePane.*;

public class NucleiViewer extends JFrame implements ActionListener {
    static final Logger logger = Logger.getLogger(NucleiViewer.class.getName());
    static final String LOAD_IMAGES = "Load images";
    static final String SAVE_IMAGE = "Save image";
    static final String SEARCH = "Search";
    static final String SHOW_MASKS = "Show masks";
    static final String SHOW_SEGMENTS = "Show segments";
    static final String MASK_FILE = "Load masks";

    static class FileNode extends DefaultMutableTreeNode {
        FileNode (File f) {
            super (f);
            if (f == null)
                throw new IllegalArgumentException ("File is null!");
        }
        File getFile () { return (File)getUserObject(); }
        public String toString () {
            String name = ((File)getUserObject()).getName();
            if (name.length() > 30) {
                name = name.substring(0,10)+"..."
                    +name.substring(name.length()-10);
            }
            return name;
        }
    }
    
    final ColonyImagePane zpane;
    final JFileChooser fileChooser;
    final JLabel maskLabel;
    final JTree fileTree;
    final JTree segTree;
    final JSpinner thresholdSpinner;
    final JCheckBox segmentCb;
    
    File maskFile;
    File imageFile;
    JButton saveBtn;
    NucleiSegmentation nuseg;
    Segment segment;
    
    public NucleiViewer () {
        JToolBar toolbar = new JToolBar ();

        JButton open = new JButton (LOAD_IMAGES);
        open.addActionListener(this);
        open.setToolTipText("Load images");
        toolbar.add(open);
        toolbar.addSeparator();

        saveBtn = new JButton (SAVE_IMAGE);
        saveBtn.setEnabled(false);
        saveBtn.addActionListener(this);
        saveBtn.setToolTipText("Save image");
        toolbar.add(saveBtn);
        toolbar.addSeparator();

        SpinnerNumberModel model = new SpinnerNumberModel (1.0, 0.2, 3.0, 0.2);
        model.addChangeListener(new ChangeListener () {
                public void stateChanged (ChangeEvent e) {
                    SpinnerNumberModel model =
                        (SpinnerNumberModel)e.getSource();
                    Number n = (Number)model.getValue();
                    zpane.setScale(n.doubleValue());
                }
            });
        JSpinner scale = new JSpinner (model);
        scale.setMaximumSize(new Dimension (100, 20));
        scale.setToolTipText("Adjust image magnification");
        toolbar.add(scale);
        toolbar.addSeparator();

        model = new SpinnerNumberModel (128, 1, 255, 1);
        model.addChangeListener(new ChangeListener () {
                public void stateChanged (ChangeEvent e) {
                    SpinnerNumberModel model =
                        (SpinnerNumberModel)e.getSource();
                    Number n = model.getNumber();
                    zpane.setThreshold(n.intValue());
                }
            });
        thresholdSpinner = new JSpinner (model);
        thresholdSpinner.setMaximumSize(new Dimension (100, 20));
        thresholdSpinner.setToolTipText("Set threshold");
        toolbar.add(thresholdSpinner);
        toolbar.addSeparator();
        
        segmentCb = new JCheckBox (SHOW_SEGMENTS);
        segmentCb.setToolTipText("Toggle segments");
        segmentCb.setSelected(true);
        segmentCb.addActionListener(this);
        toolbar.add(segmentCb);
        toolbar.addSeparator();

        JCheckBox cb = new JCheckBox (SHOW_MASKS);
        cb.setToolTipText("Toggle mask/ground truth overlay");
        cb.setSelected(true);
        cb.addActionListener(this);
        toolbar.add(cb);

        JButton mask = new JButton (MASK_FILE);
        mask.setToolTipText("Load mask/ground truth file");
        mask.addActionListener(this);
        toolbar.add(mask);
        toolbar.add(maskLabel = new JLabel ());
        
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        zpane = new ColonyImagePane ();

        fileChooser = new JFileChooser (".");
        fileTree = new JTree (new DefaultTreeModel (null));
        fileTree.setExpandsSelectedPaths(true);
        fileTree.addTreeSelectionListener(new TreeSelectionListener () {
                public void valueChanged (TreeSelectionEvent e) {
                    FileNode node = (FileNode)e.getPath()
                        .getLastPathComponent();
                    File file = node.getFile();
                    if (file.isFile()) {
                        logger.info("selected..."+file);
                        loadFile (file);
                    }
                    else {
                        expandDescendants (fileTree, node);
                    }
                }
            });
        fileTree.setRootVisible(false);
        
        JSplitPane hsplit = new JSplitPane (JSplitPane.HORIZONTAL_SPLIT);
        hsplit.setLeftComponent(new JScrollPane (fileTree));
        nuseg = new NucleiSegmentation ();
        segTree = new JTree (nuseg);
        segTree.setExpandsSelectedPaths(true);
        segTree.addTreeSelectionListener(new TreeSelectionListener () {
                public void valueChanged (TreeSelectionEvent e) {
                    Segment seg = (Segment) e.getPath().getLastPathComponent();
                    showSegment (seg);
                }
            });
        hsplit.setBorder(new EmptyBorder (0, 1, 0, 0));

        JSplitPane hs = new JSplitPane (JSplitPane.HORIZONTAL_SPLIT);
        hs.setBorder(new EmptyBorder (0, 0, 0, 1));
        
        JPanel p0 = new JPanel (new BorderLayout (0, 1));

        
        Box p1 = Box.createHorizontalBox();

        final JTextField xtf = new JTextField (5);
        final JTextField ytf = new JTextField (5);        
        ActionListener action = (ActionEvent e) -> {
            Double x = null, y = null;
            try {
                x = Double.parseDouble(xtf.getText());
            }
            catch (NumberFormatException ex) {
            }
            
            try {
                y = Double.parseDouble(ytf.getText());
            }
            catch (NumberFormatException ex) {
            }
            
            if (x != null && y != null) {
                searchSegments (x, y);
            } 
        };
        p1.add(new JLabel ("X:"));
        p1.add(xtf);
        xtf.addActionListener(action);
        p1.add(Box.createHorizontalStrut(5));
        p1.add(new JLabel ("Y:"));
        ytf.addActionListener(action);
                             
        p1.add(ytf);
        JButton searchBtn = new JButton (SEARCH);
        searchBtn.addActionListener(action);
        searchBtn.setToolTipText("Search segment/mask");
        p1.add(searchBtn);
        JButton clearBtn = new JButton ("Clear");
        clearBtn.addActionListener(new ActionListener () {
                public void actionPerformed (ActionEvent e) {
                    clearSegments ();
                }
            });
        p1.add(clearBtn);
               
        p0.add(p1, BorderLayout.NORTH);
        p0.add(new JScrollPane (segTree));
        hs.setLeftComponent(p0);
        hs.setRightComponent(new JScrollPane (zpane));
        hsplit.setRightComponent(hs);
        
        JPanel pane = new JPanel (new BorderLayout (0, 0));
        pane.add(toolbar, BorderLayout.NORTH);
        pane.add(hsplit);        

        getContentPane().add(pane);
        pack();
        setSize(800, 600);
        setVisible(true);
    }

    void showSegment (Segment seg) {
        //seg.print(System.err);
        if (segmentCb.isSelected())
            zpane.setSegment(seg);

        if (seg.bitmap != null) {
            try {
                String name = "seg."+seg.depth+"."+seg.threshold();        
                seg.bitmap.writetif(name+".tif");
                seg.bitmap.thin().write("png", new File (name+".thin.png"));

                NucleiSegmentation.debugSegment(seg, 30.0);
            }
            catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        
        thresholdSpinner.setValue(seg.threshold());
        expandDescendants (segTree, seg);
        segment = seg;
    }

    void searchSegments (double x, double y) {
        segTree.setModel(nuseg.filter(x, y));
    }

    void clearSegments () {
        segTree.setModel(nuseg);
    }
    
    void expandDescendants (JTree tree, TreeNode node) {
        LinkedList<TreeNode> path = new LinkedList<>();
        for (TreeNode p = node.getParent(); p != null; p = p.getParent())
            path.add(p);
        expandDescendants (tree, path, node);
    }

    void expandDescendants (JTree tree,
                            LinkedList<TreeNode> path, TreeNode node) {
        path.push(node);
        for (Enumeration en = node.children(); en.hasMoreElements();) {
            TreeNode n = (TreeNode) en.nextElement();
            expandDescendants (tree, path, n);
        }
        path.pop();

        if (!path.isEmpty()) {
            ArrayList<TreeNode> p = new ArrayList<>(path);
            Collections.reverse(p);
            //logger.info("expanding path..."+p);
            tree.expandPath(new TreePath (p.toArray(new TreeNode[0])));
        }
    }

    void showDialog (Throwable t) {
        JOptionPane.showMessageDialog
            (this, "Exception  "+t.getMessage(), "Error",
             JOptionPane.ERROR_MESSAGE);
    }

    void loadFile (File file) {
        try {
            String title = zpane.load(file, maskFile);
            imageFile = file;
            setTitle (title);
            saveBtn.setEnabled(true);
            ((SpinnerNumberModel)thresholdSpinner.getModel())
                .setValue(zpane.getThreshold());

            nuseg.segment(zpane.getRaster());
            segTree.setModel(nuseg);

            // clear out any previous segment selected
            zpane.setSegment(null);
            
            /*
            RLE crl = new RLE (zpane.getColony().getBitmap());
            for (RLE.Run[] r : crl.encode()) {
                for (int i = 0; i < r.length; ++i)
                    logger.info(r[i].index()+" "+r[i].len());
                logger.info("--");
            }
            */
        }
        catch (IOException ex) {
            saveBtn.setEnabled(false);
            showDialog (ex);
        }
    }
    
    void load (DefaultMutableTreeNode node, File[] files) {
        for (File f : files) {
            logger.info("..."+f.getName());
            FileNode n = new FileNode (f);
            node.add(n);            
            if (f.isDirectory()) {
                load (n, f.listFiles(new java.io.FileFilter () {
                        public boolean accept (File file) {
                            String name = file.getName();
                            return file.isDirectory()
                                || name.toLowerCase().endsWith(".png")
                                || name.toLowerCase().endsWith(".gif")
                                || name.toLowerCase().endsWith(".tif");
                        }
                    }));
            }
        }
    }

    public void actionPerformed (ActionEvent e) {
        Object source = e.getSource();
        if (source instanceof AbstractButton) {
            AbstractButton ab = (AbstractButton)source;
            String cmd = ab.getActionCommand();
            switch (cmd) {
            case LOAD_IMAGES:
                loadImages ();
                break;

            case SAVE_IMAGE:
                saveImage ();
                break;

            case MASK_FILE:
                loadMaskFile ();
                break;
                
            case SHOW_MASKS:
                { JCheckBox cb = (JCheckBox)source;
                    if (cb.isSelected())
                        zpane.show(FLAG_MASKS);
                    else
                        zpane.hide(FLAG_MASKS);
                }
                break;

            case SHOW_SEGMENTS:
                { JCheckBox cb = (JCheckBox)source;
                    zpane.setSegment(cb.isSelected() ? segment: null);
                }
            }
        }
    }

    void loadImages () {
        FileNameExtensionFilter filter =
            new FileNameExtensionFilter ("Open image format",
                                         "jpg", "gif", "png");
        fileChooser.setFileFilter(filter);
        fileChooser.setMultiSelectionEnabled(true);
        fileChooser.setFileSelectionMode
            (JFileChooser.FILES_AND_DIRECTORIES);
        int opt = fileChooser.showOpenDialog(this);
        if (opt == JFileChooser.APPROVE_OPTION) {
            File[] files = fileChooser.getSelectedFiles();
            DefaultMutableTreeNode root =
                new DefaultMutableTreeNode (null);
            load (root, files);
            fileTree.setModel(new DefaultTreeModel (root));
        }
    }

    void saveImage () {
        FileNameExtensionFilter filter =
            new FileNameExtensionFilter ("Save image format", "png");
        fileChooser.setFileFilter(filter);
        fileChooser.setMultiSelectionEnabled(false);
        int opt = fileChooser.showSaveDialog(this);
        if (opt == JFileChooser.APPROVE_OPTION) {
            File file = fileChooser.getSelectedFile();
            if (file.exists()) {
                int ans = JOptionPane.showConfirmDialog
                    (this, "File "+file.getName()
                     +" already exist; really override?", "Warning",
                     JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (ans != JOptionPane.YES_OPTION)
                    return;
            }
            logger.info("saving image to "+file.getName()+"...");
            try {
                zpane.save(file);
            }
            catch (IOException ex) {
                showDialog (ex);
            }
        }
    }

    void loadMaskFile () {
        FileNameExtensionFilter filter =
            new FileNameExtensionFilter ("CSV file", "csv");
        fileChooser.setFileFilter(filter);
        fileChooser.setMultiSelectionEnabled(false);
        fileChooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
        
        int opt = fileChooser.showOpenDialog(this);
        if (opt == JFileChooser.APPROVE_OPTION) {
            maskFile = fileChooser.getSelectedFile();
            maskLabel.setText(maskFile.getName());
            if (imageFile != null) {
                String name = imageFile.getName();
                opt = name.lastIndexOf('.');
                if (opt > 0) {
                    name = name.substring(0, opt);
                }
                try {
                    zpane.loadMasks(name, maskFile);
                }
                catch (IOException ex) {
                    showDialog (ex);
                }
            }
        }
    }

    public static void main (String[] argv) throws Exception {
        SwingUtilities.invokeLater(new Runnable () {
                public void run () {
                    try {
                        new NucleiViewer();
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            });
    }
}
