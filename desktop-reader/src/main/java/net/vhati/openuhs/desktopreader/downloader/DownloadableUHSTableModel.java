package net.vhati.openuhs.desktopreader.downloader;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Vector;
import javax.swing.table.AbstractTableModel;

import net.vhati.openuhs.core.downloader.DownloadableUHS;


public class DownloadableUHSTableModel extends AbstractTableModel {
	public static final int SORT_TITLE = 0;
	public static final int SORT_DATE = 1;
	public static final int SORT_FULLSIZE = 2;
	public static final int SORT_NAME = 3;

	private int sortOrder = SORT_TITLE;
	private DateFormat dateFormat = new SimpleDateFormat( "yyyy-MM-dd" );
	List<DownloadableUHS> dataVector = new Vector<DownloadableUHS>();
	List<String> colVector = new Vector<String>();


	public DownloadableUHSTableModel( String[] columnNames ) {
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


	public void addUHS( DownloadableUHS duh ) {
		dataVector.add( duh );
		this.fireTableDataChanged();
	}

	public void addUHSs( DownloadableUHS[] duhs ) {
		for ( int i=0; i < duhs.length; i++ ) {
			dataVector.add( duhs[i] );
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


	public DownloadableUHS getUHS( int row ) {
		if ( row < 0 || row >= dataVector.size() ) return null;
		return dataVector.get( row );
	}


	public void clear() {
		dataVector.clear();
		this.fireTableDataChanged();
	}


	public void sort() {
		Collections.sort(dataVector, new Comparator<DownloadableUHS>() {
			@Override
			public int compare( DownloadableUHS a, DownloadableUHS b ) {
				if ( a != null && b != null ) {
					if ( sortOrder == SORT_TITLE ) return a.getTitle().compareTo( b.getTitle() );
					if ( sortOrder == SORT_DATE ) return a.getDate().compareTo( b.getDate() ) * -1;
					if ( sortOrder == SORT_FULLSIZE ) {
						if ( a.getFullSize().matches( "^[0-9]+$" ) && b.getFullSize().matches( "^[0-9]+$" ) ) {
							if ( a.getFullSize().length() == b.getFullSize().length() ) {
								return a.getFullSize().compareTo( b.getFullSize() );
							}
							else {
								return (( a.getFullSize().length() > b.getFullSize().length() ) ? 1 : -1);
							}
						}
						return a.getFullSize().compareTo( b.getFullSize() );
					}
					if ( sortOrder == SORT_NAME ) return a.getName().compareTo( b.getName() );
				}
				return 1;
			}
		});
		this.fireTableDataChanged();
	}

	public void sort( int n ) {
		sortOrder = n;
		sort();
	}
}
