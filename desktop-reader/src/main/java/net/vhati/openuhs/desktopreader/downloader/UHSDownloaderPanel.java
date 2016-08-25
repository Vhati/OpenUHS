package net.vhati.openuhs.desktopreader.downloader;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import java.util.*;

import net.vhati.openuhs.core.*;
import net.vhati.openuhs.desktopreader.*;
import net.vhati.openuhs.desktopreader.reader.UHSReaderPanel;


public class UHSDownloaderPanel extends JPanel implements ActionListener {
  public static Color EXISTS_COLOR = new Color(225, 225, 225);
  public static Color NEWER_COLOR = new Color(255, 255, 200);

  private UHSErrorHandler errorHandler = null;

  private UHSDownloaderPanel pronoun = this;
  private DownloadableUHSTableModel uhsTableModel = new DownloadableUHSTableModel(new String[] {"Title","Date","FullSize","Name"});
  private JTable uhsTable = null;
  private JScrollPane uhsTableScrollPane = null;

  private JButton reloadBtn = null;
  private JButton downloadBtn = null;
  private JTextField findField = null;
  private JButton findBtn = null;

  private UHSReaderPanel readerPanel = null;
  private MouseListener readerClickListener = null;

  private String hintsPath = ".";


  public UHSDownloaderPanel() {
    super(new BorderLayout());

    setErrorHandler(new DefaultUHSErrorHandler(System.err));

    JPanel ctrlPanel = new JPanel(new BorderLayout());
      JPanel ctrlLeftPanel = new JPanel();
        ctrlLeftPanel.setLayout(new BoxLayout(ctrlLeftPanel, BoxLayout.X_AXIS));
        reloadBtn = new JButton("Refresh");
          ctrlLeftPanel.add(reloadBtn);
        ctrlPanel.add(ctrlLeftPanel, BorderLayout.WEST);

      JPanel ctrlCenterPanel = new JPanel();
        ctrlCenterPanel.setLayout(new BoxLayout(ctrlCenterPanel, BoxLayout.Y_AXIS));
      JPanel ctrlCenterHolderPanel = new JPanel();
        ctrlCenterHolderPanel.setLayout(new BoxLayout(ctrlCenterHolderPanel, BoxLayout.X_AXIS));
        // ...
        ctrlCenterPanel.add(ctrlCenterHolderPanel);
      ctrlPanel.add(ctrlCenterPanel, BorderLayout.CENTER);

      JPanel ctrlRightPanel = new JPanel();
        ctrlRightPanel.setLayout(new BoxLayout(ctrlRightPanel, BoxLayout.X_AXIS));
        downloadBtn = new JButton("Download");
          downloadBtn.setEnabled(false);
          ctrlRightPanel.add(downloadBtn);
        ctrlPanel.add(ctrlRightPanel, BorderLayout.EAST);


    uhsTable = new JTable();
      uhsTable.setModel(uhsTableModel);
      try {
        uhsTable.setDefaultRenderer(Class.forName("java.lang.Object"), new UHSTableCellRenderer());
      }
      catch(ClassNotFoundException e) {
        if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, pronoun, "Could not set table renderer for download panel", 0, e);
      }
      uhsTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
      uhsTable.getTableHeader().setReorderingAllowed(false);
      uhsTable.getColumn("Title").setPreferredWidth(80);
      uhsTable.getColumn("Date").setMaxWidth(80);
      uhsTable.getColumn("Date").setPreferredWidth(70);
      uhsTable.getColumn("FullSize").setMaxWidth(75);
      uhsTable.getColumn("FullSize").setPreferredWidth(50);
      uhsTable.getColumn("Name").setMaxWidth(130);
      uhsTable.getColumn("Name").setPreferredWidth(100);
      uhsTableScrollPane = new JScrollPane(uhsTable);
        uhsTable.addNotify();


    JPanel infoPanel = new JPanel(new BorderLayout());
      JPanel infoCenterPanel = new JPanel();
        infoCenterPanel.setLayout(new BoxLayout(infoCenterPanel, BoxLayout.Y_AXIS));
        JLabel infoLbl = new JLabel("Gray - Exists  /  Gold - Updated");
          infoLbl.setAlignmentX(0.5f);
          infoCenterPanel.add(infoLbl);
        infoCenterPanel.add(new JSeparator(JSeparator.HORIZONTAL));
        infoCenterPanel.add(Box.createVerticalStrut(2));
        JPanel findPanel = new JPanel(new BorderLayout());
          findField = new JTextField();
            findPanel.add(findField, BorderLayout.CENTER);
          findBtn = new JButton("Find Next");
            findPanel.add(findBtn, BorderLayout.EAST);
          infoCenterPanel.add(findPanel);
      infoPanel.add(infoCenterPanel, BorderLayout.SOUTH);


