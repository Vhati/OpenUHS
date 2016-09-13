package net.vhati.openuhs.core;

import java.util.HashMap;
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
	private Map<String, UHSNode> linkMap = new HashMap<String, UHSNode>();


	public UHSRootNode() {
		super( "Root" );
	}


	/**
	 * Sets whether this root node is a non-canonical message for old readers.
	 *
	 * <p>The 9x format begins with a fake 88a format tree telling old readers
	 * to upgrade.</p>
	 */
	public void setLegacy( boolean b ) {
		legacy = b;
	}

	public boolean isLegacy() {
		return legacy;
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
	 * @param doomedLink  the node to remove
	 */
	public void removeLink( UHSNode doomedLink ) {
		if ( !linkMap.containsKey( doomedLink.getId()+"" ) ) return;
		linkMap.remove( doomedLink.getId()+"" );
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
