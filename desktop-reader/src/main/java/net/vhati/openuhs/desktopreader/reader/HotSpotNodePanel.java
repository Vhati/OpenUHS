package net.vhati.openuhs.desktopreader.reader;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.event.MouseInputAdapter;
import javax.swing.event.MouseInputListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.HotSpot;
import net.vhati.openuhs.core.UHSHotSpotNode;
import net.vhati.openuhs.core.UHSImageNode;
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.desktopreader.reader.NodePanel;
import net.vhati.openuhs.desktopreader.reader.ZonePanel;


/**
 * A reusable panel that represents a UHSHotSpotNode.
 *
 * @see net.vhati.openuhs.core.UHSHotSpotNode
 */
public class HotSpotNodePanel extends NodePanel {

	private final Logger logger = LoggerFactory.getLogger( HotSpotNodePanel.class );

	protected UHSHotSpotNode hotspotNode = null;
	protected MouseInputListener hotspotListener = null;
	protected MouseListener zoneListener = null;


	public HotSpotNodePanel() {
		super();

		this.setLayout( new GridBagLayout() );

		hotspotListener = new MouseInputAdapter() {
			Cursor zoneCursor = Cursor.getPredefinedCursor( Cursor.HAND_CURSOR );
			Cursor normCursor = Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR );

			private int getZone( UHSHotSpotNode hotspotNode, int x, int y ) {
				for ( int i=1; i < hotspotNode.getChildCount(); i++ ) {
					HotSpot spot = hotspotNode.getSpot( hotspotNode.getChild( i ) );
					if ( x > spot.zoneX && y > spot.zoneY && x < spot.zoneX+spot.zoneW && y < spot.zoneY+spot.zoneH ) {
						return i;
					}
				}
				return -1;
			}
			@Override
			public void mouseMoved( MouseEvent e ) {
				Component sourceComponent = (Component)e.getSource();

				int x = e.getX(); int y = e.getY();

				if ( getZone( hotspotNode, x, y ) != -1 ) {
					sourceComponent.setCursor( zoneCursor );
				} else {
					sourceComponent.setCursor( normCursor );
				}
			}
		};

		zoneListener = new MouseInputAdapter() {
			Cursor zoneCursor = Cursor.getPredefinedCursor( Cursor.HAND_CURSOR );
			Cursor normCursor = Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR );

			@Override
			public void mouseEntered( MouseEvent e ) {
				ZonePanel sourcePanel = (ZonePanel)e.getSource();
				sourcePanel.setCursor( zoneCursor );
			}
			@Override
			public void mouseExited( MouseEvent e ) {
				ZonePanel sourcePanel = (ZonePanel)e.getSource();
				sourcePanel.setCursor( normCursor );
			}
			@Override
			public void mouseClicked( MouseEvent e ) {
				ZonePanel sourcePanel = (ZonePanel)e.getSource();
				ZonePanel zoneTarget = sourcePanel.getZoneTarget();
				int targetIndex = sourcePanel.getLinkTarget();

				if ( zoneTarget != null ) {
					zoneTarget.setContentsVisible( !zoneTarget.getContentsVisible() );
				}
				else if ( targetIndex != -1 ) {
					HotSpotNodePanel.this.getNavCtrl().setReaderNode( targetIndex );
				}
			}
		};
	}


	@Override
	public boolean accept( UHSNode node ) {
		if ( node instanceof UHSHotSpotNode ) return true;
		return false;
	}

	@Override
	public void setNode( UHSNode node, boolean showAll ) {
		reset();
		if ( !accept( node ) ) return;

		super.setNode( node, showAll );
		hotspotNode = (UHSHotSpotNode)node;

		GridBagConstraints gridC = new GridBagConstraints();
		gridC.gridy = 0;
		gridC.gridwidth = GridBagConstraints.REMAINDER;  //End Row
		gridC.weightx = 1.0;
		gridC.weighty = 0;
		gridC.fill = GridBagConstraints.HORIZONTAL;
		gridC.insets = new Insets( 1, 2, 1, 2 );

		JLayeredPane sharedPanel = new JLayeredPane();

		// The main image is visible and full size.

		byte[] mainImageBytes = hotspotNode.getRawImageContent();
		JLabel mainImageLbl = new JLabel( new ImageIcon( mainImageBytes ) );

		ZonePanel mainContentPanel = new ZonePanel( mainImageLbl );
			Dimension mainContentSize = mainContentPanel.getPreferredSize();
			mainContentPanel.setBounds( 0, 0, mainContentSize.width, mainContentSize.height );
			mainContentPanel.setContentsVisible( true );
			sharedPanel.add( mainContentPanel, JLayeredPane.DEFAULT_LAYER, 0 );

		// Stretch the shared panel to fit the main image.
		sharedPanel.setPreferredSize( mainContentSize );
		sharedPanel.setMinimumSize( mainContentSize );


		for ( int i=0; i < node.getChildCount(); i++ ) {
			UHSNode childNode = node.getChild( i );
			String childType = childNode.getType();

			if ( "Overlay".equals( childType ) && childNode instanceof UHSImageNode ) {
				UHSImageNode overlayNode = (UHSImageNode)childNode;
				String title = overlayNode.getDecoratedStringContent();

				HotSpot spot = hotspotNode.getSpot( overlayNode );

				byte[] overlayImageBytes = overlayNode.getRawImageContent();
				JLabel overlayImageLbl = new JLabel( new ImageIcon( overlayImageBytes ) );

				ZonePanel contentPanel = new ZonePanel( overlayImageLbl );
					Dimension pSize = contentPanel.getPreferredSize();
					contentPanel.setBounds( spot.x, spot.y, pSize.width, pSize.height );
					sharedPanel.add( contentPanel, JLayeredPane.DEFAULT_LAYER, 0 );

				ZonePanel spotPanel = new ZonePanel();
					spotPanel.setToolTipText( title );
					spotPanel.setZoneTarget( contentPanel );
					spotPanel.setBounds( spot.zoneX, spot.zoneY, spot.zoneW, spot.zoneH );
					sharedPanel.add( spotPanel, JLayeredPane.PALETTE_LAYER, 0 );
					spotPanel.addMouseListener( zoneListener );

			}
			else {
				String text = childNode.getDecoratedStringContent();

				HotSpot spot = hotspotNode.getSpot( childNode );

				ZonePanel spotPanel = new ZonePanel();
					spotPanel.setToolTipText( text );
					spotPanel.setBounds( spot.zoneX, spot.zoneY, spot.zoneW, spot.zoneH );
					sharedPanel.add( spotPanel, JLayeredPane.PALETTE_LAYER, 0 );
					spotPanel.addMouseListener( zoneListener );

				if ( childNode.isLink() ) { // Follow the link when clicked.
					spotPanel.setLinkTarget( childNode.getLinkTarget() );
				}
				else if ( childNode.getId() != -1 ) { // Visit that child when clicked.
					spotPanel.setLinkTarget( childNode.getId() );
				}
				else {
					logger.error( "Unexpected {} child of UHSHotSpotNode", childNode.getType() );
				}
			}
		}

		this.add( sharedPanel, gridC );
		gridC.gridy++;

		this.revalidate();
		this.repaint();
	}

	@Override
	public void reset() {
		hotspotNode = null;
		this.removeAll();
		super.reset();

		this.revalidate();
		this.repaint();
	}


	/**
	 * Returns false, meaning the panel's width is unconstrained by its viewport (horizontal scrolling enabled).
	 */
	@Override
	public boolean getScrollableTracksViewportWidth() {
		return false;
	}
}