    reloadBtn.addActionListener(this);
    downloadBtn.addActionListener(this);
    findBtn.addActionListener(this);

    Action findAction = new AbstractAction() {
      public void actionPerformed(ActionEvent e) {
        find(findField.getText());
      }
    };
    findPanel.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(KeyStroke.getKeyStroke("released ENTER"), "find");
    findPanel.getActionMap().put("find", findAction);

    uhsTable.getTableHeader().addMouseListener(new MouseAdapter() {
      public void mouseReleased(MouseEvent e) {
        int index = uhsTable.getColumnModel().getColumnIndexAtX(e.getX());
        int col = uhsTable.convertColumnIndexToModel(index);

        int order = DownloadableUHSTableModel.SORT_TITLE;
        if (uhsTableModel.getColumnName(col).equals("Title")) order = DownloadableUHSTableModel.SORT_TITLE;
        else if (uhsTableModel.getColumnName(col).equals("Date")) order = DownloadableUHSTableModel.SORT_DATE;
        else if (uhsTableModel.getColumnName(col).equals("FullSize")) order = DownloadableUHSTableModel.SORT_FULLSIZE;
        else if (uhsTableModel.getColumnName(col).equals("Name")) order = DownloadableUHSTableModel.SORT_NAME;
        uhsTableModel.sort(order);
      }
    });

    uhsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
      public void valueChanged(ListSelectionEvent e) {
        if (e.getValueIsAdjusting()) return;
        boolean state = false;
        if (uhsTable.getSelectedRow() != -1) state = true;
        downloadBtn.setEnabled(state);
      }
    });

    pronoun.add(ctrlPanel, BorderLayout.NORTH);
    pronoun.add(uhsTableScrollPane, BorderLayout.CENTER);
    pronoun.add(infoPanel, BorderLayout.SOUTH);
  }


  public void actionPerformed(ActionEvent e) {
    Object source = e.getSource();
    if (source == reloadBtn) {
      reloadTable();
    }
    else if (source == downloadBtn) {
      downloadHints();
    }
    else if (source == findBtn) {
      find(findField.getText());
    }
  }


  /**
   * Sets the error handler to notify of exceptions.
   * This is a convenience for logging/muting.
   * The default handler prints to System.err.
   *
   * @param eh the error handler, or null, for quiet parsing
   */
  public void setErrorHandler(UHSErrorHandler eh) {
    errorHandler = eh;
  }


  /**
   * Get the dir in which to look for UHS files.
   *
   * @return the path
   */
  public String getHintsPath() {return hintsPath;}

  /**
   * Set the dir in which to look for UHS files.
   * The path must exist and have no trailing slash.
   *
   * The default path is "."
   *
   * @param s the path
   */
  public void setHintsPath(String s) {
    java.io.File tmpFile = new java.io.File(s);
    if (tmpFile.exists() && tmpFile.isDirectory())
      hintsPath = s;
  }


  private void reloadTable() {
    ancestorSetNerfed(true);
    final Component parentComponent = getAncestorComponent();

    uhsTableModel.clear();

    Thread reloadWorker = new Thread() {
      public void run() {
        final ArrayList catalog = UHSFetcher.fetchCatalog(parentComponent);

        // Back to the event thread...
        Runnable r = new Runnable() {
          public void run() {
            for (int i=0; i < catalog.size(); i++) {
              uhsTableModel.addUHS( (DownloadableUHS)catalog.get(i) );
            }
            uhsTableModel.sort();
            colorizeTable();
            ancestorSetNerfed(false);
          }
        };
        EventQueue.invokeLater(r);
      }
    };

    reloadWorker.start();
  }

  private void colorizeTable() {
    uhsTable.clearSelection();

    String[] hintNames = new java.io.File(hintsPath).list();
    Arrays.sort(hintNames);

    for (int i=0; i < uhsTableModel.getRowCount(); i++) {
      DownloadableUHS tmpUHS = uhsTableModel.getUHS(i);

      if (Arrays.binarySearch(hintNames, tmpUHS.getName()) >= 0) {
        java.io.File tmpFile = new java.io.File( hintsPath +"/"+ tmpUHS.getName() );
        String tmpDate = new java.sql.Date(tmpFile.lastModified()).toString();
        if (tmpUHS.hasFixedDate() && tmpUHS.getDate().compareTo(tmpDate) > 0)
          tmpUHS.setColor(NEWER_COLOR);
        else
          tmpUHS.setColor(EXISTS_COLOR);
      }
    }
    uhsTable.repaint();
  }


  private void downloadHints() {
    if (uhsTableModel.getRowCount() == 0 || uhsTable.getSelectedRowCount() == 0) {
      // There's a JTable bug that misreports selection as 1 when empty
      return;
    }
    ancestorSetNerfed(true);
    final Component parentComponent = getAncestorComponent();

    int[] rows = uhsTable.getSelectedRows();
    final DownloadableUHS[] wants = new DownloadableUHS[rows.length];
    for (int i=0; i < rows.length; i++) {
      wants[i] = uhsTableModel.getUHS(rows[i]);
    }

    Thread downloadWorker = new Thread() {
      public void run() {
        for (int i=0; i < wants.length; i++) {
          DownloadableUHS tmpUHS = wants[i];
          byte[] bytes = UHSFetcher.fetchUHS(parentComponent, tmpUHS);
          if (bytes != null) {
            boolean success = UHSFetcher.saveBytes(parentComponent, hintsPath +"/"+ tmpUHS.getName(), bytes);
            if (errorHandler != null) {
              if (success) errorHandler.log(UHSErrorHandler.INFO, pronoun, "Saved "+ tmpUHS.getName(), 0, null);
              else errorHandler.log(UHSErrorHandler.ERROR, pronoun, "Could not save "+ tmpUHS.getName(), 0, null);
            }
          }
        }

        // Back to the event thread...
        Runnable r = new Runnable() {
          public void run() {
            colorizeTable();
            ancestorSetNerfed(false);
          }
        };
        EventQueue.invokeLater(r);
      }
    };

    downloadWorker.start();
  }


  private void find(String s) {
    if (s.length() == 0) return;
    String findString = s.toLowerCase();

    Rectangle viewRect = uhsTableScrollPane.getViewport().getViewRect();
    int firstRow = uhsTable.getSelectedRow();
    int rowCount = uhsTableModel.getRowCount();
    int foundRow = -1;

    if (firstRow >= 0) {
      for (int i=firstRow+1; i < rowCount; i++) {
        DownloadableUHS tmpUHS = uhsTableModel.getUHS(i);
        if (tmpUHS.getName().toLowerCase().indexOf(findString) != -1) {
          foundRow = i;
          break;
        }
      }
    }
    if (foundRow == -1) {
      for (int i=0; i < (firstRow >= 0 ? firstRow : rowCount); i++) {
        DownloadableUHS tmpUHS = uhsTableModel.getUHS(i);
        if (tmpUHS.getTitle().toLowerCase().indexOf(findString) != -1) {
          foundRow = i;
          break;
        }
      }
    }
    if (foundRow != -1) {
      uhsTable.scrollRectToVisible(new Rectangle(uhsTable.getCellRect(foundRow, 0, true)));
      uhsTable.setRowSelectionInterval(foundRow, foundRow);
    }
  }


  /**
   * Get the table displaying the catalog.
   * This is so parent containers can add listeners to the downloader's GUI.
   *
   * @return the table
   */
  public JTable getUHSTable() {return uhsTable;}


  public AppliablePanel getSettingsPanel() {
    AppliablePanel result = new AppliablePanel(new BorderLayout());
      JPanel optionsPanel = new JPanel(new BorderLayout());
        JPanel optionsCenterPanel = new JPanel();
          optionsCenterPanel.setLayout(new BoxLayout(optionsCenterPanel, BoxLayout.Y_AXIS));
          optionsCenterPanel.setBorder(BorderFactory.createTitledBorder("Proxy"));
          JPanel httpPanel = new JPanel(new BorderLayout());
            final JCheckBox httpBox = new JCheckBox("http", false);
              httpPanel.add(httpBox, BorderLayout.WEST);
            JPanel httpHostPanel = new JPanel();
              httpHostPanel.setLayout(new BoxLayout(httpHostPanel, BoxLayout.X_AXIS));
              httpHostPanel.add(Box.createHorizontalStrut(2));
              httpHostPanel.add(new JLabel("Host"));
              final JTextField httpHostField = new JTextField("255.255.255.255 ");
                httpHostField.setMaximumSize(httpHostField.getPreferredSize());
                httpHostField.setMinimumSize(httpHostField.getPreferredSize());
                httpHostField.setPreferredSize(httpHostField.getPreferredSize());
                httpHostField.setText("");
                httpHostField.setEnabled(false);
                httpHostPanel.add(httpHostField);
              httpHostPanel.add(new JLabel(" Port"));
              final JTextField httpPortField = new JTextField("65536 ");
                httpPortField.setMaximumSize(httpPortField.getPreferredSize());
                httpPortField.setMinimumSize(httpPortField.getPreferredSize());
                httpPortField.setPreferredSize(httpPortField.getPreferredSize());
                httpPortField.setText("");
                httpPortField.setEnabled(false);
                httpHostPanel.add(httpPortField);
              httpHostPanel.add(Box.createHorizontalStrut(2));
              httpPanel.add(httpHostPanel, BorderLayout.EAST);
              optionsCenterPanel.add(httpPanel);
          JPanel socksPanel = new JPanel(new BorderLayout());
            final JCheckBox socksBox = new JCheckBox("socks", false);
              socksPanel.add(socksBox, BorderLayout.WEST);
            JPanel socksHostPanel = new JPanel();
              socksHostPanel.setLayout(new BoxLayout(socksHostPanel, BoxLayout.X_AXIS));
              socksHostPanel.add(Box.createHorizontalStrut(2));
              socksHostPanel.add(new JLabel("Host"));
              final JTextField socksHostField = new JTextField("255.255.255.255 ");
                socksHostField.setMaximumSize(socksHostField.getPreferredSize());
                socksHostField.setMinimumSize(socksHostField.getPreferredSize());
                socksHostField.setPreferredSize(socksHostField.getPreferredSize());
                socksHostField.setText("");
                socksHostField.setEnabled(false);
                socksHostPanel.add(socksHostField);
              socksHostPanel.add(new JLabel(" Port"));
              final JTextField socksPortField = new JTextField("65536 ");
                socksPortField.setMaximumSize(socksPortField.getPreferredSize());
                socksPortField.setMinimumSize(socksPortField.getPreferredSize());
                socksPortField.setPreferredSize(socksPortField.getPreferredSize());
                socksPortField.setText("");
                socksPortField.setEnabled(false);
                socksHostPanel.add(socksPortField);
              socksHostPanel.add(Box.createHorizontalStrut(2));
              socksPanel.add(socksHostPanel, BorderLayout.EAST);
              optionsCenterPanel.add(socksPanel);
          optionsPanel.add(optionsCenterPanel, BorderLayout.CENTER);
        result.add(optionsPanel, BorderLayout.NORTH);

    ActionListener settingsListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        Object source = e.getSource();
        if (source == httpBox) {
          boolean state = httpBox.isSelected();
          httpHostField.setEnabled(state);
          httpPortField.setEnabled(state);
        }
        else if (source == socksBox) {
          boolean state = socksBox.isSelected();
          socksHostField.setEnabled(state);
          socksPortField.setEnabled(state);
        }
      }
    };
    httpBox.addActionListener(settingsListener);
    socksBox.addActionListener(settingsListener);

    Properties prop = System.getProperties();
    String httpHost = prop.getProperty("http.proxyHost");
    String httpPort = prop.getProperty("http.proxyPort");
    if (httpHost != null) httpHostField.setText(httpHost);
    if (httpPort != null) httpPortField.setText(httpPort);
    if (httpHost != null && httpPort != null) {
      if (httpHost.length() > 0 && httpPort.length() > 0) {
        httpBox.doClick();
      }
    }
    String socksHost = prop.getProperty("socks.proxyHost");
    String socksPort = prop.getProperty("socks.proxyPort");
    if (socksHost != null) socksHostField.setText(socksHost);
    if (socksPort != null) socksPortField.setText(socksPort);
    if (socksHost != null && socksPort != null) {
      if (socksHost.length() > 0 && socksPort.length() > 0) {
        socksBox.doClick();
      }
    }

    Runnable applyAction = new Runnable() {
      public void run() {
        Properties prop = System.getProperties();
        String httpHost = httpHostField.getText();
        String httpPort = httpPortField.getText();
        if (httpBox.isSelected() && httpHost.length() > 0 && httpPort.length() > 0) {
          prop.setProperty("http.proxyHost", httpHost);
          prop.setProperty("http.proxyPort", httpPort);
        } else {
          prop.setProperty("http.proxyHost", "");
          prop.setProperty("http.proxyPort", "");
        }
        String socksHost = socksHostField.getText();
        String socksPort = socksPortField.getText();
        if (socksBox.isSelected() && socksHost.length() > 0 && socksPort.length() > 0) {
          prop.setProperty("socks.proxyHost", socksHost);
          prop.setProperty("socks.proxyPort", socksPort);
        } else {
          prop.setProperty("socks.proxyHost", "");
          prop.setProperty("socks.proxyPort", "");
        }
      }
    };
    result.setApplyAction(applyAction);

    return result;
  }


  /**
   * Calls setNerfed on the top-level ancestor, if nerfable.
   * A dedicated method was easier than passing the ancestor to runnables.
   */
  private void ancestorSetNerfed(boolean b) {
    boolean nerfable = false;
    Component ancestorComponent = getAncestorComponent();
    if (ancestorComponent != null) {
      if (ancestorComponent instanceof Nerfable) nerfable = true;
    }

    if (nerfable) ((Nerfable)ancestorComponent).setNerfed(b);
  }

  /**
   * Returns the top level ancestor, cast as a Component.
   * Otherwise returns null.
   */
  private Component getAncestorComponent() {
    Component ancestorComponent = null;
    Object ancestor = pronoun.getTopLevelAncestor();
    if (ancestor != null) {
      if (ancestor instanceof Component) ancestorComponent = (Component)ancestor;
    }
    return ancestorComponent;
  }
}
