package net.vhati.openuhs.desktopreader.reader;

import java.awt.Dimension;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;


/**
 * A slightly modified JPanel.
 * <br />It grows no larger than its enclosing JScrollPane so flowing components within can wrap.
 */
public class JScrollablePanel extends JPanel implements Scrollable {
  public JScrollablePanel() {
    super();
  }

  public JScrollablePanel(LayoutManager layout) {
    super(layout);
  }

  public Dimension getPreferredScrollableViewportSize() {return new Dimension(1,1);}
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {return 40;}
  public boolean getScrollableTracksViewportHeight() {return false;}
  public boolean getScrollableTracksViewportWidth() {return true;}
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {return 20;}
}