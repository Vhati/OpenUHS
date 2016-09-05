package net.vhati.openuhs.desktopreader.reader;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.event.MouseInputAdapter;
import javax.swing.event.MouseInputListener;

import net.vhati.openuhs.core.HotSpot;
import net.vhati.openuhs.core.UHSHotSpotNode;
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.desktopreader.reader.JScrollablePanel;
import net.vhati.openuhs.desktopreader.reader.UHSReaderNavCtrl;
import net.vhati.openuhs.desktopreader.reader.ZonePanel;


/**
 * A JPanel that displays a node and its children.
 */
public class NodePanel extends JScrollablePanel {
	private NodePanel pronoun = this;
	private UHSNode node = null;
	private UHSReaderNavCtrl navCtrl = null;


	/**
	 * Constructor.
	 *
	 * @param n  the UHSNode to be used
	 * @param c  callback used to replace this panel when a child is clicked
	 * @param showAll  true if all child hints should be revealed, false otherwise
	 */
	public NodePanel( UHSNode n, UHSReaderNavCtrl c, boolean showAll ) {
		node = n;
		navCtrl = c;

		GridBagLayout layoutGridbag = new GridBagLayout();
		GridBagConstraints layoutC = new GridBagConstraints();
			layoutC.fill = GridBagConstraints.HORIZONTAL;
			layoutC.insets = new Insets( 1, 2, 1, 2 );
			layoutC.weightx = 1.0;
			layoutC.weighty = 0;
			layoutC.gridy = 0;
			layoutC.gridwidth = GridBagConstraints.REMAINDER;  //End Row
		pronoun.setLayout( layoutGridbag );

		MouseListener clickListener = new MouseAdapter() {
			@Override
			public void mouseClicked( MouseEvent e ) {
				if ( pronoun.getParent() != null ) {
					JComponent thisComponent = (JComponent)e.getSource();
					JComponent parent = (JComponent)pronoun.getParent();
					for ( int i=0; i < ((JComponent)thisComponent.getParent()).getComponentCount(); i++ ) {
						if ( ((JComponent)thisComponent.getParent()).getComponent( i ).equals( thisComponent ) ) {
							if ( node.getChild( i ).isGroup() ) {
								navCtrl.setReaderNode( node.getChild( i ) );
							}
							else if ( node.getChild( i ).isLink() ) {
								int targetIndex = node.getChild( i ).getLinkTarget();
								navCtrl.setReaderNode( targetIndex );
							}
							break;
						}
					}
				}
			}
		};

		MouseInputListener hotspotListener = new MouseInputAdapter() {
			Cursor zoneCursor = Cursor.getPredefinedCursor( Cursor.HAND_CURSOR );
			Cursor normCursor = Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR );

			private int getZone( UHSHotSpotNode nick, int x, int y ) {
				for ( int i=1; i < nick.getChildCount(); i++ ) {
					HotSpot spot = nick.getSpot( nick.getChild(i) );
					if ( x > spot.zoneX && y > spot.zoneY && x < spot.zoneX+spot.zoneW && y < spot.zoneY+spot.zoneH ) {
						return i;
					}
				}
				return -1;
			}
			@Override
			public void mouseMoved( MouseEvent e ) {
				if ( pronoun.getParent() != null ) {
					JComponent thisComponent = (JComponent)e.getSource();
					UHSHotSpotNode nick = (UHSHotSpotNode)node;

					int x = e.getX(); int y = e.getY();

					if ( getZone( nick, x, y ) != -1 ) thisComponent.setCursor( zoneCursor );
					else thisComponent.setCursor( normCursor );
				}
			}
		};

		MouseListener zoneListener = new MouseInputAdapter() {
			Cursor zoneCursor = Cursor.getPredefinedCursor( Cursor.HAND_CURSOR );
			Cursor normCursor = Cursor.getPredefinedCursor( Cursor.DEFAULT_CURSOR );

			@Override
			public void mouseEntered( MouseEvent e ) {
				ZonePanel thisComponent = (ZonePanel)e.getSource();
				thisComponent.setCursor( zoneCursor );
			}
			@Override
			public void mouseExited( MouseEvent e ) {
				ZonePanel thisComponent = (ZonePanel)e.getSource();
				thisComponent.setCursor( normCursor );
			}
			@Override
			public void mouseClicked( MouseEvent e ) {
				ZonePanel thisComponent = (ZonePanel)e.getSource();
				ZonePanel zoneTarget = thisComponent.getZoneTarget();
				int targetIndex = thisComponent.getLinkTarget();
				if ( zoneTarget != null )
					zoneTarget.setContentsVisible( !zoneTarget.getContentsVisible() );
				else if ( targetIndex != -1 ) {
					navCtrl.setReaderNode( targetIndex );
				}
			}
		};

