package net.vhati.openuhs.desktopreader;

import java.awt.*;
import javax.swing.*;


public class AppliablePanel extends JPanel {
  private Runnable applyAction = null;


  public AppliablePanel() {super();}
  public AppliablePanel(LayoutManager layout) {super(layout);}


  public Runnable getApplyAction() {return applyAction;}
  public void setApplyAction(Runnable r) {applyAction = r;}

  public void apply() {
    if (applyAction != null) applyAction.run();
  }
}

