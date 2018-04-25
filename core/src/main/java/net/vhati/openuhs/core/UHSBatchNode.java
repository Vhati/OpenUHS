package net.vhati.openuhs.core;

import java.util.List;
import java.util.Vector;

import net.vhati.openuhs.core.UHSNode;


/**
 * A container for UHSNodes that should be revealed in batches.
 * <p>
 * Children behave normally, except when one is revealed.
 * All nodes marked as 'addon' that immediately follow the
 * revealed one are revealed as well. The revealed count
 * increments accordingly.
 */
public class UHSBatchNode extends UHSNode {
	private List<Boolean> batchAddons = new Vector<Boolean>();


	public UHSBatchNode( String type ) {
		super( type );
	}


	/**
	 * Returns the batch status of a child.
	 *
	 * @param childNode  a child node
	 * @return true if that node should be revealed when its predecessor is
	 */
	public boolean isAddon( UHSNode childNode ) {
		int index = super.indexOfChild( childNode );
		if ( index == -1 ) return false;
		return batchAddons.get( index ).booleanValue();
	}

	/**
	 * Returns the batch status of a child.
	 *
	 * @param n  index of a child node
	 * @return if that node should be revealed when its predecessor is
	 */
	public boolean isAddon( int n ) {
		if ( super.getChildCount()-1 < n ) return false;
		return batchAddons.get( n ).booleanValue();
	}

	/**
	 * Sets the batch status of a child.
	 *
	 * @param childNode  a child node
	 * @param addon  true if that node should be revealed when its predecessor is, false otherwise
	 */
	public void setAddon( UHSNode childNode, boolean addon ) {
		int index = super.indexOfChild( childNode );
		if ( index == -1 ) return;

		batchAddons.set( index, new Boolean( addon ) );
	}

	/**
	 * Sets the zone/position of a child.
	 *
	 * @param n  index of a child node
	 * @param addon  true if that node should be revealed when its predecessor is, false otherwise
	 */
	public void setAddon( int n, boolean addon ) {
		if ( super.getChildCount()-1 < n ) return;
		batchAddons.set( n, new Boolean( addon ) );
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
	 * <p>
	 * This method gives the new nodes default batch status (revealed individually).
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
			batchAddons.clear();
			for ( int i=0; i < newChildren.size(); i++ ) {
				batchAddons.add( Boolean.FALSE );
			}
		}
	}


	@Override
	public void addChild( UHSNode newNode ) {
		super.addChild( newNode );
		batchAddons.add( Boolean.FALSE );
	}

	@Override
	public void removeChild( UHSNode doomedNode ) {
		int index = super.indexOfChild( doomedNode );
		if ( index == -1 ) return;
		super.removeChild( index );
		batchAddons.remove( index );
	}

	@Override
	public void removeAllChildren() {
		super.removeAllChildren();
		batchAddons.clear();
	}


	/**
	 * Sets the current reveal progress.
	 * <p>
	 * The given amount will be a minimum. Revealing will continue to
	 * increment for every 'addon' child following the last one explicitly
	 * revealed.
	 *
	 * @param n  a non-negative number less than or equal to the maximum reveal
	 */
	@Override
	public void setCurrentReveal( int n ) {
		n = Math.min( Math.max( 0, n ), getMaximumReveal() );
		int pendingReveal = n;
		for ( int i=(n-1)+1; i < this.getChildCount(); i++ ) {  // Check the children that follow.
			if ( batchAddons.get( i ).booleanValue() ) {
				pendingReveal++;
			} else {
				break;
			}
		}
		super.setCurrentReveal( pendingReveal );
	}
}
