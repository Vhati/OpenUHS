package net.vhati.openuhs.desktopreader;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.downloader.CatalogItem;
import net.vhati.openuhs.desktopreader.Nerfable;
import net.vhati.openuhs.desktopreader.UHSReaderAboutPanel;
import net.vhati.openuhs.desktopreader.UHSReaderConfig;
import net.vhati.openuhs.desktopreader.UHSReaderConfigPanel;
import net.vhati.openuhs.desktopreader.downloader.CatalogTableModel;
import net.vhati.openuhs.desktopreader.downloader.UHSDownloaderPanel;
import net.vhati.openuhs.desktopreader.reader.AudioNodePanel;
import net.vhati.openuhs.desktopreader.reader.DefaultNodePanel;
import net.vhati.openuhs.desktopreader.reader.HotSpotNodePanel;
import net.vhati.openuhs.desktopreader.reader.ImageNodePanel;
import net.vhati.openuhs.desktopreader.reader.RootNodePanel;
import net.vhati.openuhs.desktopreader.reader.UHSReaderPanel;


public class UHSReaderFrame extends JFrame implements Nerfable {

	private final Logger logger = LoggerFactory.getLogger( UHSReaderFrame.class );

	private File appDir = new File( "./" );
	private File appDataDir = new File( "./" );
	private File userDataDir = new File( "./" );
	private File hintsDir = new File( userDataDir, "hints" );

	private UHSReaderConfig appConfig = null;

	private String titlePrefix = "";
	private UHSReaderPanel readerPanel = new UHSReaderPanel();
	private UHSDownloaderPanel downloaderPanel = new UHSDownloaderPanel();
	private UHSReaderConfigPanel configPanel = new UHSReaderConfigPanel();
	private UHSReaderAboutPanel aboutPanel = new UHSReaderAboutPanel();


