package net.vhati.openuhs.desktopreader.reader;

import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComponent;

import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.core.UHSRootNode;
import net.vhati.openuhs.desktopreader.reader.NodePanel;
import net.vhati.openuhs.desktopreader.reader.UHSTextArea;


/**
 * A reusable panel that represents a UHSRootNode.
 *
 * <p>This panel does not display root's children canonically.
 * <ul>
 * <li>Where the master Subject node would be, that node's children will be
 * listed instead.</li>
 * <li>A Blank node will be included as a divider.</li>
 * <li>Remaining top-level children (auxiliary nodes) will appear after the
 * divider.</li>
 * </ul></p>
 *
 * @see net.vhati.openuhs.core.UHSRootNode
 */
public class RootNodePanel extends NodePanel {

	protected UHSRootNode rootNode = null;
	protected List<UHSNode> nodeList = new ArrayList<UHSNode>();
	protected MouseListener clickListener = null;


	public RootNodePanel() {
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
					UHSNode tmpNode = nodeList.get( index );

					if ( tmpNode.isLink() ) {
						int targetIndex = tmpNode.getLinkTarget();
						RootNodePanel.this.getNavCtrl().setReaderNode( targetIndex );
					}
					else if ( RootNodePanel.this.getNavCtrl().isNodeVisitable( tmpNode ) ) {
						RootNodePanel.this.getNavCtrl().setReaderNode( tmpNode );
					}
				}
			}
		};
	}


	@Override
	public boolean accept( UHSNode node ) {
		if ( node instanceof UHSRootNode ) return true;
		return false;
	}

	@Override
	public void setNode( UHSNode node, boolean showAll ) {
		reset();
		if ( !accept( node ) ) return;

		super.setNode( node, showAll );
		rootNode = (UHSRootNode)node;

		// Collect master Subject node's children.
		UHSNode masterSubjectNode = rootNode.getMasterSubjectNode();
		if ( masterSubjectNode != null ) {
			for ( int i=0; i < masterSubjectNode.getChildCount(); i++ ) {
				UHSNode tmpNode = masterSubjectNode.getChild( i );
				nodeList.add( tmpNode );
			}
		}

		// Divider.
		UHSNode blankNode = new UHSNode( "Blank" );
		blankNode.setRawStringContent( "--=File Info=--" );
		nodeList.add( blankNode );

		// Collect auxiliary nodes.
		for ( int i=0; i < rootNode.getChildCount(); i++ ) {
			UHSNode tmpNode = rootNode.getChild( i );
			if ( tmpNode.equals( masterSubjectNode ) ) continue;

			nodeList.add( tmpNode );
		}

		GridBagConstraints gridC = new GridBagConstraints();
		gridC.gridy = 0;
		gridC.gridwidth = GridBagConstraints.REMAINDER;  //End Row
		gridC.weightx = 1.0;
		gridC.weighty = 0;
		gridC.fill = GridBagConstraints.HORIZONTAL;
		gridC.insets = new Insets( 1, 2, 1, 2 );

		for ( UHSNode tmpNode : nodeList ) {
			UHSTextArea tmpUHSArea = new UHSTextArea();
				tmpUHSArea.setNode( tmpNode, this.getNavCtrl().isNodeVisitable( tmpNode ) );
				tmpUHSArea.setEditable( false );
				tmpUHSArea.setBorder( BorderFactory.createEtchedBorder() );
				this.add( tmpUHSArea, gridC );
			gridC.gridy++;

			if ( tmpNode.isLink() || this.getNavCtrl().isNodeVisitable( tmpNode ) ) {
				tmpUHSArea.addMouseListener( clickListener );
			}

			if ( "Version".equals( tmpNode.getType() ) ) {
				tmpUHSArea.setOverrideText( String.format( "%s: %s", tmpNode.getType(), tmpNode.getRawStringContent() ) );
			}
			else if ( "Incentive".equals( tmpNode.getType() ) ) {
				tmpUHSArea.setOverrideText( String.format( "%s: %s", tmpNode.getType(), tmpNode.getRawStringContent() ) );
			}
			else if ( "Info".equals( tmpNode.getType() ) ) {
				tmpUHSArea.setOverrideText( String.format( "%s: %s", tmpNode.getType(), tmpNode.getRawStringContent() ) );
			}
			else if ( "Credit".equals( tmpNode.getType() ) ) {
				tmpUHSArea.setOverrideText( String.format( "%s: %s", tmpNode.getType(), tmpNode.getRawStringContent() ) );
			}
		}

		// Reveal isn't used, but setting it regardless.
		rootNode.setCurrentReveal( rootNode.getMaximumReveal() );

		this.revalidate();
		this.repaint();
	}

	@Override
	public String getTitle() {
		return "";
	}

	@Override
	public void reset() {
		rootNode = null;
		this.removeAll();
		nodeList.clear();
		super.reset();
	}
}
