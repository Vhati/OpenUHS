package net.vhati.openuhs.desktopreader.reader;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.Proto4xUHSParser;
import net.vhati.openuhs.core.UHSAudioNode;
import net.vhati.openuhs.core.UHSHotSpotNode;
import net.vhati.openuhs.core.UHSImageNode;
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.core.UHSParser;
import net.vhati.openuhs.core.UHSRootNode;
import net.vhati.openuhs.desktopreader.Nerfable;
import net.vhati.openuhs.desktopreader.reader.NodePanel;
import net.vhati.openuhs.desktopreader.reader.UHSReaderNavCtrl;
import net.vhati.openuhs.desktopreader.reader.UHSTextArea;


/**
 * A panel to navigate UHS nodes.
 *
 * Typical usage...
 * <blockquote><pre>
 * <code>
 * import org.openuhs.core.*;
 * import org.openuhs.reader.*;
 *
 * UHSReaderPanel readerPanel = new UHSReaderPanel();
 * readerPanel.registerNodePanel(new DefaultNodePanel());
 *
 * UHSParser uhsParser = new UHSParser();
 * UHSRootNode rootNode = uhsParser.parseFile(new File("./hints/somefile.uhs"));
 *
 * if (rootNode != null) readerPanel.setReaderRootNode(rootNode);
 * </code>
 * </pre></blockquote>
 */
public class UHSReaderPanel extends JPanel implements UHSReaderNavCtrl, ActionListener {
	public static final int SCROLL_TO_TOP = 0;
	public static final int SCROLL_TO_BOTTOM = 1;
	public static final int SCROLL_IF_INCOMPLETE = 2;


	private final Logger logger = LoggerFactory.getLogger( UHSReaderPanel.class );

	private String readerTitle = "";

	private UHSRootNode rootNode = null;
	private UHSNode currentNode = null;

	private List<NodePanel> nodePanelRegistry = new ArrayList<NodePanel>();

	private NodePanel currentNodePanel = null;

	private JScrollPane centerScroll = null;

	private List<UHSNode> historyArray = new ArrayList<UHSNode>();
	private List<UHSNode> futureArray = new ArrayList<UHSNode>();
	private JButton openBtn = null;
	private JButton backBtn = null;
	private JButton forwardBtn = null;
	private JButton findBtn = null;

	private JLabel nodeTitleLbl = null;
	private JLabel revealedLbl = null;
	private JButton revealNextBtn = null;
	private JCheckBox showAllBox = null;

	private File hintsDir = new File( "./" );


