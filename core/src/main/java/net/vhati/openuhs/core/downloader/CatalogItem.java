package net.vhati.openuhs.core.downloader;

import java.util.Date;


/**
 * Catalog info about a UHS catalog entry (and its local file).
 *
 * <p>The CatalogParser will set most fields.</p>
 *
 * <p>Additional state flags can be set for tracking the entry's relation to its local file.</p>
 *
 * @see net.vhati.openuhs.core.downloader.CatalogParser
 */
public class CatalogItem {
	private String title = "";
	private String url = "";
	private String name = "";
	private Date date = null;
	private String compressedSize = "";
	private String fullSize = "";

	private boolean stateLocal = false;
	private boolean stateNewer = false;


	public CatalogItem() {
	}


	public void setTitle( String s ) {
		title = (( s != null ) ? s : "");
	}

	public String getTitle() {
		return title;
	}

	public void setUrl( String s ) {
		url = (( s != null ) ? s : "");
	}

	public String getUrl() {
		return url;
	}

	public void setName( String s ) {
		name = (( s != null ) ? s : "");
	}

	public String getName() {
		return name;
	}

	public void setDate( Date d ) {
		date = d;
	}

	public Date getDate() {
		return date;
	}

	/**
	 * Sets the reported size of the zip file that wraps the UHS file.
	 */
	public void setCompressedSize( String s ) {
		compressedSize = (( s != null ) ? s : "");
	}

	public String getCompressedSize() {
		return compressedSize;
	}

	/**
	 * Sets the reported size of the UHS file, once extracted from the zip file.
	 */
	public void setFullSize( String s ) {
		fullSize = (( s != null ) ? s : "");
	}

	public String getFullSize() {
		return fullSize;
	}


	/**
	 * Sets whether there is a local file with this catalog entry's name.
	 *
	 * @see #resetState()
	 */
	public void setLocal( boolean b ) {
		stateLocal = b;
	}

	public boolean isLocal() {
		return stateLocal;
	}

	/**
	 * Sets whether the catalog entry is newer than the local file.
	 *
	 * @see #resetState()
	 */
	public void setNewer( boolean b ) {
		stateNewer = b;
	}

	public boolean isNewer() {
		return stateNewer;
	}

	/**
	 * Resets the catalog-vs-local state flage.
	 *
	 * @see #setLocal(boolean)
	 * @see #setNewer(boolean)
	 */
	public void resetState() {
		setLocal( false );
		setNewer( false );
	}


	@Override
	public String toString() {
		return getTitle();
	}
}
