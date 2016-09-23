package net.vhati.openuhs.androidreader.downloader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import net.vhati.openuhs.androidreader.downloader.CatalogRowView;
import net.vhati.openuhs.core.downloader.CatalogItem;
import net.vhati.openuhs.core.downloader.CatalogItemComparator;


public class CatalogAdapter extends BaseAdapter {
	private int defaultColor = android.graphics.Color.BLACK;
	private int localColor = android.graphics.Color.DKGRAY;
	private int newerColor = android.graphics.Color.LTGRAY;

	private boolean localFilter = false;
	private Comparator<CatalogItem> sortFilter = null;
	private String titleFilter = null;

	private final Context context;
	private List<CatalogItem> originalCatalog = Collections.emptyList();
	private List<CatalogItem> filteredCatalog = new ArrayList<CatalogItem>();


	public CatalogAdapter( Context context ) {
		this.context = context;
	}


	/**
	 * Returns the number of items in the filtered catalog.
	 */
	@Override
	public int getCount() {
		return filteredCatalog.size();
	}

	@Override
	public long getItemId( int position ) {
		return position;
	}


	@Override
	public View getView( int position, View convertView, ViewGroup parent ) {
		View view = convertView;

		if ( view == null ) {
			view = new CatalogRowView( context );
		}
		CatalogItem catItem = getItem( position );

		((CatalogRowView)view).getTitleLabel().setText( catItem.getTitle() );

		int iconColor = defaultColor;

		if ( catItem.isNewer() ) {
			iconColor = newerColor;
		}
		else if ( catItem.isLocal() ) {
			iconColor = localColor;
		}
		((CatalogRowView)view).getIcon().setBackgroundColor( iconColor );


		return view;
	}


	/**
	 * Sets a new original catalog to back this adapter.
	 */
	public void setCatalog( List<CatalogItem> newCatalog ) {
		originalCatalog = newCatalog;
		applyFilters();
	}

	/**
	 * Returns the original catalog backing this adapter.
	 */
	public List<CatalogItem> getCatalog() {
		return originalCatalog;
	}

	/**
	 * Returns the CatalogItem at a position in the filtered catalog.
	 */
	public CatalogItem getItem( int position ) {
		return filteredCatalog.get( position );
	}


	/**
	 * Toggles whether to only show local CatalogItems.
	 *
	 * <p>This means pruning any items for which isLocal() returns false.</p>
	 *
	 * @see #applyFilters()
	 * @see net.vhati.openuhs.core.downloader.CatalogItem#isLocal()
	 */
	public void setLocalFilterEnabled( boolean b ) {
		localFilter = b;
	}

	public boolean isLocalFilterEnabled() {
		return localFilter;
	}

	/**
	 * Sets a comparator to sort the catalog.
	 *
	 * @para c  the comparator, or null
	 * @see #applyFilters()
	 */
	public void setSortFilter( Comparator<CatalogItem> c ) {
		sortFilter = c;
	}

	public Comparator<CatalogItem> getSortFilter() {
		return sortFilter;
	}

	/**
	 * Sets a substring to find in CatalogItem titles.
	 *
	 * <p>This means pruning any items whose titles lack the substring.</p>
	 *
	 * @para s  the substring, or null
	 * @see net.vhati.openuhs.core.downloader.CatalogItem#getTitle()
	 */
	public void setTitleFilter( String s ) {
		titleFilter = s;
	}

	public String getTitleFilter() {
		return titleFilter;
	}


	/**
	 * Repopulates the filteredCatalog with items from the original catalog.
	 *
	 * <p>Note: Remember to call notifyDataSetChanged() afterward.</p>
	 */
	protected void resetFilteredCatalog() {
		filteredCatalog.clear();
		filteredCatalog.addAll( originalCatalog );
	}

	/**
	 * Resets, prunes, and sorts the filtered catalog.
	 *
	 * @see #setLocalFilterEnabled(boolean)
	 * @see #setSortFilter(Comparator)
	 */
	public void applyFilters() {
		resetFilteredCatalog();

		if ( isLocalFilterEnabled() ) {
			Iterator<CatalogItem> it = filteredCatalog.iterator();
			while ( it.hasNext() ) {
				if ( !it.next().isLocal() ) it.remove();
			}
		}

		if ( getTitleFilter() != null ) {
			Pattern titlePtn = Pattern.compile( Pattern.quote( titleFilter ), Pattern.CASE_INSENSITIVE );

			Iterator<CatalogItem> it = filteredCatalog.iterator();
			while ( it.hasNext() ) {
				String itemTitle = it.next().getTitle();
				if ( !titlePtn.matcher( itemTitle ).find() ) it.remove();
			}
		}

		if ( getSortFilter() != null ) Collections.sort( filteredCatalog, sortFilter );
		this.notifyDataSetChanged();
	}


	public void setDefaultColor( int c ) {
		defaultColor = c;
	}

	public void setLocalColor( int c ) {
		localColor = c;
	}

	public void setNewerColor( int c ) {
		newerColor = c;
	}
}