	public UHSReaderPanel() {
		super( new BorderLayout() );

		GridBagConstraints gridC = new GridBagConstraints();

		JPanel topPanel = new JPanel( new BorderLayout() );
			JPanel ctrlPanel = new JPanel( new GridLayout( 0, 3 ) );

				JPanel ctrlLeftPanel = new JPanel( new GridBagLayout() );
					gridC.gridx = 0;
					gridC.weightx = 1.0;
					gridC.fill = GridBagConstraints.NONE;
					gridC.anchor = GridBagConstraints.WEST;
					openBtn = new JButton( "Open..." );
						ctrlLeftPanel.add( openBtn, gridC );
				ctrlPanel.add( ctrlLeftPanel );

				JPanel ctrlCenterPanel = new JPanel( new GridBagLayout() );
					gridC.gridx = 0;
					gridC.weightx = 1.0;
					gridC.fill = GridBagConstraints.NONE;
					gridC.anchor = GridBagConstraints.CENTER;
					backBtn = new JButton( "<" );
						backBtn.setEnabled( false );
						ctrlCenterPanel.add( backBtn, gridC );
					gridC.gridx++;
					forwardBtn = new JButton( ">" );
						forwardBtn.setEnabled( false );
						ctrlCenterPanel.add( forwardBtn, gridC );
					ctrlPanel.add( ctrlCenterPanel );

				JPanel ctrlRightPanel = new JPanel( new GridBagLayout() );
					gridC.gridx = 0;
					gridC.weightx = 1.0;
					gridC.fill = GridBagConstraints.NONE;
					gridC.anchor = GridBagConstraints.EAST;
					findBtn = new JButton( "Find..." );
						ctrlRightPanel.add( findBtn, gridC );
					ctrlPanel.add( ctrlRightPanel );

				topPanel.add( ctrlPanel, BorderLayout.CENTER );

			JPanel nodeTitlePanel = new JPanel();
				nodeTitlePanel.setLayout( new BoxLayout( nodeTitlePanel, BoxLayout.X_AXIS ) );
				nodeTitleLbl = new JLabel( "" );
					nodeTitlePanel.add( nodeTitleLbl );
				topPanel.add( nodeTitlePanel, BorderLayout.SOUTH );

		JPanel centerPanel = new JPanel( new BorderLayout() );
			centerScroll = new JScrollPane();
				centerScroll.setVerticalScrollBarPolicy( JScrollPane.VERTICAL_SCROLLBAR_ALWAYS );
				centerScroll.setHorizontalScrollBarPolicy( JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED );
				centerPanel.add( centerScroll );

		JPanel revealPanel = new JPanel( new GridLayout( 0, 3 ) );
			revealedLbl = new JLabel( "" );
				revealedLbl.setHorizontalAlignment( SwingConstants.CENTER );
				revealPanel.add( revealedLbl );

			JPanel revealNextHolderPanel = new JPanel( new GridBagLayout() );
				revealNextBtn = new JButton( "V" );
					revealNextBtn.setEnabled( false );
					revealNextBtn.setPreferredSize( new Dimension( revealNextBtn.getPreferredSize().width*2, revealNextBtn.getPreferredSize().height ) );
					revealNextHolderPanel.add( revealNextBtn );
				revealPanel.add( revealNextHolderPanel );

			showAllBox = new JCheckBox( "Show All" );
				showAllBox.setEnabled( true );
				showAllBox.setHorizontalAlignment( SwingConstants.CENTER );
				//showAllBox.setMaximumSize( showAllBox.getPreferredSize() );
				revealPanel.add( showAllBox );

		openBtn.addActionListener( this );
		backBtn.addActionListener( this );
		forwardBtn.addActionListener( this );
		findBtn.addActionListener( this );
		revealNextBtn.addActionListener( this );
		showAllBox.addActionListener( this );


		this.add( topPanel, BorderLayout.NORTH );
		this.add( centerPanel, BorderLayout.CENTER );
		this.add( revealPanel, BorderLayout.SOUTH );

		reset();
	}


	@Override
	public void actionPerformed( ActionEvent e ) {
		Object source = e.getSource();

		if ( source == openBtn ) {
			FileFilter uhsFilter = new FileFilter() {
				@Override
				public String getDescription() {return "UHS Files (*.uhs)";}

				@Override
				public boolean accept( File f ) {
					return (f.isDirectory() || f.getName().toLowerCase().endsWith( ".uhs" ));
				}
			};
			FileFilter puhsFilter = new FileFilter() {
				@Override
				public String getDescription() {return "Proto UHS Files (*.puhs)";}

				@Override
				public boolean accept( File f ) {
					return (f.isDirectory() || f.getName().toLowerCase().endsWith( ".puhs" ));
				}
			};

			JFileChooser chooser = new JFileChooser( hintsDir );
				chooser.addChoosableFileFilter( uhsFilter );
				chooser.addChoosableFileFilter( puhsFilter );
				chooser.setFileFilter( uhsFilter );

			int status = chooser.showOpenDialog( this );
			if ( status == 0 ) {
				openFile( chooser.getSelectedFile() );
			}
		}
		else if ( source == backBtn ) {
			setReaderNode( historyArray.get( historyArray.size()-1 ) );
		}
		else if ( source == forwardBtn ) {
			setReaderNode( futureArray.get( futureArray.size()-1 ) );
		}
		else if ( source == findBtn ) {
			String tmpString = JOptionPane.showInputDialog( this, "Find what?", "Find text", JOptionPane.QUESTION_MESSAGE );
			if ( tmpString == null || tmpString.length() == 0 ) return;

			UHSNode newNode = new UHSNode( "Result" );
			newNode.setRawStringContent( "Search Results for \""+ tmpString +"\"" );
			searchNode( newNode, "", 0, rootNode, tmpString.toLowerCase() );
			setReaderNode( newNode );
		}
		else if ( source == revealNextBtn ) {
			revealNext();
			scrollTo( SCROLL_TO_BOTTOM );
			scrollTo( SCROLL_TO_BOTTOM );
		}
		else if ( source == showAllBox ) {
			if( !showAllBox.isSelected() ) return;

			while ( revealNext() != false );
		}
	}


