package net.vhati.openuhs.desktopreader.downloader;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import javax.swing.table.AbstractTableModel;

import net.vhati.openuhs.core.downloader.CatalogItem;
import net.vhati.openuhs.core.downloader.CatalogItemComparator;


public class CatalogTableModel extends AbstractTableModel {
	private Comparator<CatalogItem> comparator = new CatalogItemComparator();
	private DateFormat dateFormat = new SimpleDateFormat( "yyyy-MM-dd" );

	List<CatalogItem> dataVector = new Vector<CatalogItem>();
	List<String> colVector = new Vector<String>();


	public CatalogTableModel( String[] columnNames ) {
		for ( int i=0; i < columnNames.length; i++ ) {
			colVector.add( columnNames[i] );
		}
	}

	@Override
	public int getRowCount() {
		return dataVector.size();
	}

	@Override
	public int getColumnCount() {
		return colVector.size();
	}

	@Override
	public String getColumnName( int column ) {
		if ( column < 0 || column >= colVector.size() ) return null;

		return colVector.get( column );
	}

	@Override
	public Object getValueAt( int row, int column ) {
		if ( column < 0 || column >= colVector.size() || row < 0 || row >= dataVector.size() ) return null;

		Object value = null;
		if ( getColumnName( column ).equals( "Title" ) ) {
			value = dataVector.get( row ).getTitle();
		}
		else if ( getColumnName( column ).equals( "Name" ) ) {
			value = dataVector.get( row ).getName();
		}
		else if (getColumnName( column ).equals( "Date" ) ) {
			Date uhsDate = dataVector.get( row ).getDate();
			value = (( uhsDate != null ) ? dateFormat.format( dataVector.get( row ).getDate() ) : "");
		}
		else if ( getColumnName( column ).equals( "Size" ) ) {
			value = dataVector.get( row ).getCompressedSize();
		}
		else if ( getColumnName( column ).equals( "FullSize" ) ) {
			value = dataVector.get( row ).getFullSize();
		}
		return value;
	}

	@Override
	public boolean isCellEditable( int x, int y ) {
		return false;
	}


	public void addUHS( CatalogItem catItem ) {
		dataVector.add( catItem );
		this.fireTableDataChanged();
	}

	public void addUHSs( CatalogItem[] catItems ) {
		for ( int i=0; i < catItems.length; i++ ) {
			dataVector.add( catItems[i] );
		}
		this.fireTableDataChanged();
	}


	public void removeUHSs( int[] indeces ) {
		Vector<Integer> indexVector = new Vector<Integer>();
		for ( int i=0; i < indeces.length; i++ ) {
			indexVector.add( new Integer( indeces[i] ) );
		}
		Collections.sort( indexVector );

		for ( int i=indexVector.size()-1; i >= 0; i-- ) {
			dataVector.remove( indexVector.get( i ).intValue() );
		}
		this.fireTableDataChanged();
	}


	public CatalogItem getUHS( int row ) {
		if ( row < 0 || row >= dataVector.size() ) return null;
		return dataVector.get( row );
	}


	public void clear() {
		dataVector.clear();
		this.fireTableDataChanged();
	}


	public void sort() {
		Collections.sort( dataVector, comparator );
		this.fireTableDataChanged();
	}

	public void sort( Comparator<CatalogItem> c ) {
		comparator = c;
		sort();
	}
}
