package net.vhati.openuhs.desktopreader.reader;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.ToolTipManager;
import javax.swing.event.MouseInputAdapter;
import javax.swing.event.MouseInputListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.ByteReference;
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
	protected BufferedImage mainImage = null;

	protected List<ZoneHolder> zoneHolders = new ArrayList<ZoneHolder>();

	protected Cursor zoneCursor = Cursor.getPredefinedCursor( Cursor.HAND_CURSOR );

	protected BasicStroke zoneStroke;
	protected MouseInputListener zoneListener;


	public HotSpotNodePanel() {
		super();

		float zoneDashes[] = {1f, 2f};
		zoneStroke = new BasicStroke( 1, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1, zoneDashes, 0 );

		zoneListener = new MouseInputAdapter() {
			@Override
			public void mouseMoved( MouseEvent e ) {
				boolean inZone = false;
				for ( ZoneHolder zoneHolder : zoneHolders ) {
					if ( zoneHolder.zoneRect.contains( e.getX(), e.getY() ) ) {
						inZone = true;
						HotSpotNodePanel.this.setCursor( zoneCursor );
						HotSpotNodePanel.this.setToolTipText( zoneHolder.title );
						break;
					}
				}
				if ( !inZone ) {
					HotSpotNodePanel.this.setCursor( Cursor.getDefaultCursor() );
					HotSpotNodePanel.this.setToolTipText( null );
				}

				ToolTipManager.sharedInstance().mouseMoved( e );
			}

			@Override
			public void mouseExited( MouseEvent e ) {
				HotSpotNodePanel.this.setCursor( Cursor.getDefaultCursor() );
			}

			@Override
			public void mouseClicked( MouseEvent e ) {
				for ( ZoneHolder zoneHolder : zoneHolders ) {
					if ( zoneHolder.zoneRect.contains( e.getX(), e.getY() ) ) {
						if ( zoneHolder.imageRef != null ) {
							zoneHolder.revealed = !zoneHolder.revealed;
							HotSpotNodePanel.this.repaint();
							// Keep looping through layers? Sure.
						}
						else if ( zoneHolder.linkTarget != -1 ) {
							HotSpotNodePanel.this.getNavCtrl().setReaderNode( zoneHolder.linkTarget );
							break;
						}
					}
				}
			}
		};
		this.addMouseListener( zoneListener );
		this.addMouseMotionListener( zoneListener );
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

		InputStream is = null;
		try {
			// The main image is visible and full size.

			ByteReference mainImageRef = hotspotNode.getRawImageContent();
			is = mainImageRef.getInputStream();
			mainImage = ImageIO.read( is );
			is.close();

			// Stretch to fit the main image.
			Dimension mainImageSize = new Dimension( mainImage.getWidth(), mainImage.getHeight() );
			this.setPreferredSize( mainImageSize );
			this.setMinimumSize( mainImageSize );

			for ( int i=0; i < node.getChildCount(); i++ ) {
				UHSNode childNode = node.getChild( i );
				String childType = childNode.getType();

				HotSpot spot = hotspotNode.getSpot( childNode );

				ZoneHolder zoneHolder = new ZoneHolder();
				zoneHolder.zoneRect = new Rectangle( spot.zoneX, spot.zoneY, spot.zoneW, spot.zoneH );
				zoneHolder.title = childNode.getDecoratedStringContent();

				if ( "Overlay".equals( childType ) && childNode instanceof UHSImageNode ) {
					UHSImageNode overlayNode = (UHSImageNode)childNode;

					zoneHolder.imageRef = overlayNode.getRawImageContent();
					if ( zoneHolder.imageRef != null ) {

						is = zoneHolder.imageRef.getInputStream();
						BufferedImage overlayImage = ImageIO.read( is );
						is.close();

						zoneHolder.image = overlayImage;
						zoneHolder.overlayRect = new Rectangle( spot.x, spot.y, overlayImage.getWidth(), overlayImage.getHeight() );

						if ( showAll ) zoneHolder.revealed = true;
					}
				}
				else if ( childNode.isLink() ) {  // Follow the link when clicked.
					zoneHolder.linkTarget = childNode.getLinkTarget();
				}
				else if ( childNode.getId() != -1 ) {  // Visit that child when clicked.
					zoneHolder.linkTarget = childNode.getId();
				}
				else {
					logger.error( "Unexpected {} child of UHSHotSpotNode", childNode.getType() );
				}

				zoneHolders.add( zoneHolder );
			}
		}
		catch ( IOException e ) {
			logger.error( "Error loading binary content within {} node (\"{}\"): {}", hotspotNode.getType(), hotspotNode.getRawStringContent(), e );

			reset();
			return;
		}
		finally {
			try {if ( is != null ) is.close();} catch ( IOException e ) {}
		}

		this.revalidate();
		this.repaint();
	}

	@Override
	public void reset() {
		if ( mainImage != null ) {
			mainImage.flush();
			mainImage = null;
		}

		for ( ZoneHolder zoneHolder : zoneHolders ) {
			if ( zoneHolder.image != null ) {
				zoneHolder.image.flush();
			}
		}
		zoneHolders.clear();

		hotspotNode = null;
		super.reset();

		this.setPreferredSize( null );
		this.setMinimumSize( null );
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


	@Override
	public void paintComponent( Graphics g ) {
		super.paintComponent( g );
		Graphics2D g2d = (Graphics2D)g;

		if ( mainImage != null ) {
			g2d.drawImage( mainImage, 0, 0, null );
		}

		for ( ZoneHolder zoneHolder : zoneHolders ) {
			if ( zoneHolder.imageRef != null && zoneHolder.revealed ) {
				g2d.drawImage( zoneHolder.image, zoneHolder.overlayRect.x, zoneHolder.overlayRect.y, null );
			}
		}

		for ( ZoneHolder zoneHolder : zoneHolders ) {
			Color prevColor = g2d.getColor();
			Stroke prevStroke = g2d.getStroke();

			if ( zoneHolder.imageRef != null ) {
				if ( !zoneHolder.revealed ) {
					g2d.setStroke( zoneStroke );

					g2d.setColor( Color.GRAY );
					g2d.drawRect( zoneHolder.overlayRect.x, zoneHolder.overlayRect.y, zoneHolder.overlayRect.width, zoneHolder.overlayRect.height );

					g2d.setColor( Color.ORANGE );
					g2d.drawRect( zoneHolder.zoneRect.x, zoneHolder.zoneRect.y, zoneHolder.zoneRect.width, zoneHolder.zoneRect.height );
				}
			}
			else if ( zoneHolder.linkTarget != -1 ) {
				g2d.setStroke( zoneStroke );
				g2d.setColor( Color.GREEN );
				g2d.drawRect( zoneHolder.zoneRect.x, zoneHolder.zoneRect.y, zoneHolder.zoneRect.width, zoneHolder.zoneRect.height );
			}
			else {
				g2d.setStroke( zoneStroke );
				g2d.setColor( Color.BLUE );
				g2d.drawRect( zoneHolder.zoneRect.x, zoneHolder.zoneRect.y, zoneHolder.zoneRect.width, zoneHolder.zoneRect.height );
			}

			g2d.setColor( prevColor );
			g2d.setStroke( prevStroke );
		}
	}


	private static class ZoneHolder {
		public Rectangle zoneRect = null;
		public Rectangle overlayRect = null;
		public String title = null;
		public ByteReference imageRef = null;
		public int linkTarget = -1;

		public BufferedImage image = null;

		public boolean revealed = false;
	}
}
