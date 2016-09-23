package net.vhati.openuhs.core.downloader;

import java.util.Comparator;

import net.vhati.openuhs.core.downloader.CatalogItem;


public class CatalogItemComparator implements Comparator<CatalogItem> {
	public static final int SORT_TITLE = 0;
	public static final int SORT_NAME = 1;
	public static final int SORT_DATE = 2;
	public static final int SORT_FULLSIZE = 3;

	protected int sortBy;


	/**
	 * Constructs a comparator that sorts by a given property.
	 *
	 * @param sortBy  one of: SORT_TITLE, SORT_NAME, SORT_DATE, or SORT_FULLSIZE
	 */
	public CatalogItemComparator( int sortBy ) {
		this.sortBy = sortBy;
	}

	/**
	 * Constructs a comparator that sorts by title.
	 */
	public CatalogItemComparator() {
		this( SORT_TITLE );
	}


	@Override
	public int compare( CatalogItem a, CatalogItem b ) {
		if ( a != null && b != null ) {
			if ( sortBy == SORT_TITLE ) {
				return a.getTitle().compareTo( b.getTitle() );
			}
			if ( sortBy == SORT_DATE ) {
				return a.getDate().compareTo( b.getDate() );
			}
			if ( sortBy == SORT_FULLSIZE ) {
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
			if ( sortBy == SORT_NAME ) {
				return a.getName().compareTo( b.getName() );
			}
		}
		return 1;
	}
}
