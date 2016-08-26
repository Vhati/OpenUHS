package net.vhati.openuhs.desktopreader.downloader;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import net.vhati.openuhs.desktopreader.downloader.DownloadableUHS;
import net.vhati.openuhs.desktopreader.downloader.DownloadableUHSTableModel;


public class UHSTableCellRenderer extends DefaultTableCellRenderer {
  Color normalUnselColor = null;


  public UHSTableCellRenderer() {
    //Color changes don't reset on their own, so cache the initial value
    normalUnselColor = this.getBackground();
  }


  @Override
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