	public UHSReaderFrame( UHSReaderConfig appConfig ) {
		super();
		this.appConfig = appConfig;

		JPanel pane = new JPanel( new BorderLayout() );

		final JTabbedPane tabbedPane = new JTabbedPane();
		readerPanel.setHintsDir( hintsDir );
		readerPanel.registerNodePanel( new DefaultNodePanel() );
		readerPanel.registerNodePanel( new AudioNodePanel() );
		readerPanel.registerNodePanel( new ImageNodePanel() );
		readerPanel.registerNodePanel( new HotSpotNodePanel() );
		readerPanel.registerNodePanel( new RootNodePanel() );
		tabbedPane.add( readerPanel, "Reader" );

		downloaderPanel.setHintsDir( hintsDir );
		tabbedPane.add( downloaderPanel, "Downloader" );

		tabbedPane.add( configPanel, "Settings" );

		tabbedPane.add( aboutPanel, "About" );

		readerPanel.addAncestorListener(new AncestorListener() {
			@Override
			public void ancestorAdded( AncestorEvent event ) {
				UHSReaderFrame.this.setTitle( readerPanel.getReaderTitle() );
			}

			@Override
			public void ancestorMoved( AncestorEvent event ) {
			}

			@Override
			public void ancestorRemoved( AncestorEvent event ) {
				UHSReaderFrame.this.setTitle( null );
			}
		});

		downloaderPanel.getCatalogTable().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked( MouseEvent e ) {
				JTable catalogTable = (JTable)e.getSource();
				if ( catalogTable.getRowCount() == 0 ) return;

				if ( e.getClickCount() == 2 ) {
					int row = catalogTable.getSelectedRow();
					if ( row == -1 ) return;
					CatalogItem catItem = ((CatalogTableModel)catalogTable.getModel()).getUHS( row );
					File uhsFile = new File( hintsDir, catItem.getName() );
					if ( uhsFile.exists() ) {
						tabbedPane.setSelectedIndex( tabbedPane.indexOfTab( "Reader" ) );
						readerPanel.openFile( uhsFile );
					}
				}
			}
		});

		configPanel.addAncestorListener(new AncestorListener() {
			@Override
			public void ancestorAdded( AncestorEvent event ) {
				configPanel.setConfig( UHSReaderFrame.this.appConfig );
			}

			@Override
			public void ancestorMoved( AncestorEvent event ) {
			}

			@Override
			public void ancestorRemoved( AncestorEvent event ) {
				configPanel.setConfig( null );
			}
		});

		configPanel.setApplyCallback(new Runnable() {
			@Override
			public void run() {
				configChanged();
				try {
					UHSReaderFrame.this.appConfig.store();
				}
				catch ( IOException e ) {
					logger.error( "Error storing config", e );
				}
			}
		});

		// Prep the glasspane to nerf this window later.
		this.getGlassPane().setCursor(Cursor.getPredefinedCursor( Cursor.WAIT_CURSOR ));
		this.getGlassPane().setFocusTraversalKeysEnabled( false );
		this.getGlassPane().addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed( MouseEvent e ) {
				e.consume();
			}

			@Override
			public void mouseReleased( MouseEvent e ) {
				e.consume();
			}
		});
		this.getGlassPane().addKeyListener(new KeyListener() {
			@Override
			public void keyPressed( KeyEvent e ) {
				e.consume();
			}

			@Override
			public void keyReleased( KeyEvent e ) {
				e.consume();
			}

			@Override
			public void keyTyped( KeyEvent e ) {
				e.consume();
			}
		});

		pane.add( tabbedPane, BorderLayout.CENTER );
		this.setContentPane( pane );
	}

	/**
	 * Does extra initialization after the constructor.
	 *
	 * <p>This must be called on the Swing event thread (use invokeLater()).</p>
	 */
	public void init() {
		configChanged();

		// Get the JFileChooser cached.
		try {
			Class.forName( "javax.swing.JFileChooser" );
		}
		catch( ClassNotFoundException e ) {
			logger.error( "Could not cache the JFileChooser class", e );
		}
	}


	public void configChanged() {
		int newFontSize = appConfig.getPropertyAsInt( "font_size", 0 );
		if ( newFontSize > 0 ) readerPanel.setFontSize( newFontSize );

		Pattern hostPortPtn = Pattern.compile( "^([a-z\\d](?:[a-z\\d\\-]{0,61}[a-z\\d])?(\\.[a-z\\d](?:[a-z\\d\\-]{0,61}‌​[a-z\\d])?)*):([0-9]+)$" );
		Matcher m = null;

		Properties sysProp = System.getProperties();
		if ( (m=hostPortPtn.matcher( appConfig.getProperty( "http_proxy", "" ) )).matches() ) {
			sysProp.setProperty( "http.proxyHost", m.group( 1 ) );
			sysProp.setProperty( "http.proxyPort", m.group( 2 ) );
		}
		if ( (m=hostPortPtn.matcher( appConfig.getProperty( "socks_proxy", "" ) )).matches() ) {
			sysProp.setProperty( "socks.proxyHost", m.group( 1 ) );
			sysProp.setProperty( "socks.proxyPort", m.group( 2 ) );
		}
	}


	public void setAppDir( File d ) {
		appDir = d;
	}

	public File getAppDir() {
		return appDir;
	}

	public void setAppDataDir( File d ) {
		appDataDir = d;
	}

	public File getAppDataDir() {
		return appDataDir;
	}

	public void setUserDataDir( File d ) {
		userDataDir = d;
		hintsDir = new File( userDataDir, "hints" );
		readerPanel.setHintsDir( hintsDir );
		downloaderPanel.setHintsDir( hintsDir );
	}

	public File getUserDataDir() {
		return userDataDir;
	}


	public UHSReaderPanel getUHSReaderPanel() {
		return readerPanel;
	}

	public UHSDownloaderPanel getUHSDownloaderPanel() {
		return downloaderPanel;
	}

	public UHSReaderConfigPanel getConfigPanel() {
		return configPanel;
	}


	public void setTitlePrefix( String s ) {
		if ( s == null || s.length() == 0 ) titlePrefix = "";
		else titlePrefix = s;
	}

	public void setTitle( String title ) {
		if ( title == null || title.length() == 0 ) super.setTitle( titlePrefix );
		else super.setTitle( titlePrefix +"-"+ title );
	}


	@Override
	public void setNerfed( boolean b ) {
		//button mnemonics will still work
		Component glassPane = this.getGlassPane();
		if ( b ) {
			glassPane.setVisible( true );
			glassPane.requestFocusInWindow();
		} else {
			glassPane.setVisible( false );
		}
	}
}
