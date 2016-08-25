package net.vhati.openuhs.desktopreader.downloader;

import java.awt.*;
import javax.swing.*;
import javax.swing.table.*;


public class UHSTableCellRenderer extends DefaultTableCellRenderer {
  Color normalUnselColor = null;


  public UHSTableCellRenderer() {
    //Color changes don't reset on their own, so cache the initial value
    normalUnselColor = this.getBackground();
  }


  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
    Component c = super.getTableCellRendererComponent(table,value,isSelected,hasFocus,row,column);

    boolean custom = false;
    if (!isSelected && table != null && table.getModel() instanceof DownloadableUHSTableModel) {
      DownloadableUHSTableModel model = (DownloadableUHSTableModel)table.getModel();

      DownloadableUHS tmpUHS = model.getUHS(row);
      if (tmpUHS != null && tmpUHS.getColor() != null) {
        c.setBackground(tmpUHS.getColor());
        custom = true;
      }
    }

    if (!isSelected && !custom) {
      c.setBackground(normalUnselColor);
    }

    return c;
  }
}
