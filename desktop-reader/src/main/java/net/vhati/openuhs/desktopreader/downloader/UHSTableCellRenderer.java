package net.vhati.openuhs.desktopreader.downloader;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import net.vhati.openuhs.core.downloader.DownloadableUHS;
import net.vhati.openuhs.desktopreader.downloader.DownloadableUHSTableModel;


public class UHSTableCellRenderer extends DefaultTableCellRenderer {
	private Color localColor = new Color( 225, 225, 225 );
	private Color newerColor = new Color( 255, 255, 200 );

	Color normalUnselColor = null;


	public UHSTableCellRenderer() {
		//Color changes don't reset on their own, so cache the initial value
		normalUnselColor = this.getBackground();
	}


	@Override
	public Component getTableCellRendererComponent( JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column ) {
		Component c = super.getTableCellRendererComponent( table, value, isSelected, hasFocus, row, column );

		if ( table != null && table.getModel() instanceof DownloadableUHSTableModel ) {
			if ( !isSelected ) {
				DownloadableUHSTableModel model = (DownloadableUHSTableModel)table.getModel();
				DownloadableUHS tmpUHS = model.getUHS( row );
				Color rowColor = normalUnselColor;

				if ( tmpUHS != null ) {
					if ( tmpUHS.isNewer() ) {
						rowColor = newerColor;
					}
					else if ( tmpUHS.isLocal() ) {
						rowColor = localColor;
					}
				}
				c.setBackground( rowColor );
			}
		}

		return c;
	}
}
