package net.vhati.openuhs.androidreader.reader;

import android.content.Context;
import android.widget.FrameLayout;

import net.vhati.openuhs.androidreader.reader.UHSReaderNavCtrl;
import net.vhati.openuhs.core.UHSNode;


public abstract class NodeView extends FrameLayout {
	private UHSNode node = null;
	private UHSReaderNavCtrl navCtrl = null;


	public NodeView( Context context ) {
		super( context );
	}


	/**
	 * Returns true if this panel could represent a given node, if visited.
	 */
	public abstract boolean accept( UHSNode node );

	/**
	 * Sets a new note to represent.
	 *
	 * <p>Subclasses should override this and call super.setNode() early.</p>
	 *
	 * @param node  the new node
	 * @param showAll  true if all child hints should be revealed, false otherwise
	 */
	public void setNode( UHSNode node, boolean showAll ) {
		this.node = node;
	}

	public UHSNode getNode() {
		return node;
	}

	/**
	 * Returns a question/section string to display above this panel.
	 */
	public String getTitle() {
		StringBuilder buf = new StringBuilder();
		buf.append( node.getType() ).append( "=" );
		buf.append( node.getDecoratedStringContent() );

		return buf.toString();
	}

	/**
	 * Resets this panel's state.
	 *
	 * <p>Subclasses should override this and call super.reset() late.</p>
	 */
	public void reset() {
		node = null;
	}


	/**
	 * Sets an object to notify of navigation triggers.
	 */
	public void setNavCtrl( UHSReaderNavCtrl navCtrl ) {
		this.navCtrl = navCtrl;
	}

	public UHSReaderNavCtrl getNavCtrl() {
		return navCtrl;
	}


	/**
	 * Returns whether this panel supports revealing hints incrementally.
	 *
	 * <p>Subclasses that support revealing should override this.</p>
	 *
	 * @see #getCurrentReveal()
	 * @see #getMaximumReveal()
	 * @see #isRevealSupported()
	 * @see #revealNext()
	 */
	public boolean isRevealSupported() {
		return false;
	}

	/**
	 * Returns the highest value that might be returned by getCurrentReveal().
	 *
	 * <p>This defers to the node's reported value.</p>
	 *
	 * @see #getCurrentReveal()
	 * @see #isRevealSupported()
	 * @see #revealNext()
	 */
	public int getMaximumReveal() {
		return node.getMaximumReveal();
	}

	/**
	 * Returns the current reveal progress.
	 *
	 * <p>This defers to the node's reported value.</p>
	 *
	 * @see #getMaximumReveal()
	 * @see #isRevealSupported()
	 * @see #revealNext()
	 */
	public int getCurrentReveal() {
		return node.getCurrentReveal();
	}

	/**
	 * Reveals the next hint, possibly more, by setting components' visibility.
	 *
	 * <p>Subclasses that support revealing should override this.
	 * After setting a node's reveal, call its getter for the new current
	 * value. Don't assume it only incremented.</p>
	 *
	 * @see #getCurrentReveal()
	 * @see #getMaximumReveal()
	 * @see #isRevealSupported()
	 */
	public void revealNext() {
	}


	/**
	 * Returns true if no further reveals are possible, false otherwise.
	 */
	public boolean isComplete() {
		if ( !isRevealSupported() ) {
			return true;
		}
		else if ( getCurrentReveal() >= getMaximumReveal() ) {
			return true;
		}
		else {
			return false;
		}
	}
}
