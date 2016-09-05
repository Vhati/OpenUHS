package net.vhati.openuhs.core;

import java.util.List;
import java.util.Vector;

import net.vhati.openuhs.core.HotSpot;
import net.vhati.openuhs.core.UHSNode;


/**
 * A container for UHSNodes that should be revealed in batches.
 *
 * <p>Children behave normally, except when one is revealed.
 * All nodes marked as 'addon' that immediately follow the
 * revealed one are revealed as well. The revealed count
 * increments accordingly.</p>
 */
public class UHSBatchNode extends UHSNode {
	private List<Boolean> batchAddons = new Vector<Boolean>();


	public UHSBatchNode( String inType ) {
		super( inType );
	}


	/**
	 * Returns the batch status of a child.
	 *
	 * @param inChild  a child node
	 * @return true if that node should be revealed when its predecessor is
	 */
	public boolean isAddon( UHSNode inChild ) {
		int index = super.indexOfChild( inChild );
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
	 * @param inChild  a child node
	 * @param addon  true if that node should be revealed when its predecessor is, false otherwise
	 */
	public void setAddon( UHSNode inChild, boolean addon ) {
		int index = super.indexOfChild( inChild );
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


	@Override
	public Object getContent() {
		return super.getContent();
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
	 * <p>This method gives the new nodes default batch status (revealed individually).</p>
	 *
	 * @param inChildren  a List of new child UHSNodes
	 */
	@Override
	public void setChildren( List<UHSNode> inChildren ) {
		if ( inChildren == null || inChildren.size() == 0 ) {
			this.removeAllChildren();
		}
		else {
			super.setChildren( inChildren );
			batchAddons.clear();
			for ( int i=0; i < inChildren.size(); i++ ) {
				batchAddons.add( Boolean.FALSE );
			}
		}
	}


	@Override
	public void addChild( UHSNode inChild ) {
		super.addChild( inChild );
		batchAddons.add( Boolean.FALSE );
	}

	@Override
	public void removeChild( UHSNode inChild ) {
		int index = super.indexOfChild( inChild );
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
	 * Sets the number of revealed children.
	 *
	 * <p>The given amount will be a minimum. Any 'addon' children that follow
	 * will further increment the value.</p>
	 *
	 * @param n  a number greater than 1 and less than or equal to the child count
	 */
	@Override
	public void setRevealedAmount( int n ) {
		if ( n > this.getChildCount() || n < 1 ) return;
		int pendingReveal = n;
		for ( int i=(n-1)+1; i < this.getChildCount(); i++ ) {  // Check the children that follow.
			if ( batchAddons.get( i ).booleanValue() ) {
				pendingReveal++;
			} else {
				break;
			}
		}
		super.setRevealedAmount( pendingReveal );
	}
}
