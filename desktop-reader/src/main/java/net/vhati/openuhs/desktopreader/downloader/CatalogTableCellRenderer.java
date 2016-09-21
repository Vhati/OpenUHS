package net.vhati.openuhs.desktopreader.downloader;

import java.awt.Color;
import java.awt.Component;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;

import net.vhati.openuhs.core.downloader.CatalogItem;
import net.vhati.openuhs.desktopreader.downloader.CatalogTableModel;


public class CatalogTableCellRenderer extends DefaultTableCellRenderer {
	private Color localColor = new Color( 225, 225, 225 );
	private Color newerColor = new Color( 255, 255, 200 );

	Color normalUnselColor = null;


	public CatalogTableCellRenderer() {
		//Color changes don't reset on their own, so cache the initial value
		normalUnselColor = this.getBackground();
	}


	@Override
	public Component getTableCellRendererComponent( JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column ) {
		Component c = super.getTableCellRendererComponent( table, value, isSelected, hasFocus, row, column );

		if ( table != null && table.getModel() instanceof CatalogTableModel ) {
			if ( !isSelected ) {
				CatalogTableModel model = (CatalogTableModel)table.getModel();
				CatalogItem catItem = model.getUHS( row );
				Color rowColor = normalUnselColor;

				if ( catItem != null ) {
					if ( catItem.isNewer() ) {
						rowColor = newerColor;
					}
					else if ( catItem.isLocal() ) {
						rowColor = localColor;
					}
				}
				c.setBackground( rowColor );
			}
		}

		return c;
	}
}