		boolean allgroup = true;
		if ( node instanceof UHSHotSpotNode ) {
			UHSHotSpotNode nick = (UHSHotSpotNode)node;
			JLayeredPane sharedPanel = new JLayeredPane();

			for ( int i=0; i < node.getChildCount(); i++ ) {
				int childContentType = node.getChild( i ).getContentType();
				if ( childContentType == UHSNode.STRING ) {
					HotSpot spot = nick.getSpot( nick.getChild( i ) );
					ZonePanel spotPanel = new ZonePanel();
						//spotPanel.setToolTipText( (String)(node.getChild( i ).getContent()) );
						spotPanel.setBounds( spot.zoneX, spot.zoneY, spot.zoneW, spot.zoneH );
						sharedPanel.add( spotPanel, JLayeredPane.PALETTE_LAYER, 0 );
						spotPanel.addMouseListener( zoneListener );
					if ( node.getChild( i ).isLink() ) {
						// Follow the link when clicked
						spotPanel.setLinkTarget( node.getChild( i ).getLinkTarget() );
					} else {
						// Switch to that child when clicked
						spotPanel.setLinkTarget( node.getChild( i ).getId() );
					}
				}
				else if ( childContentType == UHSNode.IMAGE ) {
					HotSpot spot = nick.getSpot( nick.getChild( i ) );

					JLabel imageLbl = new JLabel( new ImageIcon( (byte[])(node.getChild( i ).getContent()) ) );

					ZonePanel contentPanel = new ZonePanel( imageLbl );
						Dimension pSize = contentPanel.getPreferredSize();
						contentPanel.setBounds( spot.x, spot.y, pSize.width, pSize.height );
						sharedPanel.add( contentPanel, JLayeredPane.DEFAULT_LAYER, 0 );
					if ( i == 0 ) {
						contentPanel.setContentsVisible( true );
						sharedPanel.setPreferredSize( pSize );
						sharedPanel.setMinimumSize( pSize );
					} else {
						ZonePanel spotPanel = new ZonePanel();
							spotPanel.setZoneTarget( contentPanel );
							spotPanel.setBounds( spot.zoneX, spot.zoneY, spot.zoneW, spot.zoneH );
							sharedPanel.add( spotPanel, JLayeredPane.PALETTE_LAYER, 0 );
							spotPanel.addMouseListener( zoneListener );
					}
				}
			}
			pronoun.add( sharedPanel, layoutC );
			layoutC.gridy++;
		}
		else {
			for ( int i=0; i < node.getChildCount(); i++ ) {
				UHSNode tmpNode = node.getChild( i );
				int contentType = node.getChild( i ).getContentType();

				if ( contentType == UHSNode.STRING ) {
					UHSTextArea tmpUHSArea = new UHSTextArea( tmpNode );
						tmpUHSArea.setEditable( false );
						tmpUHSArea.setBorder( BorderFactory.createEtchedBorder() );
						tmpUHSArea.setVisible( i==0 || showAll );
						pronoun.add( tmpUHSArea, layoutC );
					layoutC.gridy++;
					if ( tmpNode.isGroup() || tmpNode.isLink() ) {
						tmpUHSArea.addMouseListener( clickListener );
					}
					else if ( tmpNode.getType().equals( "Blank" ) == false ) {
						allgroup = false;
					}
				}
				else {
					JComponent tmpComp = null;
					if ( contentType == UHSNode.IMAGE ) {
						tmpComp = new JLabel( new ImageIcon( (byte[])tmpNode.getContent() ) );
					}
					else if ( contentType == UHSNode.AUDIO ) {
						tmpComp = new MinimalSoundPlayer( (byte[])tmpNode.getContent() );
					}
					else {
						tmpComp = new JLabel("^UNKNOWN CONTENT^");
					}
					JPanel tmpPanel = new JPanel();
						tmpPanel.add( tmpComp );
						tmpPanel.setVisible( i==0 || showAll );
						pronoun.add( tmpPanel, layoutC );
					layoutC.gridy++;
				}
			}
		}
		if ( allgroup || showAll ) {
			for ( int i=0; i < pronoun.getComponentCount(); i++ ) {
				((JComponent)pronoun.getComponent( i )).setVisible( true );
			}
			node.setRevealedAmount( node.getChildCount() );
		}
		else {
			for ( int i=0; i < pronoun.getComponentCount() && i < node.getRevealedAmount(); i++ ) {
				((JComponent)pronoun.getComponent( i )).setVisible( true );
			}
		}
		layoutC.weighty = 1;
		//pronoun.add( new JLabel( "" ), layoutC );

		pronoun.revalidate();
		pronoun.repaint();
	}


	/**
	 * Gets the node this panel represents.
	 *
	 * @return the underlying UHSNode
	 */
	public UHSNode getNode() {
		return node;
	}


	/**
	 * Reveals the next child hint.
	 *
	 * @return the new 1-based revealed amount, or -1 if there was no more to see
	 */
	public int showNext() {
		if ( isComplete() ) return -1;
		node.setRevealedAmount( node.getRevealedAmount()+1 );
		int currentRevealedAmount = node.getRevealedAmount();  // Let the node decide how much more was revealed.

		boolean guiChanged = false;
		for ( int i=0; i < this.getComponentCount(); i++ ) {
			boolean reveal = ( i < currentRevealedAmount );
			Component c = this.getComponent( i );
			if ( c.isVisible() != reveal ) {
				c.setVisible( reveal );
				guiChanged = true;
			}
		}
		if ( guiChanged ) {
			pronoun.revalidate();
			pronoun.repaint();
		}
		return currentRevealedAmount;
	}


	/**
	 * Determines whether the child hints have all been revealed.
	 *
	 * @return true if all children are revealed, false otherwise
	 */
	public boolean isComplete() {
		if ( node.getRevealedAmount() == node.getChildCount() ) {
			return true;
		} else {
			return false;
		}
	}
}
