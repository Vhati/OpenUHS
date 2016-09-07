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
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.core.UHSParser;
import net.vhati.openuhs.core.UHSRootNode;
import net.vhati.openuhs.core.markup.DecoratedFragment;
import net.vhati.openuhs.desktopreader.AppliablePanel;
import net.vhati.openuhs.desktopreader.Nerfable;
import net.vhati.openuhs.desktopreader.reader.JScrollablePanel;
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
 * UHSParser uhsParser = new UHSParser();
 * UHSRootNode rootNode = uhsParser.parseFile(new File("./hints/somefile.uhs"));
 *
 * if (rootNode != null) readerPanel.setUHSNodes(rootNode, rootNode);
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

	private NodePanel currentNodePanel = null;
	private JScrollPane centerScroll = null;
	private JScrollablePanel centerScrollView = new JScrollablePanel( new BorderLayout() );

	private List<UHSNode> historyArray = new ArrayList<UHSNode>();
	private List<UHSNode> futureArray = new ArrayList<UHSNode>();
	private JButton openBtn = null;
	private JButton backBtn = null;
	private JButton forwardBtn = null;
	private JButton findBtn = null;

	private JLabel questionLbl = null;
	private JLabel showLbl = null;
	private JButton showNextBtn = null;
	private JCheckBox showAllBox = null;

	private File hintsDir = new File( "." );


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

			JPanel questionPanel = new JPanel();
				questionPanel.setLayout( new BoxLayout( questionPanel, BoxLayout.X_AXIS ) );
				questionLbl = new JLabel( "" );
					questionPanel.add( questionLbl );
				topPanel.add( questionPanel, BorderLayout.SOUTH );


		JPanel centerPanel = new JPanel( new BorderLayout() );
			centerScroll = new JScrollPane( centerScrollView, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER );
				//The default scroll speed is too slow
				centerScroll.getHorizontalScrollBar().setUnitIncrement( 25 );
				centerScroll.getVerticalScrollBar().setUnitIncrement( 25 );
				centerPanel.add( centerScroll );


		JPanel showPanel = new JPanel( new GridLayout( 0, 3 ) );
			showLbl = new JLabel( "" );
				showLbl.setHorizontalAlignment( SwingConstants.CENTER );
				showPanel.add( showLbl );

			JPanel showNextHolderPanel = new JPanel( new GridBagLayout() );
				showNextBtn = new JButton( "V" );
					showNextBtn.setEnabled( false );
					showNextBtn.setPreferredSize( new Dimension( showNextBtn.getPreferredSize().width*2, showNextBtn.getPreferredSize().height ) );
					showNextHolderPanel.add( showNextBtn );
				showPanel.add( showNextHolderPanel );

			showAllBox = new JCheckBox( "Show All" );
				showAllBox.setEnabled( true );
				showAllBox.setHorizontalAlignment( SwingConstants.CENTER );
				//showAllBox.setMaximumSize( showAllBox.getPreferredSize() );
				showPanel.add( showAllBox );

		openBtn.addActionListener( this );
		backBtn.addActionListener( this );
		forwardBtn.addActionListener( this );
		findBtn.addActionListener( this );
		showNextBtn.addActionListener( this );
		showAllBox.addActionListener( this );


		this.add( topPanel, BorderLayout.NORTH );
		this.add( centerPanel, BorderLayout.CENTER );
		this.add( showPanel, BorderLayout.SOUTH );

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

			UHSNode newNode = new UHSNode( "result" );
			newNode.setContent( "Search Results for \""+ tmpString +"\"", UHSNode.STRING );
			searchNode( newNode, "", 0, (UHSNode)rootNode, tmpString.toLowerCase() );
			setReaderNode( newNode );
		}
		else if ( source == showNextBtn ) {
			showNext();
			scrollTo( SCROLL_TO_BOTTOM );
			scrollTo( SCROLL_TO_BOTTOM );
		}
		else if ( source == showAllBox ) {
			if( !showAllBox.isSelected() ) return;

			while ( showNext() != false );
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
	 * Clears everything.
	 */
	public void reset() {
		historyArray.clear();
		futureArray.clear();
		backBtn.setEnabled( false );
		forwardBtn.setEnabled( false );
		findBtn.setEnabled( false );

		showLbl.setText( "" );
		showNextBtn.setEnabled( false );

		rootNode = null;
		currentNode = null;

		centerScrollView.removeAll();
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
						rootNode = uhsParser.parseFile( f, UHSParser.AUX_NEST );
					}
					else if ( f.getName().matches( "(?i).*[.]puhs" ) ) {
						Proto4xUHSParser protoParser = new Proto4xUHSParser();
						rootNode = protoParser.parseFile( f, Proto4xUHSParser.AUX_NEST );
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
							setUHSNodes( finalRootNode, finalRootNode );
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
	 * @param inCurrentNode  the new initial node
	 * @param inRootNode  the new root node
	 */
	public void setUHSNodes( UHSNode inCurrentNode, UHSRootNode inRootNode ) {
		reset();
		rootNode = inRootNode;
		findBtn.setEnabled( true );
		setReaderNode( inCurrentNode );
		System.gc();
	}


	/**
	 * Displays a new node within the current tree.
	 * <p>If the node is the same as the next/prev one, breadcrumbs will be traversed.</p>
	 *
	 * @param newNode  the new node
	 */
	@Override
	public void setReaderNode( UHSNode newNode ) {
		if ( newNode == null ) {return;}

		int matchesNextPrev = 0; // -1 prev, 1 next, 0 neither.

		if ( historyArray.size() > 0 && historyArray.get( historyArray.size()-1 ).equals( newNode ) ) {
			matchesNextPrev = -1;
		}
		else if ( futureArray.size() > 0 && futureArray.get( futureArray.size()-1 ).equals( newNode ) ) {
			matchesNextPrev = 1;
		}


		if ( matchesNextPrev == -1 ) {
			// Move one node into the past.
			historyArray.remove( historyArray.size()-1 );
			if ( currentNode != null ) {
				// Leave a breadcrumb before changing to the new node.
				futureArray.add( currentNode );
			}
		}
		else if ( matchesNextPrev == 1 ) {
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
		backBtn.setEnabled( (historyArray.size() > 0) );
		forwardBtn.setEnabled( (futureArray.size() > 0) );


		currentNode = newNode;
		boolean showAll = false;

		if ( currentNode.equals(rootNode) ) {
			questionLbl.setText( "" );
			setReaderTitle( (String)currentNode.getContent() );
			showAll = true;
		}
		else {
			if ( currentNode.getContentType() == UHSNode.STRING ) {
				StringBuffer questionBuf = new StringBuffer();
				questionBuf.append( currentNode.getType() ).append( "=" );

				if ( currentNode.getStringContentDecorator() != null ) {
					DecoratedFragment[] fragments = currentNode.getDecoratedStringContent();
					for ( int i=0; i < fragments.length; i++ ) {
						questionBuf.append( fragments[i].fragment );
					}
				}
				else {
					questionBuf.append( (String)currentNode.getContent() );
				}
				questionLbl.setText( questionBuf.toString() );
			}
			else {
				questionLbl.setText( "" );
			}
			showAll = showAllBox.isSelected();
		}
		centerScrollView.removeAll();
		currentNodePanel = new NodePanel( currentNode, this, showAll );
		centerScrollView.add( currentNodePanel );

		scrollTo( SCROLL_IF_INCOMPLETE );

		boolean complete = currentNodePanel.isComplete();
		showLbl.setText( (( complete ) ? currentNode.getChildCount() : currentNode.getRevealedAmount()) +"/"+ currentNode.getChildCount() );
		showNextBtn.setEnabled( !complete );

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
		UHSNode tmpNode = rootNode.getLink( id );
		if ( tmpNode != null ) {
			setReaderNode( tmpNode );
		} else {
			JOptionPane.showMessageDialog( this, "Could not find link target: "+ id, "OpenUHS Cannot Continue", JOptionPane.ERROR_MESSAGE );
		}
	}


	/**
	 * Sets the reader's title to the specified string.
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

	/**
	 * Gets the title of the reader.
	 *
	 * @return the title of the reader
	 */
	@Override
	public String getReaderTitle() {
		return readerTitle;
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
	 * @param input  the phrase to search for
	 */
	public void searchNode( UHSNode resultsNode, String prefix, int depth, UHSNode currentNode, String input ) {
		if ( input == null || input.length() == 0 ) return;
		// Assuming input is lower case because toLowering it here would be wasteful.

		if ( currentNode.getContentType() == UHSNode.STRING ) {
			prefix = (( depth > 1 ) ? prefix+" : " : "") + currentNode.getContent();
		} else {
			prefix = (( depth > 1 ) ? prefix+" : " : "") + "???";
		}

		depth++;
		UHSNode tmpNode = null;
		UHSNode newNode = null;
		boolean beenListed = false;
		for ( int i=0; i < currentNode.getChildCount(); i++ ) {
			tmpNode = currentNode.getChild( i );
			if ( tmpNode.getContentType() == UHSNode.STRING && beenListed == false ) {
				if ( ((String)tmpNode.getContent()).toLowerCase().indexOf( input ) != -1 ) {
					newNode = new UHSNode( "result" );
					newNode.setContent( prefix, UHSNode.STRING );
					newNode.setChildren( currentNode.getChildren() );
					resultsNode.addChild( newNode );
					beenListed = true;
				}
			}
			searchNode( resultsNode, prefix, depth, currentNode.getChild( i ), input );
		}
	}


	/**
	 * Reveals the next hint of the current node panel.
	 *
	 * @return true if successful, false otherwise
	 */
	public boolean showNext() {
		if ( currentNodePanel == null ) return false;

		int revealed = currentNodePanel.showNext();
		if ( revealed == -1 ) {
			showNextBtn.setEnabled( false );
			return false;
		}
		else {
			int hintCount = currentNodePanel.getNode().getChildCount();
			if ( revealed == hintCount ) showNextBtn.setEnabled( false );
			showLbl.setText( revealed +"/"+ hintCount);
			return true;
		}
	}


	/**
	 * Scrolls to the top/bottom of the visible hints.
	 *
	 * <p>This enqueues a thread to do the scrolling at the next opportunity.</p>
	 *
	 * <p>SCROLL_IF_INCOMPLETE option defers completeness checking until the thread runs: top if true, bottom otherwise.</p>
	 *
	 * <p>The threading and yielding is a workaround for JScrollPane goofiness when the content grows.</p>
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
					centerScroll.getViewport().setViewPosition( new Point(0, 0) );
				}
			});
		}
		else if ( position == SCROLL_TO_BOTTOM ) {
			SwingUtilities.invokeLater(new Runnable() {
				@Override
				public void run() {
					// Wait some more.
					Thread.yield();
					int areaHeight = centerScrollView.getPreferredSize().height;
					int viewHeight = centerScroll.getViewport().getExtentSize().height;
					if (areaHeight > viewHeight)
					centerScroll.getViewport().setViewPosition( new Point( 0, areaHeight-viewHeight ) );
				}
			});
		}
	}


	public AppliablePanel getSettingsPanel() {
		GridBagConstraints gridC = new GridBagConstraints();

		AppliablePanel result = new AppliablePanel( new GridBagLayout() );

			gridC.gridy = 0;
			gridC.gridx = 0;
			gridC.gridwidth = 1;
			gridC.weightx = 0.0;
			gridC.fill = GridBagConstraints.NONE;
			gridC.anchor = GridBagConstraints.WEST;
			JLabel textSizeLbl = new JLabel( "Text Size" );
				result.add( textSizeLbl, gridC );
			gridC.gridx++;
			gridC.gridwidth = GridBagConstraints.REMAINDER;
			gridC.weightx = 1.0;
			gridC.fill = GridBagConstraints.NONE;
			gridC.anchor = GridBagConstraints.EAST;
			final JTextField textSizeField = new JTextField( "222" );
				textSizeField.setHorizontalAlignment( JTextField.RIGHT );
				textSizeField.setPreferredSize( textSizeField.getPreferredSize() );
				textSizeField.setText( "" );
				result.add( textSizeField, gridC );

		Style regularStyle = UHSTextArea.DEFAULT_STYLES.getStyle( "regular" );
		if ( regularStyle != null ) {
			textSizeField.setText( StyleConstants.getFontSize( regularStyle ) +"" );
		} else {
			textSizeField.setText( "" );
		}

		Runnable applyAction = new Runnable() {
			@Override
			public void run() {
				try {
					int newSize = Integer.parseInt( textSizeField.getText() );
					if ( newSize > 0 ) {
						Style baseStyle = UHSTextArea.DEFAULT_STYLES.getStyle( "base" );
						StyleConstants.setFontSize( baseStyle, newSize );
					}
				}
				catch ( NumberFormatException e ) {}
			}
		};
		result.setApplyAction( applyAction );

		return result;
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
