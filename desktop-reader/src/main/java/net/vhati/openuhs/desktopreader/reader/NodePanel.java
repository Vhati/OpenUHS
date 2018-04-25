package net.vhati.openuhs.desktopreader.reader;

import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.JPanel;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;

import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.desktopreader.reader.UHSReaderNavCtrl;


/**
 * A reusable panel that represents a UHSNode.
 *
 * @see net.vhati.openuhs.core.UHSNode
 */
public abstract class NodePanel extends JPanel implements Scrollable {

	private UHSNode node = null;
	private UHSReaderNavCtrl navCtrl = null;


	public NodePanel() {
	}


	/**
	 * Returns true if this panel could represent a given node, if visited.
	 */
	public abstract boolean accept( UHSNode node );

	/**
	 * Sets a new note to represent.
	 * <p>
	 * Subclasses should override this and call super.setNode() early.
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
	 * <p>
	 * Subclasses should override this and call super.reset() late.
	 */
	public void reset() {
		this.node = null;
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
	 * <p>
	 * Subclasses that support revealing should override this.
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
	 * <p>
	 * This defers to the node's reported value.
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
	 * <p>
	 * This defers to the node's reported value.
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
	 * <p>
	 * Subclasses that support revealing should override this.
	 * After setting a node's reveal, call its getter for the new current
	 * value. Don't assume it only incremented.
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


	@Override
	public Dimension getPreferredScrollableViewportSize() {
		return new Dimension( 1, 1 );
	}

	/**
	 * Returns a unit increment that scrolls 25 pixels.
	 */
	@Override
	public int getScrollableUnitIncrement( Rectangle visibleRect, int orientation, int direction ) {
		return 25;
	}

	/**
	 * Returns a block increment that scrolls through half the visible rectangle.
	 */
	@Override
	public int getScrollableBlockIncrement( Rectangle visibleRect, int orientation, int direction ) {
		if ( orientation == SwingConstants.VERTICAL ) {
			return visibleRect.height / 2;
		} else {
			return visibleRect.width / 2;
		}
	}

	/**
	 * Returns false, meaning the panel's height is unconstrained by its viewport (vertical scrolling enabled).
	 */
	@Override
	public boolean getScrollableTracksViewportHeight() {
		return false;
	}

	/**
	 * Returns true, meaning the panel's width will match its viewport (horizontal scrolling disabled).
	 */
	@Override
	public boolean getScrollableTracksViewportWidth() {
		return true;
	}
}