	/**
	 * Sets the dir in which to look for UHS files.
	 *
	 * The default path is "."
	 */
	public void setHintsDir( File d ) {
		hintsDir = d;
	}

	/**
	 * Returns the dir in which to look for UHS files.
	 */
	public File getHintsDir() {
		return hintsDir;
	}


	/**
	 * Registers a reusable NodePanel to handle a UHSNode class (and its subclasses).
	 *
	 * <p>When needed, panels will be searched (in reverse order of
	 * registration) for the first one whose accept() method returns true.</p>
	 *
	 * <p>Registered panels determine the result of isNodeVisitable().</p>
	 *
	 * @see isNodeVisitable(UHSNode)
	 * @see net.vhati.openuhs.desktopreader.reader.NodePanel.accept(UHSNode)
	 */
	public void registerNodePanel( NodePanel nodePanel ) {
		nodePanelRegistry.add( nodePanel );
	}

	/**
	 * Returns a previously registered NodePanel capable of representing a UHSNode, or null.
	 *
	 * @see #registerNodePanel(NodePanel)
	 */
	protected NodePanel getPanelForNode( UHSNode node ) {
		for ( int i=nodePanelRegistry.size()-1; i >= 0 ; i-- ) {
			NodePanel p = nodePanelRegistry.get( i );
			if ( p.accept( node ) ) return p;
		}
		return null;
	}


	/**
	 * Resets this panel.
	 */
	public void reset() {
		historyArray.clear();
		futureArray.clear();
		backBtn.setEnabled( false );
		forwardBtn.setEnabled( false );
		findBtn.setEnabled( false );

		revealedLbl.setText( "" );
		revealNextBtn.setEnabled( false );

		rootNode = null;
		currentNode = null;

		centerScroll.setViewportView( null );
		if ( currentNodePanel != null ) currentNodePanel.reset();
		currentNodePanel = null;

		setReaderTitle( null );
	}


	/**
	 * Opens a UHS file.
	 *
	 * @param f  the location of the file
	 */
	public void openFile( final File f ) {
		ancestorSetNerfed( true );

		logger.info( "Opened {}", f.getName() );

		Thread parseWorker = new Thread() {
			public void run() {
				UHSRootNode rootNode = null;
				try {
					if ( f.getName().matches( "(?i).*[.]uhs$" ) ) {
						UHSParser uhsParser = new UHSParser();
						rootNode = uhsParser.parseFile( f );
					}
					else if ( f.getName().matches( "(?i).*[.]puhs" ) ) {
						Proto4xUHSParser protoParser = new Proto4xUHSParser();
						rootNode = protoParser.parseFile( f );
					}
				}
				catch ( IOException e ) {
					logger.error( "Unreadable file or parsing error", e );
				}

				final UHSRootNode finalRootNode = rootNode;
				// Back to the event thread...
				Runnable r = new Runnable() {
					@Override
					public void run() {
						if ( finalRootNode != null ) {
							setReaderRootNode( finalRootNode );
						} else {
							JOptionPane.showMessageDialog( UHSReaderPanel.this, "Unreadable file or parsing error", "OpenUHS Cannot Continue", JOptionPane.ERROR_MESSAGE );
						}
						ancestorSetNerfed( false );
					}
				};
				SwingUtilities.invokeLater( r );
			}
		};

		parseWorker.start();
	}


	/**
	 * Displays a new UHS tree.
	 *
	 * @param newRootNode  the new root node
	 */
	public void setReaderRootNode( UHSRootNode newRootNode ) {
		reset();
		rootNode = newRootNode;
		findBtn.setEnabled( true );
		setReaderNode( rootNode );

		String title = rootNode.getUHSTitle();
		setReaderTitle( (( title != null ) ? title : "") );

		System.gc();
	}


