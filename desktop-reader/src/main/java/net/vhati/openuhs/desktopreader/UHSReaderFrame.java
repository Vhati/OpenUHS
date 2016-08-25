package net.vhati.openuhs.desktopreader;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import net.vhati.openuhs.desktopreader.Nerfable;
import net.vhati.openuhs.desktopreader.SettingsPanel;
import net.vhati.openuhs.desktopreader.downloader.DownloadableUHS;
import net.vhati.openuhs.desktopreader.downloader.DownloadableUHSTableModel;
import net.vhati.openuhs.desktopreader.downloader.UHSDownloaderPanel;
import net.vhati.openuhs.desktopreader.reader.UHSReaderPanel;


public class UHSReaderFrame extends JFrame implements Nerfable {
  private static String HINTS_PATH = "./hints";

  private UHSReaderFrame pronoun = this;

  private String titlePrefix = "";
  private UHSReaderPanel readerPanel = new UHSReaderPanel();
  private UHSDownloaderPanel downloaderPanel = new UHSDownloaderPanel();
  private SettingsPanel settingsPanel = new SettingsPanel();


  public UHSReaderFrame() {
    super();
    JPanel pane = new JPanel(new BorderLayout());

    final JTabbedPane tabbedPane = new JTabbedPane();
      readerPanel.setHintsPath(HINTS_PATH);
      tabbedPane.add(readerPanel, "Reader");

      downloaderPanel.setHintsPath(HINTS_PATH);
      tabbedPane.add(downloaderPanel, "Downloader");

      tabbedPane.add(settingsPanel, "Settings");

    readerPanel.addAncestorListener(new AncestorListener() {
      public void ancestorAdded(AncestorEvent event) {pronoun.setTitle(readerPanel.getReaderTitle());}
      public void ancestorMoved(AncestorEvent event) {}
      public void ancestorRemoved(AncestorEvent event) {pronoun.setTitle(null);}
    });

    downloaderPanel.getUHSTable().addMouseListener(new MouseAdapter() {
      public void mouseClicked(MouseEvent e) {
        JTable tmpTable = (JTable)e.getSource();
        if (tmpTable.getRowCount() == 0) return;

        if (e.getClickCount() == 2) {
          int row = tmpTable.getSelectedRow();
          if (row == -1) return;
          DownloadableUHS tmpUHS = ((DownloadableUHSTableModel)tmpTable.getModel()).getUHS(row);
          java.io.File tmpFile = new java.io.File( HINTS_PATH +"/"+ tmpUHS.getName() );
          if (tmpFile.exists()) {
            tabbedPane.setSelectedIndex(tabbedPane.indexOfTab("Reader"));
            readerPanel.openFile(tmpFile.getPath());
          }
        }
      }
    });

    settingsPanel.addAncestorListener(new AncestorListener() {
      public void ancestorAdded(AncestorEvent event) {
        settingsPanel.clear();
        settingsPanel.addSection("Reader", readerPanel.getSettingsPanel());
        settingsPanel.addSection("Downloader", downloaderPanel.getSettingsPanel());
      }
      public void ancestorMoved(AncestorEvent event) {}
      public void ancestorRemoved(AncestorEvent event) {
        settingsPanel.clear();
      }
    });

    //Prep the glasspane to nerf this window later
    this.getGlassPane().setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    this.getGlassPane().setFocusTraversalKeysEnabled(false);
    this.getGlassPane().addMouseListener(new MouseAdapter() {
      public void mousePressed(MouseEvent e) {e.consume();}
      public void mouseReleased(MouseEvent e) {e.consume();}
    });
    this.getGlassPane().addKeyListener(new KeyListener() {
      public void keyPressed(KeyEvent e) {e.consume();}
      public void keyReleased(KeyEvent e) {e.consume();}
      public void keyTyped(KeyEvent e) {e.consume();}
    });

    pane.add(tabbedPane, BorderLayout.CENTER);
    this.setContentPane(pane);
  }


  public UHSReaderPanel getUHSReaderPanel() {return readerPanel;}

  public UHSDownloaderPanel getUHSDownloaderPanel() {return downloaderPanel;}

  public SettingsPanel getSettingsPanel() {return settingsPanel;}


  public void setTitlePrefix(String s) {
    if (s == null || s.length() == 0) titlePrefix = "";
    else titlePrefix = s;
  }

  public void setTitle(String title) {
    if (title == null || title.length() == 0) super.setTitle(titlePrefix);
    else super.setTitle(titlePrefix +"-"+ title);
  }


  public void setNerfed(boolean b) {
    //button mnemonics will still work
    Component glassPane = this.getGlassPane();
    if (b) {
      glassPane.setVisible(true);
      glassPane.requestFocusInWindow();
    } else {
      glassPane.setVisible(false);
    }
  }
}
