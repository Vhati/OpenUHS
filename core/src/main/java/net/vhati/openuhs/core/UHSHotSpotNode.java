package net.vhati.openuhs.core;

import java.util.List;
import java.util.Vector;

import net.vhati.openuhs.core.ExtraNodeId;
import net.vhati.openuhs.core.HotSpot;
import net.vhati.openuhs.core.UHSImageNode;
import net.vhati.openuhs.core.UHSNode;


/**
 * A container for UHSNodes that have clickable regions.
 *
 * <p>This node has string content for the title, image content for the main
 * background image, and various children. Children are initially invisible
 * and associated with clickable HotSpot zones. When a zone is clicked, that
 * child node is either revealed or visited (if it's not an overlay image).</p>
 *
 * @see net.vhati.openuhs.core.HotSpot
 */
public class UHSHotSpotNode extends UHSImageNode {
	private List<HotSpot> spots = new Vector<HotSpot>();


	public UHSHotSpotNode( String inType ) {
		super( inType );
	}


	/**
	 * Returns the zone/position of a child.
	 *
	 * @param childNode  a child node
	 * @return a HotSpot
	 */
	public HotSpot getSpot( UHSNode childNode ) {
		int index = super.indexOfChild( childNode );
		if ( index == -1 ) return null;
		return spots.get( index );
	}

	/**
	 * Gets the zone/position of a child.
	 *
	 * @param n  index of a child node
	 * @return a HotSpot
	 */
	public HotSpot getSpot( int n ) {
		if ( super.getChildCount()-1 < n ) return null;
		return spots.get( n );
	}

	/**
	 * Sets the zone/position of a child.
	 *
	 * @param childNode  an existing child node
	 * @param spot  a HotSpot
	 */
	public void setSpot( UHSNode childNode, HotSpot spot ) {
		int index = super.indexOfChild( childNode );
		if ( index == -1 ) return;

		spots.set( index, spot );
	}

	/**
	 * Sets the zone/position of a child.
	 *
	 * @param n  index of a child node
	 * @param spot  a HotSpot
	 */
	public void setSpot( int n, HotSpot spot ) {
		if ( super.getChildCount()-1 < n ) return;
		spots.set( n, spot );
	}


	/**
	 * Overridden to make linking impossible.
	 *
	 * @param n  ID of the node to target
	 * @see net.vhati.openuhs.core.UHSNode#setLinkTarget(int)
	 */
	@Override
	public void setLinkTarget( int n ) {
		return;
	}


	/**
	 * Replace or initialize the current children.
	 *
	 * <p>This method gives the new nodes default zones/positions.</p>
	 *
	 * @param newChildren  a List of new child UHSNodes
	 */
	@Override
	public void setChildren( List<UHSNode> newChildren ) {
		if ( newChildren == null || newChildren.size() == 0 ) {
			this.removeAllChildren();
		}
		else {
			super.setChildren( newChildren );
			spots.clear();
			for ( int i=0; i < newChildren.size(); i++ ) {
				spots.add( new HotSpot() );
			}
		}
	}


	@Override
	public void addChild( UHSNode newChild ) {
		super.addChild( newChild );
		spots.add( new HotSpot() );
	}

	@Override
	public void removeChild( UHSNode childNode ) {
		int index = super.indexOfChild( childNode );
		if ( index == -1 ) return;
		super.removeChild( index );
		spots.remove( index );
	}

	@Override
	public void removeAllChildren() {
		super.removeAllChildren();
		spots.clear();
	}



	public static class HotSpotMainImageId extends ExtraNodeId {

		public HotSpotMainImageId( int id ) {
			super( id );
		}

		@Override
		public String toString() {
			return String.format( "%d (HotSpotMainImage)", this.getId() );
		}
	}
}
