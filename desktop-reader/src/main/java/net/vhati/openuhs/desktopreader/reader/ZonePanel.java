package net.vhati.openuhs.desktopreader.reader;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.swing.JComponent;


/**
 * A panel representing a single zone in a hotspot node.
 */
public class ZonePanel extends JComponent {
  private boolean showContents = false;
  private JComponent component = null;
  private ZonePanel zoneTarget = null;
  private int linkTarget = -1;


  public ZonePanel() {
    super();
  }

  public ZonePanel(JComponent c) {
    super();
    component = c;
    c.setBounds(0, 0, c.getPreferredSize().width, c.getPreferredSize().height);
  }

  public Dimension getPreferredSize() {
    if (component == null) return super.getPreferredSize();
    else return component.getPreferredSize();
  }
  public void setPreferredSize(Dimension d) {
    if (component == null) super.setPreferredSize(d);
    else component.setPreferredSize(d);
  }
  public Dimension getMinimumSize() {
    if (component == null) return super.getMinimumSize();
    else return component.getMinimumSize();
  }
  public void setMinimumSize(Dimension d) {
    if (component == null) super.setMinimumSize(d);
    else component.setMinimumSize(d);
  }
  public Dimension getMaximumSize() {
    if (component == null) return super.getMaximumSize();
    else return component.getMaximumSize();
  }
  public void setMaximumSize(Dimension d) {
    if (component == null) super.setMaximumSize(d);
    else component.setMaximumSize(d);
  }

  /**
   * Returns true if this zone's content is visible.
   */
  public boolean getContentsVisible() {return showContents;}
  public void setContentsVisible(boolean b) {showContents = b; this.repaint();}

  /**
   * Returns another zone that toggles visibility when this is clicked, or null.
   */
  public ZonePanel getZoneTarget() {return zoneTarget;}
  public void setZoneTarget(ZonePanel z) {zoneTarget = z;}

  /**
   * Returns a UHSNode id to switch to when this is clicked, or -1.
   */
  public int getLinkTarget() {return linkTarget;}
  public void setLinkTarget(int n) {linkTarget = n;}


  public void paint(Graphics g) {
    super.paint(g);
    if (component != null) {
      if (showContents) {component.paint(g);}
      else {
        paintEdges((Graphics2D)g.create(), Color.GRAY);
      }
    }
    else if (zoneTarget != null) {
      paintEdges((Graphics2D)g.create(), Color.ORANGE);
    }
    else if (linkTarget != -1) {
      paintEdges((Graphics2D)g.create(), Color.GREEN);
    }
    else {
      paintEdges((Graphics2D)g.create(), Color.BLUE);
    }
  }

  private void paintEdges(Graphics2D g2, Color c) {
    g2.setColor(c);
    float dashes[] = {1f,2f};
    g2.setStroke(new BasicStroke(1,BasicStroke.CAP_ROUND,BasicStroke.JOIN_ROUND,1,dashes,0));
    g2.draw(new Rectangle(1, 1, getWidth()-2, getHeight()-2));
  }
}