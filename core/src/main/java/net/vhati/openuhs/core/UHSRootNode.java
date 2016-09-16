package net.vhati.openuhs.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.vhati.openuhs.core.UHSNode;


/**
 * A node to hold all others.
 *
 * <p>Additionally a root node is responsible for tracking nodes that are link
 * targets.</p>
 */
public class UHSRootNode extends UHSNode {
	private boolean legacy = false;
	private UHSRootNode legacyRootNode = null;
	private Map<String, UHSNode> linkMap = new HashMap<String, UHSNode>();


	public UHSRootNode() {
		super( "Root" );
	}


	/**
	 * Sets a flag indicating this root node is a non-canonical message for old readers.
	 *
	 * <p>The 9x format begins with a fake 88a format section telling old
	 * readers to upgrade.</p>
	 *
	 * #setLegacyRootNode(UHSRootNode)
	 */
	public void setLegacy( boolean b ) {
		legacy = b;
	}

	public boolean isLegacy() {
		return legacy;
	}

	/**
	 * Sets an alternate tree to display in legacy 88a readers.
	 *
	 * <p>The 9x format begins with a fake 88a section telling readers to
	 * upgrade.</p>
	 *
	 * @see #setLegacy(boolean)
	 */
	public void setLegacyRootNode( UHSRootNode legacyRootNode ) {
		this.legacyRootNode = legacyRootNode;
	}

	public UHSRootNode getLegacyRootNode() {
		return legacyRootNode;
	}


	/**
	 * Makes a node available to target by link nodes.
	 *
	 * @param newLink  the node to add
	 */
	public void addLink( UHSNode newLink ) {
		linkMap.put( newLink.getId()+"", newLink );
	}

	/**
	 * Makes a node unavailable to target by link nodes.
	 *
	 * @param id  ID of the node to remove
	 */
	public void removeLinkById( int id ) {
		if ( !linkMap.containsKey( id+"" ) ) return;
		linkMap.remove( id+"" );
	}

	/**
	 * Makes a node unavailable to target by link nodes.
	 *
	 * <p>TODO: Due to a workaround that introduced arbitrary many-to-one
	 * linking for UHSHotSpotNodes, this method has to iterate linkMap's
	 * values to remove every map entry with the non-unique node.</p>
	 *
	 * @param doomedLink  the node to remove
	 * @see #addLink(UHSNode, int)
	 */
	public void removeLink( UHSNode doomedLink ) {
		//if ( !linkMap.containsKey( doomedLink.getId()+"" ) ) return;
		//linkMap.remove( doomedLink.getId()+"" );

		for ( Iterator<UHSNode> it = linkMap.values().iterator(); it.hasNext(); ) {
			if ( doomedLink.equals( it.next() ) ) {
				it.remove();
			}
		}
	}

	/**
	 * Associates a node with an arbitrary id, for unusual link nodes.
	 *
	 * <p>Some link nodes point to the main image in a HyperImage hunk.</p>
	 *
	 * <p>TODO: This method seems like a bad idea. Currently UHSNodes only
	 * claim one id themselves, but UHSHotSpotNodes represent both the title
	 * and main image content.</p>
	 */
	public void addLink( UHSNode newLink, int id ) {
		linkMap.put( id+"", newLink );
	}

	/**
	 * Makes all nodes unavailable to target by link nodes.
	 */
	public void removeAllLinks() {
		linkMap.clear();
	}


	/**
	 * Gets a node by its id.
	 *
	 * <p>The node itself will always be returned,
	 * without any temporary group wrapping it.</p>
	 *
	 * @param id  the id of the node to get
	 * @return the node, or null if not found
	 */
	public UHSNode getNodeByLinkId( int id ) {
		UHSNode targetNode = linkMap.get( id+"" );

		return targetNode;
	}

	/**
	 * Gets a link's target.
	 *
	 * <p>If the target is not a group, a temporary
	 * group node will be created as a wrapper so that
	 * the target's content will not be treated as
	 * a title.</p>
	 *
	 * @param id  the id of the node to get
	 * @return the node, possibly wrapped, or null if not found
	 */
	public UHSNode getLink( int id ) {
		UHSNode targetNode = linkMap.get( id+"" );
		if ( targetNode == null ) return null;

		if ( targetNode.isGroup() ) {
			return targetNode;
		}
		else {
			UHSNode wrapperNode = new UHSNode( "LinkWrapper" );
			wrapperNode.setRawStringContent( "" );
			wrapperNode.addChild( targetNode );
			return wrapperNode;
		}
	}

	public int getLinkCount() {
		return linkMap.size();
	}


	@Override
	public void setChildren( List<UHSNode> newChildren ) {
		super.setChildren( newChildren );
		if ( this.getChildCount() > 0 ) {
			this.setCurrentReveal( this.getMaximumReveal() );
		}
	}

	@Override
	public void addChild( UHSNode newChild ) {
		super.addChild( newChild );
		if ( this.getChildCount() > 0 ) {
			this.setCurrentReveal( this.getMaximumReveal() );
		}
	}


	/**
	 * Returns the title of this hint tree.
	 *
	 * <p>It may be the root's content, or the content of the
	 * first child, if it's a Subject node with String content.</p>
	 *
	 * @return the title of the hint file, or null if absent or blank
	 */
	public String getUHSTitle() {
		String result = null;

		String tmp = this.getDecoratedStringContent();
		if ( !"Root".equals( tmp ) ) {
			result = tmp;
		}
		else if ( this.getChildCount() > 0 ) {
			UHSNode subjectNode = this.getFirstChild( "Subject", UHSNode.class );
			if ( subjectNode != null ) {
				result = subjectNode.getDecoratedStringContent();
			}
		}

		if ( result != null && result.length() == 0 ) result = null;

		return result;
	}


	/**
	 * Reverse-searches immediate children and returns the last Version node's content.
	 *
	 * <p>It may be inaccurate, blank, or conflict with what is claimed in the info node.</p>
	 *
	 * <p>"Version: " will be stripped from the beginning.</p>
	 *
	 * @return the reported hint version (e.g., "96a"), or null if absent or blank
	 */
	public String getUHSVersion() {
		String result = null;

		for ( int i=this.getChildCount()-1; result == null && i >= 0; i-- ) {
			UHSNode tmpNode = this.getChild( i );
			if ( "Version".equals( tmpNode.getType() ) ) {
				result = tmpNode.getDecoratedStringContent();
			}
		}

		if ( result != null ) {
			if ( result.startsWith( "Version: " ) ) result = result.substring( 9 );
			if ( result.length() == 0 ) result = null;
		}
		return result;
	}
}