	/**
	 * Displays a new node within the current tree.
	 * 
	 * <p>If the new node is the same as the next/previous one, breadcrumbs
	 * will be traversed.</p>
	 *
	 * @param newNode  the new node
	 */
	@Override
	public void setReaderNode( UHSNode newNode ) {
		if ( newNode == null ) return;

		NodePanel newNodePanel = getPanelForNode( newNode );
		if ( newNodePanel == null ) {
			JOptionPane.showMessageDialog( this, "That node is not supported by this reader.", "OpenUHS Cannot Continue", JOptionPane.ERROR_MESSAGE );
			logger.error( "The reader has no registered NodePanels for node ({}) with content: {}", newNode.getClass().getCanonicalName(), newNode.getPrintableContent() );
			return;
		}
		else {
			logger.debug( "Setting reader's node panel to: {}", newNodePanel.getClass().getCanonicalName() );
		}

		if ( !historyArray.isEmpty() && historyArray.get( historyArray.size()-1 ).equals( newNode ) ) {
			// Move one node into the past.
			historyArray.remove( historyArray.size()-1 );
			if ( currentNode != null ) {
				// Leave a breadcrumb before changing to the new node.
				futureArray.add( currentNode );
			}
		}
		else if ( !futureArray.isEmpty() && futureArray.get( futureArray.size()-1 ).equals( newNode ) ) {
			// Move one node into the future.
			futureArray.remove( futureArray.size()-1 );
			if ( currentNode != null ) {
				// Leave a breadcrumb before changing to the new node.
				historyArray.add( currentNode );
			}
		}
		else {
			if ( currentNode != null ) {
				// Leave a breadcrumb before changing to the new node.
				historyArray.add( currentNode );
			}
			// Wipe the future.
			futureArray.clear();
		}
		backBtn.setEnabled( !historyArray.isEmpty() );
		forwardBtn.setEnabled( !futureArray.isEmpty() );

		currentNode = newNode;
		boolean showAll = showAllBox.isSelected();

		if ( currentNodePanel != null ) currentNodePanel.reset();

		if ( currentNodePanel != newNodePanel ) {
			currentNodePanel = newNodePanel;
			currentNodePanel.setNavCtrl( this );
			centerScroll.setViewportView( currentNodePanel );
		}
		currentNodePanel.setNode( currentNode, showAll );

		nodeTitleLbl.setText( currentNodePanel.getTitle() );

		scrollTo( SCROLL_IF_INCOMPLETE );

		if ( currentNodePanel.isRevealSupported() ) {
			revealedLbl.setText( currentNode.getCurrentReveal() +"/"+ currentNode.getMaximumReveal() );
		} else {
			revealedLbl.setText( "" );
		}
		revealNextBtn.setEnabled( !currentNodePanel.isComplete() );

		this.validate();
		this.repaint();
	}


	/**
	 * Displays a new node within the current tree.
	 *
	 * <p>Nothing will happen if the ID isn't among the root node's list of link targets.</p>
	 *
	 * @param id  the id of the new node
	 */
	@Override
	public void setReaderNode( int id ) {
		UHSNode tmpNode = rootNode.getNodeByLinkId( id );
		if ( tmpNode != null ) {
			setReaderNode( tmpNode );
		} else {
			JOptionPane.showMessageDialog( this, "Could not find link target: "+ id, "OpenUHS Cannot Continue", JOptionPane.ERROR_MESSAGE );
		}
	}


	/**
	 * Sets the reader's title.
	 *
	 * @param s  a title (null is treated as "")
	 */
	@Override
	public void setReaderTitle( String s ) {
		readerTitle = (( s != null ) ? s : "");

		Object ancestor = getTopLevelAncestor();
		if ( ancestor != null && ancestor instanceof Frame ) {
			((Frame)ancestor).setTitle( readerTitle );
		}
	}

	@Override
	public String getReaderTitle() {
		return readerTitle;
	}

	@Override
	public boolean isNodeVisitable( UHSNode node ) {
		return ( getPanelForNode( node ) != null );
	}


