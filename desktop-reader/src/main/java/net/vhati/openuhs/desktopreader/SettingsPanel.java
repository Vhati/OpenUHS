package net.vhati.openuhs.desktopreader;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import java.util.ArrayList;

import net.vhati.openuhs.core.*;
import net.vhati.openuhs.desktopreader.*;
import net.vhati.openuhs.desktopreader.downloader.*;
import net.vhati.openuhs.desktopreader.reader.*;


public class SettingsPanel extends JPanel implements ActionListener {
  private ArrayList appliablePanels = new ArrayList();
  private JPanel sectionsPanel = null;
  GridBagConstraints sectionC = null;
  private JButton applyBtn = null;


  public SettingsPanel() {
    super(new BorderLayout());

    JPanel paddingPanel = new JPanel(new BorderLayout());
      sectionsPanel = new JPanel(new GridBagLayout());
        sectionC = new GridBagConstraints();
          sectionC.fill = GridBagConstraints.HORIZONTAL;
          sectionC.weightx = 1.0;
          sectionC.weighty = 0;
          sectionC.insets = new Insets(0, 0, 8, 0);
          sectionC.gridwidth = GridBagConstraints.REMAINDER;  //End Row
          sectionC.gridy = 1;
          paddingPanel.add(sectionsPanel, BorderLayout.NORTH);
      JScrollPane scrollPane = new JScrollPane(paddingPanel, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        this.add(scrollPane, BorderLayout.CENTER);

    JPanel applyPanel = new JPanel();
      applyPanel.setLayout(new BoxLayout(applyPanel, BoxLayout.X_AXIS));
      applyPanel.add(Box.createHorizontalGlue());
      applyBtn = new JButton("Apply");
        applyPanel.add(applyBtn);
      applyPanel.add(Box.createHorizontalGlue());
      this.add(applyPanel, BorderLayout.SOUTH);

    applyBtn.addActionListener(this);
  }


  public void actionPerformed(ActionEvent e) {
    Object source = e.getSource();
    if (source == applyBtn) {
      for (int i=0; i < appliablePanels.size(); i++) {
        ((AppliablePanel)appliablePanels.get(i)).apply();
      }
    }
  }


  public void clear() {
    appliablePanels.clear();
    sectionsPanel.removeAll();
    sectionC.gridy = 1;
    this.validate();
    this.repaint();
  }


  public void addSection(String sectionName, AppliablePanel sectionPanel) {
    appliablePanels.add(sectionPanel);

    JPanel tmpPanel = new JPanel(new BorderLayout());
      tmpPanel.setBorder(BorderFactory.createTitledBorder(sectionName));
      tmpPanel.add(sectionPanel);
      sectionsPanel.add(tmpPanel, sectionC);
    sectionC.gridy++;

    this.validate();
    this.repaint();
  }
}
