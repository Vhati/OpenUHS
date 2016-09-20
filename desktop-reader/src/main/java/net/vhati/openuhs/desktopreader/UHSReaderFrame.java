package net.vhati.openuhs.desktopreader;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

import net.vhati.openuhs.core.downloader.DownloadableUHS;
import net.vhati.openuhs.desktopreader.Nerfable;
import net.vhati.openuhs.desktopreader.SettingsPanel;
import net.vhati.openuhs.desktopreader.downloader.DownloadableUHSTableModel;
import net.vhati.openuhs.desktopreader.downloader.UHSDownloaderPanel;
import net.vhati.openuhs.desktopreader.reader.AudioNodePanel;
import net.vhati.openuhs.desktopreader.reader.DefaultNodePanel;
import net.vhati.openuhs.desktopreader.reader.HotSpotNodePanel;
import net.vhati.openuhs.desktopreader.reader.ImageNodePanel;
import net.vhati.openuhs.desktopreader.reader.RootNodePanel;
import net.vhati.openuhs.desktopreader.reader.UHSReaderPanel;


public class UHSReaderFrame extends JFrame implements Nerfable {
	private File appDir = new File( "./" );
	private File appDataDir = new File( "./" );
	private File userDataDir = new File( "./" );
	private File hintsDir = new File( userDataDir, "hints" );

	private String titlePrefix = "";
	private UHSReaderPanel readerPanel = new UHSReaderPanel();
	private UHSDownloaderPanel downloaderPanel = new UHSDownloaderPanel();
	private SettingsPanel settingsPanel = new SettingsPanel();


	public UHSReaderFrame() {
		super();

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

		tabbedPane.add( settingsPanel, "Settings" );

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

		downloaderPanel.getUHSTable().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked( MouseEvent e ) {
				JTable tmpTable = (JTable)e.getSource();
				if ( tmpTable.getRowCount() == 0 ) return;

				if ( e.getClickCount() == 2 ) {
					int row = tmpTable.getSelectedRow();
					if ( row == -1 ) return;
					DownloadableUHS tmpUHS = ((DownloadableUHSTableModel)tmpTable.getModel()).getUHS( row );
					File uhsFile = new File( hintsDir, tmpUHS.getName() );
					if ( uhsFile.exists() ) {
						tabbedPane.setSelectedIndex( tabbedPane.indexOfTab( "Reader" ) );
						readerPanel.openFile( uhsFile );
					}
				}
			}
		});

		settingsPanel.addAncestorListener(new AncestorListener() {
			@Override
			public void ancestorAdded( AncestorEvent event ) {
				settingsPanel.clear();
				settingsPanel.addSection( "Reader", readerPanel.getSettingsPanel() );
				settingsPanel.addSection( "Downloader", downloaderPanel.getSettingsPanel() );
			}

			@Override
			public void ancestorMoved( AncestorEvent event ) {
			}

			@Override
			public void ancestorRemoved( AncestorEvent event ) {
				settingsPanel.clear();
			}
		});

		//Prep the glasspane to nerf this window later
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

	public SettingsPanel getSettingsPanel() {
		return settingsPanel;
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