	/**
	 * Recursively searches for a phrase within children of a node.
	 *
	 * <p>This'll go into an infinite loop if two nodes have each other as children.
	 * Luckily real UHS files aren't structured that way.
	 * Link targets don't count as children.</p>
	 *
	 * @param resultsNode  an existing temporary node to add results to
	 * @param prefix  phrase to prepend to result titles (use "")
	 * @param depth  recursion level reminder (use 0)
	 * @param input  the phrase to search for (must be lowercase)
	 */
	public void searchNode( UHSNode resultsNode, String prefix, int depth, UHSNode currentNode, String input ) {
		if ( input == null || input.length() == 0 ) return;
		// Assuming input is lower case because toLowering it here would be wasteful.

		prefix = (( depth > 1 ) ? prefix+" : " : "") + currentNode.getDecoratedStringContent();

		depth++;
		boolean beenListed = false;
		for ( int i=0; i < currentNode.getChildCount(); i++ ) {
			UHSNode tmpNode = currentNode.getChild( i );

			if ( beenListed == false ) {
				if ( tmpNode.getDecoratedStringContent().toLowerCase().indexOf( input ) != -1 ) {
					UHSNode newNode = new UHSNode( "Result" );
					newNode.setRawStringContent( prefix );
					newNode.setChildren( currentNode.getChildren() );
					resultsNode.addChild( newNode );
					beenListed = true;
				}
			}
			searchNode( resultsNode, prefix, depth, tmpNode, input );
		}
	}


	/**
	 * Reveals the next hint of the current node panel.
	 *
	 * @return true if successful, false otherwise
	 */
	public boolean revealNext() {
		if ( currentNodePanel == null ) return false;
		if ( currentNodePanel.isComplete() ) return false;

		currentNodePanel.revealNext();

		revealedLbl.setText( currentNodePanel.getCurrentReveal() +"/"+ currentNodePanel.getMaximumReveal() );
		revealNextBtn.setEnabled( !currentNodePanel.isComplete() );

		return true;
	}


	/**
	 * Scrolls to the top/bottom of the visible hints.
	 *
	 * <p>This schedules a Runnable to do the scrolling at the next opportunity.</p>
	 *
	 * <p>SCROLL_IF_INCOMPLETE option defers completeness checking until the thread runs: top if true, bottom otherwise.</p>
	 *
	 * <p>The thread yielding is a workaround for JScrollPane goofiness when the content grows.</p>
	 *
	 * @param position  one of: SCROLL_TO_TOP, SCROLL_TO_BOTTOM, or SCROLL_IF_INCOMPLETE
	 * @see SwingUtilities#invokeLater(Runnable)
	 * @see Thread#yield()
	 */
	public void scrollTo( int position ) {
		// Wait a bit longer.
		Thread.yield();

		if ( position == SCROLL_IF_INCOMPLETE ) {
			if ( currentNodePanel.isComplete() ) position = SCROLL_TO_TOP;
			else position = SCROLL_TO_BOTTOM;
		}
		if ( position == SCROLL_TO_TOP ) {
			SwingUtilities.invokeLater( new Runnable() {
				@Override
				public void run() {
					centerScroll.getViewport().setViewPosition( new Point( 0, 0 ) );
				}
			});
		}
		else if ( position == SCROLL_TO_BOTTOM ) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					// Wait some more.
					Thread.yield();
					int viewHeight = centerScroll.getViewport().getViewSize().height;
					int visibleHeight = centerScroll.getViewport().getExtentSize().height;
					if ( viewHeight > visibleHeight ) {
						centerScroll.getViewport().setViewPosition( new Point( 0, viewHeight-visibleHeight ) );
					}
				}
			});
		}
	}


	/**
	 * Sets the font size for all UHSTextAreas.
	 */
	public void setFontSize( int newSize ) {
		if ( newSize <= 0 ) throw new IllegalArgumentException( String.format( "Invalid font size: %d", newSize ) );

		Style baseStyle = UHSTextArea.DEFAULT_STYLES.getStyle( "base" );
		StyleConstants.setFontSize( baseStyle, newSize );
	}


	/**
	 * Calls setNerfed on the top-level ancestor, if nerfable.
	 *
	 * A dedicated method was easier than passing the ancestor to runnables.
	 */
	private void ancestorSetNerfed( boolean b ) {
		boolean nerfable = false;
		Component parentComponent = null;
		Object ancestor = this.getTopLevelAncestor();
		if ( ancestor != null ) {
			if ( ancestor instanceof Nerfable ) nerfable = true;
			if ( ancestor instanceof Component ) parentComponent = (Component)ancestor;
		}

		if (nerfable) ((Nerfable)ancestor).setNerfed( b );
	}
}
