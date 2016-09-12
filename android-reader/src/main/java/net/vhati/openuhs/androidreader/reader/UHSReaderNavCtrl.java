package net.vhati.openuhs.androidreader.reader;


import net.vhati.openuhs.core.UHSNode;


/**
 * An interface to expose UHS reader navigation methods.
 */
public interface UHSReaderNavCtrl {

	/**
	 * Displays a new node within the current tree.
	 *
	 * @param newNode  the new node
	 */
	public void setReaderNode( UHSNode newNode );

	/**
	 * Displays a new node within the current tree.
	 *
	 * @param id  ID of the new node
	 */
	public void setReaderNode( int id );


	/**
	 * Sets the reader's title.
	 *
	 * @param s  the title to be displayed in the reader.
	 */
	public void setReaderTitle( String s );

	/**
	 * Returns the reader's title.
	 *
	 * @return the title of the reader
	 */
	public String getReaderTitle();

	/**
	 * Returns true if the reader supports visiting a given node.
	 */
	public boolean isNodeVisitable( UHSNode node );
}
