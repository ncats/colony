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

import com.jgoodies.looks.plastic.Plastic3DLookAndFeel;
import com.jidesoft.swing.JideTabbedPane;

import static tripod.colony.ColonyImagePane.*;

public class NucleiViewer extends JFrame implements ActionListener {
    static final Logger logger = Logger.getLogger(NucleiViewer.class.getName());
    static final String LOAD_IMAGES = "Load images";
    static final String SAVE_IMAGE = "Save image";
    static final String SHOW_MASKS = "Show masks";
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
    
    File maskFile;
    File imageFile;
    JButton saveBtn;

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
                        expandDescendants (node);
                    }
                }
            });
        fileTree.setRootVisible(false);
        
        JSplitPane hsplit = new JSplitPane (JSplitPane.HORIZONTAL_SPLIT);
        hsplit.setLeftComponent(new JScrollPane (fileTree));
        hsplit.setRightComponent(new JScrollPane (zpane));

        JPanel pane = new JPanel (new BorderLayout (0, 0));
        pane.add(toolbar, BorderLayout.NORTH);
        pane.add(hsplit);        

        getContentPane().add(pane);
        pack();
        setSize(800, 600);
        setVisible(true);
    }

    void expandDescendants (MutableTreeNode node) {
        LinkedList<TreeNode> path = new LinkedList<>();
        for (TreeNode p = node.getParent(); p != null; p = p.getParent())
            path.add(p);
        expandDescendants (path, node);
    }

    void expandDescendants (LinkedList<TreeNode> path, TreeNode node) {
        path.push(node);
        for (Enumeration en = node.children(); en.hasMoreElements();) {
            TreeNode n = (TreeNode) en.nextElement();
            expandDescendants (path, n);
        }
        path.pop();

        if (!path.isEmpty()) {
            ArrayList<TreeNode> p = new ArrayList<>(path);
            Collections.reverse(p);
            //logger.info("expanding path..."+p);
            fileTree.expandPath(new TreePath (p.toArray(new TreeNode[0])));
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
            CodecRunLength crl = new CodecRunLength
                (zpane.getColony().getBitmap());
            for (CodecRunLength.Run[] r : crl.encode()) {
                for (int i = 0; i < r.length; ++i)
                    logger.info(r[i].index()+" "+r[i].len());
                logger.info("--");
            }
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
