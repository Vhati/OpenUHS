package net.vhati.openuhs.desktopreader.reader;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.BorderFactory;
import javax.swing.JComponent;

import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.core.UHSRootNode;
import net.vhati.openuhs.desktopreader.reader.NodePanel;
import net.vhati.openuhs.desktopreader.reader.UHSTextArea;


/**
 * A reusable panel that represents a UHSNode.
 *
 * @see net.vhati.openuhs.core.UHSNode
 */
public class DefaultNodePanel extends NodePanel {

	protected UHSNode mainNode = null;
	protected MouseListener clickListener = null;


	public DefaultNodePanel() {
		super();

		this.setLayout( new GridBagLayout() );

		clickListener = new MouseAdapter() {
			@Override
			public void mouseClicked( MouseEvent e ) {
				JComponent sourceComponent = (JComponent)e.getSource();
				Container sourceParent = sourceComponent.getParent();

				int index = -1;
				for ( int i=0; i < sourceParent.getComponentCount(); i++ ) {
					if ( sourceParent.getComponent( i ) == sourceComponent ) {
						index = i;
						break;
					}
				}
				if ( index >= 0 ) {
					UHSNode tmpNode = mainNode.getChild( index );

					if ( tmpNode.isLink() ) {
						int targetIndex = tmpNode.getLinkTarget();
						DefaultNodePanel.this.getNavCtrl().setReaderNode( targetIndex );
					}
					else if ( DefaultNodePanel.this.getNavCtrl().isNodeVisitable( tmpNode ) ) {
						DefaultNodePanel.this.getNavCtrl().setReaderNode( tmpNode );
					}
				}
			}
		};
	}


	@Override
	public boolean accept( UHSNode node ) {
		if ( node.isGroup() ) return true;
		return false;
	}

	@Override
	public void setNode( UHSNode node, boolean showAll ) {
		reset();
		if ( !accept( node ) ) return;

		if ( node instanceof UHSRootNode ) showAll = true;

		super.setNode( node, showAll );
		mainNode = node;

		GridBagConstraints gridC = new GridBagConstraints();
		gridC.gridy = 0;
		gridC.gridwidth = GridBagConstraints.REMAINDER;  //End Row
		gridC.weightx = 1.0;
		gridC.weighty = 0;
		gridC.fill = GridBagConstraints.HORIZONTAL;
		gridC.insets = new Insets( 1, 2, 1, 2 );

		boolean allClickable = true;
		for ( int i=0; i < mainNode.getChildCount(); i++ ) {
			UHSNode tmpNode = node.getChild( i );

			UHSTextArea tmpUHSArea = new UHSTextArea();
				tmpUHSArea.setNode( tmpNode, this.getNavCtrl().isNodeVisitable( tmpNode ) );
				tmpUHSArea.setEditable( false );
				tmpUHSArea.setBorder( BorderFactory.createEtchedBorder() );
				this.add( tmpUHSArea, gridC );
			gridC.gridy++;

			if ( tmpNode.isLink() || this.getNavCtrl().isNodeVisitable( tmpNode ) ) {
				tmpUHSArea.addMouseListener( clickListener );
			}
			else if ( tmpNode.getType().equals( "Blank" ) == false ) {
				allClickable = false;
			}
		}

		if ( allClickable || showAll ) {
			mainNode.setCurrentReveal( mainNode.getMaximumReveal() );
		}
		else if ( mainNode.getCurrentReveal() == 0 ) {  // Reveal at least one hint.
			mainNode.setCurrentReveal( Math.min( 1, mainNode.getMaximumReveal() ) );
		}
		int revealValue = mainNode.getCurrentReveal();
		for ( int i=0; i < this.getComponentCount(); i++ ) {
			boolean reveal = ( i < revealValue );
			this.getComponent( i ).setVisible( reveal );
		}

		this.revalidate();
		this.repaint();
	}

	@Override
	public String getTitle() {
		if ( mainNode instanceof UHSRootNode ) {
			return "";
		} else {
			return super.getTitle();
		}
	}

	@Override
	public void reset() {
		mainNode = null;
		this.removeAll();
		super.reset();
	}

	@Override
	public boolean isRevealSupported() {
		return true;
	}

	@Override
	public void revealNext() {
		if ( isComplete() ) return;
		mainNode.setCurrentReveal( mainNode.getCurrentReveal()+1 );
		int revealValue = mainNode.getCurrentReveal();

		boolean guiChanged = false;
		for ( int i=0; i < this.getComponentCount(); i++ ) {
			boolean reveal = ( i < revealValue );
			Component c = this.getComponent( i );
			if ( c.isVisible() != reveal ) {
				c.setVisible( reveal );
				guiChanged = true;
			}
		}
		if ( guiChanged ) {
			this.revalidate();
			this.repaint();
		}
	}
}
