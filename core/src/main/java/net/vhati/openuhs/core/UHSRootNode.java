package net.vhati.openuhs.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.vhati.openuhs.core.UHSNode;


/**
 * A node to hold all others.
 * <p>
 * Additionally a root node is responsible for tracking ids to resolve link
 * targets.
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
	 * <p>
	 * The 9x format begins with a fake 88a format section telling old
	 * readers to upgrade.
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
	 * <p>
	 * The 9x format begins with a fake 88a section telling readers to
	 * upgrade.
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
	 * @param doomedLink  the node to remove
	 * @see #addLink(UHSNode)
	 */
	public void removeLink( UHSNode doomedLink ) {
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
	 * <p>
	 * The node itself will always be returned, without any temporary group
	 * wrapping it.
	 *
	 * @param id  the id of the node to get
	 * @return the node, or null if not found
	 */
	public UHSNode getNodeByLinkId( int id ) {
		UHSNode targetNode = linkMap.get( id+"" );

		return targetNode;
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
	 * Returns the master Subject node containing the table of contents.
	 * <p>
	 * This is equivalent to getting the first Subject child.
	 */
	public UHSNode getMasterSubjectNode() {
		return this.getFirstChild( "Subject", UHSNode.class );
	}


	/**
	 * Returns the title of this hint tree.
	 * <p>
	 * This is the string content of the master Subject node.
	 *
	 * @return the title of the hint file, or null if absent or blank
	 * @see #getMasterSubjectNode()
	 */
	public String getUHSTitle() {
		String result = null;

		UHSNode masterSubjectNode = getMasterSubjectNode();
		if ( masterSubjectNode != null ) {
			result = masterSubjectNode.getDecoratedStringContent();
		}
		if ( result != null && result.length() == 0 ) result = null;

		return result;
	}


	/**
	 * Returns the Version node's content.
	 * <p>
	 * It may be inaccurate, blank, or conflict with what is claimed in the info node.
	 *
	 * @return the reported hint version (e.g., "96a"), or null if absent or blank
	 * @see net.vhati.openuhs.core.UHSParser#parseVersionNode(UHSParseContext, UHSNode, int)
	 */
	public String getUHSVersion() {
		String result = null;

		UHSNode versionNode = this.getFirstChild( "Version", UHSNode.class );
		if ( versionNode != null ) {
			String versionString = versionNode.getDecoratedStringContent();
			if ( versionString.length() > 0 ) result = versionString;
		}

		return result;
	}
}
