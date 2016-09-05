package net.vhati.openuhs.desktopreader.downloader;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import net.vhati.openuhs.core.DefaultUHSErrorHandler;
import net.vhati.openuhs.core.UHSErrorHandler;
import net.vhati.openuhs.core.downloader.DownloadableUHS;
import net.vhati.openuhs.core.downloader.DownloadableUHSComparator;
import net.vhati.openuhs.desktopreader.AppliablePanel;
import net.vhati.openuhs.desktopreader.Nerfable;
import net.vhati.openuhs.desktopreader.downloader.DownloadableUHSTableModel;
import net.vhati.openuhs.desktopreader.downloader.UHSTableCellRenderer;
import net.vhati.openuhs.desktopreader.reader.UHSReaderPanel;


public class UHSDownloaderPanel extends JPanel implements ActionListener {
	private UHSErrorHandler errorHandler = null;

	private UHSDownloaderPanel pronoun = this;
	private DownloadableUHSTableModel uhsTableModel = new DownloadableUHSTableModel( new String[] {"Title","Date","FullSize","Name"} );
	private JTable uhsTable = null;
	private JScrollPane uhsTableScroll = null;

	private JButton reloadBtn = null;
	private JButton downloadBtn = null;
	private JTextField findField = null;
	private JButton findBtn = null;

	private UHSReaderPanel readerPanel = null;
	private MouseListener readerClickListener = null;

	private File hintsDir = new File( "." );


	public UHSDownloaderPanel() {
		super( new BorderLayout() );

		setErrorHandler( new DefaultUHSErrorHandler( System.err ) );

		GridBagConstraints gridC = new GridBagConstraints();

		JPanel ctrlPanel = new JPanel( new GridLayout( 0, 3 ) );

			JPanel ctrlLeftPanel = new JPanel( new GridBagLayout() );
				gridC.gridx = 0;
				gridC.weightx = 1.0;
				gridC.fill = GridBagConstraints.NONE;
				gridC.anchor = GridBagConstraints.WEST;
				reloadBtn = new JButton( "Refresh" );
					ctrlLeftPanel.add( reloadBtn, gridC );
				ctrlPanel.add( ctrlLeftPanel );

			JPanel ctrlCenterPanel = new JPanel( new GridBagLayout() );
				gridC.gridx = 0;
				gridC.weightx = 1.0;
				gridC.fill = GridBagConstraints.NONE;
				gridC.anchor = GridBagConstraints.CENTER;
				// ...
			ctrlPanel.add( ctrlCenterPanel );

			JPanel ctrlRightPanel = new JPanel( new GridBagLayout() );
				gridC.gridx = 0;
				gridC.weightx = 1.0;
				gridC.fill = GridBagConstraints.NONE;
				gridC.anchor = GridBagConstraints.EAST;
				downloadBtn = new JButton( "Download" );
					downloadBtn.setEnabled( false );
					ctrlRightPanel.add( downloadBtn, gridC );
				ctrlPanel.add( ctrlRightPanel );


		uhsTable = new JTable();
			uhsTable.setModel( uhsTableModel );
			try {
				uhsTable.setDefaultRenderer( Class.forName( "java.lang.Object" ), new UHSTableCellRenderer() );
			}
			catch( ClassNotFoundException e ) {
				if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, pronoun, "Could not set table renderer for download panel", 0, e );
			}
			uhsTable.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
			uhsTable.getTableHeader().setReorderingAllowed( false );
			uhsTable.getColumn( "Title" ).setPreferredWidth( 80 );
			uhsTable.getColumn( "Date" ).setMaxWidth( 80 );
			uhsTable.getColumn( "Date" ).setPreferredWidth( 70 );
			uhsTable.getColumn( "FullSize" ).setMaxWidth( 75 );
			uhsTable.getColumn( "FullSize" ).setPreferredWidth( 50 );
			uhsTable.getColumn( "Name" ).setMaxWidth( 130 );
			uhsTable.getColumn( "Name" ).setPreferredWidth( 100 );
			uhsTableScroll = new JScrollPane( uhsTable );
				uhsTable.addNotify();


		JPanel bottomPanel = new JPanel();
			bottomPanel.setLayout( new BoxLayout( bottomPanel, BoxLayout.Y_AXIS ) );

			JLabel legendLbl = new JLabel( "Gray - Exists  /  Gold - Updated" );
				legendLbl.setAlignmentX( 0.5f );
				bottomPanel.add( legendLbl );
			bottomPanel.add( new JSeparator( JSeparator.HORIZONTAL ) );
			bottomPanel.add( Box.createVerticalStrut( 2 ) );
			JPanel findPanel = new JPanel( new GridBagLayout() );
				gridC.gridx = 0;
				gridC.weightx = 1.0;
				gridC.fill = GridBagConstraints.HORIZONTAL;
				findField = new JTextField();
				findPanel.add( findField, gridC );
				gridC.gridx++;
				gridC.weightx = 0.0;
				gridC.fill = GridBagConstraints.NONE;
				findBtn = new JButton( "Find Next" );
					findPanel.add( findBtn, gridC );
				bottomPanel.add( findPanel );

		reloadBtn.addActionListener( this );
		downloadBtn.addActionListener( this );
		findBtn.addActionListener( this );

		Action findAction = new AbstractAction() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				find( findField.getText() );
			}
		};
		findPanel.getInputMap( JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT ).put( KeyStroke.getKeyStroke( "released ENTER" ), "find" );
		findPanel.getActionMap().put( "find", findAction );

		uhsTable.getTableHeader().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased( MouseEvent e ) {
				int index = uhsTable.getColumnModel().getColumnIndexAtX( e.getX() );
				int col = uhsTable.convertColumnIndexToModel( index );

				int sortBy = DownloadableUHSComparator.SORT_TITLE;
				boolean reverse = false;
				if ( "Title".equals( uhsTableModel.getColumnName( col ) ) ) {
					sortBy = DownloadableUHSComparator.SORT_TITLE;
				}
				else if ( "Date".equals( uhsTableModel.getColumnName( col ) ) ) {
					sortBy = DownloadableUHSComparator.SORT_DATE;
					reverse = true;
				}
				else if ( "FullSize".equals( uhsTableModel.getColumnName( col ) ) ) {
					sortBy = DownloadableUHSComparator.SORT_FULLSIZE;
				}
				else if ( "Name".equals( uhsTableModel.getColumnName( col ) ) ) {
					sortBy = DownloadableUHSComparator.SORT_NAME;
				}
				Comparator<DownloadableUHS> c = new DownloadableUHSComparator( sortBy );
				if ( reverse ) c = Collections.reverseOrder( c );
				uhsTableModel.sort( c );
			}
		});

		uhsTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged( ListSelectionEvent e ) {
				if ( e.getValueIsAdjusting() ) return;
				boolean state = false;
				if ( uhsTable.getSelectedRow() != -1 ) state = true;
				downloadBtn.setEnabled( state );
			}
		});

		pronoun.add( ctrlPanel, BorderLayout.NORTH );
		pronoun.add( uhsTableScroll, BorderLayout.CENTER );
		pronoun.add( bottomPanel, BorderLayout.SOUTH );
	}


	@Override
	public void actionPerformed( ActionEvent e ) {
		Object source = e.getSource();
		if ( source == reloadBtn ) {
			reloadTable();
		}
		else if ( source == downloadBtn ) {
			downloadHints();
		}
		else if ( source == findBtn ) {
			find( findField.getText() );
		}
	}


	/**
	 * Sets the error handler to notify of exceptions.
	 *
	 * <p>This is a convenience for logging/muting.</p>
	 *
	 * <p>The default handler prints to System.err.</p>
	 *
	 * @param eh  the error handler, or null, for quiet parsing
	 */
	public void setErrorHandler( UHSErrorHandler eh ) {
		errorHandler = eh;
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


	private void reloadTable() {
		ancestorSetNerfed( true );
		final Component parentComponent = getAncestorComponent();

		uhsTableModel.clear();

		Thread reloadWorker = new Thread() {
			@Override
			public void run() {
				final List<DownloadableUHS> catalog = UHSFetcher.fetchCatalog( parentComponent );

				// Back to the event thread...
				Runnable r = new Runnable() {
					@Override
					public void run() {
						for ( int i=0; i < catalog.size(); i++ ) {
							uhsTableModel.addUHS( catalog.get(i) );
						}
						uhsTableModel.sort();
						colorizeTable();
						ancestorSetNerfed( false );
					}
				};
				EventQueue.invokeLater( r );
			}
		};

		reloadWorker.start();
	}

	private void colorizeTable() {
		uhsTable.clearSelection();

		String[] hintNames = hintsDir.list();
		Arrays.sort( hintNames );

		for ( int i=0; i < uhsTableModel.getRowCount(); i++ ) {
			DownloadableUHS tmpUHS = uhsTableModel.getUHS( i );
			tmpUHS.resetState();

			if (Arrays.binarySearch( hintNames, tmpUHS.getName()) >= 0 ) {
				tmpUHS.setLocal( true );
				File uhsFile = new File( hintsDir, tmpUHS.getName() );
				Date localDate = new Date( uhsFile.lastModified() );

				if ( tmpUHS.getDate() != null && tmpUHS.getDate().after( localDate ) ) {
					tmpUHS.setNewer( true );
				}
			}
		}
		uhsTable.repaint();
	}


	private void downloadHints() {
		if ( uhsTableModel.getRowCount() == 0 || uhsTable.getSelectedRowCount() == 0 ) {
			// There's a JTable bug that misreports selection as 1 when empty
			return;
		}
		ancestorSetNerfed( true );
		final Component parentComponent = getAncestorComponent();

		int[] rows = uhsTable.getSelectedRows();
		final DownloadableUHS[] wants = new DownloadableUHS[rows.length];
		for ( int i=0; i < rows.length; i++ ) {
			wants[i] = uhsTableModel.getUHS( rows[i] );
		}

		Thread downloadWorker = new Thread() {
			@Override
			public void run() {
				for ( int i=0; i < wants.length; i++ ) {
					DownloadableUHS tmpUHS = wants[i];
					byte[] bytes = UHSFetcher.fetchUHS( parentComponent, tmpUHS );
					if ( bytes != null ) {
						boolean success = UHSFetcher.saveBytes( parentComponent, new File( hintsDir, tmpUHS.getName() ).getAbsolutePath(), bytes );
						if ( errorHandler != null ) {
							if ( success ) errorHandler.log( UHSErrorHandler.INFO, pronoun, "Saved "+ tmpUHS.getName(), 0, null );
							else errorHandler.log( UHSErrorHandler.ERROR, pronoun, "Could not save "+ tmpUHS.getName(), 0, null );
						}
					}
				}

				// Back to the event thread...
				Runnable r = new Runnable() {
					@Override
					public void run() {
						colorizeTable();
						ancestorSetNerfed( false );
					}
				};
				EventQueue.invokeLater( r );
			}
		};

		downloadWorker.start();
	}


	private void find( String s ) {
		if ( s.length() == 0 ) return;
		String findString = s.toLowerCase();

		Rectangle viewRect = uhsTableScroll.getViewport().getViewRect();
		int selRow = uhsTable.getSelectedRow();
		int rowCount = uhsTableModel.getRowCount();
		int foundRow = -1;

		if ( selRow >= 0 ) {
			for ( int i=selRow+1; i < rowCount; i++ ) {
				DownloadableUHS tmpUHS = uhsTableModel.getUHS( i );
				if ( tmpUHS.getTitle().toLowerCase().indexOf( findString ) != -1 ) {
					foundRow = i;
					break;
				}
			}
		}
		if ( foundRow == -1 ) {
			for ( int i=0; i < (selRow >= 0 ? selRow : rowCount); i++ ) {
				DownloadableUHS tmpUHS = uhsTableModel.getUHS( i );
				if ( tmpUHS.getTitle().toLowerCase().indexOf( findString ) != -1 ) {
					foundRow = i;
					break;
				}
			}
		}
		if ( foundRow != -1 ) {
			uhsTable.scrollRectToVisible( new Rectangle( uhsTable.getCellRect( foundRow, 0, true ) ) );
			uhsTable.setRowSelectionInterval( foundRow, foundRow );
		}
	}


	/**
	 * Returns the table displaying the catalog.
	 *
	 * <p>This is so parent containers can add listeners to the downloader's GUI.</p>
	 *
	 * @return the table
	 */
	public JTable getUHSTable() {
		return uhsTable;
	}


	public AppliablePanel getSettingsPanel() {
		GridBagConstraints gridC = new GridBagConstraints();
		Insets defaultInsets = new Insets( 0, 0, 0, 0 );
		Insets labelInsets = new Insets( 0, 10, 0, 5 );

		AppliablePanel result = new AppliablePanel( new GridBagLayout() );
			gridC.gridy = 0;
			gridC.gridx = 0;
			gridC.gridwidth = GridBagConstraints.REMAINDER;
			gridC.weightx = 1.0;
			gridC.fill = GridBagConstraints.NONE;
			gridC.anchor = GridBagConstraints.WEST;
			gridC.insets = labelInsets;
			JLabel proxySepLbl = new JLabel( "Proxy" );
				proxySepLbl.setAlignmentX( Component.CENTER_ALIGNMENT );
				result.add( proxySepLbl, gridC );

			// Http proxy.

			gridC.gridy++;
			gridC.gridx = 0;
			gridC.gridwidth = 1;
			gridC.weightx = 0.0;
			gridC.fill = GridBagConstraints.HORIZONTAL;
			gridC.anchor = GridBagConstraints.WEST;
			gridC.insets = defaultInsets;
			final JCheckBox httpBox = new JCheckBox( "http", false );
				result.add( httpBox, gridC );

			gridC.gridx++;
			gridC.weightx = 0.0;
			gridC.fill = GridBagConstraints.NONE;
			gridC.anchor = GridBagConstraints.EAST;
			gridC.insets = labelInsets;
			JLabel httpHostLbl = new JLabel( "Host" );
				result.add( httpHostLbl, gridC );
			gridC.gridx++;
			gridC.weightx = 1.0;
			gridC.fill = GridBagConstraints.HORIZONTAL;
			gridC.anchor = GridBagConstraints.WEST;
			gridC.insets = defaultInsets;
			final JTextField httpHostField = new JTextField( "255.255.255.255 " );
				httpHostField.setPreferredSize( httpHostField.getPreferredSize() );
				httpHostField.setText( "" );
				httpHostField.setEnabled( false );
				result.add( httpHostField, gridC );

			gridC.gridx++;
			gridC.weightx = 0.0;
			gridC.fill = GridBagConstraints.NONE;
			gridC.anchor = GridBagConstraints.EAST;
			gridC.insets = labelInsets;
			JLabel httpPortLbl = new JLabel( "Port" );
				result.add( httpPortLbl, gridC );
			gridC.gridx++;
			gridC.weightx = 0.1;
			gridC.fill = GridBagConstraints.HORIZONTAL;
			gridC.anchor = GridBagConstraints.WEST;
			gridC.insets = defaultInsets;
			final JTextField httpPortField = new JTextField( "65536 " );
				httpPortField.setPreferredSize( httpPortField.getPreferredSize() );
				httpPortField.setText( "" );
				httpPortField.setEnabled( false );
				result.add( httpPortField, gridC );

			// Socks proxy.

			gridC.gridy++;
			gridC.gridx = 0;
			gridC.gridwidth = 1;
			gridC.weightx = 0.0;
			gridC.fill = GridBagConstraints.HORIZONTAL;
			gridC.anchor = GridBagConstraints.WEST;
			gridC.insets = defaultInsets;
			final JCheckBox socksBox = new JCheckBox( "socks", false );
				result.add( socksBox, gridC );

			gridC.gridx++;
			gridC.weightx = 0.0;
			gridC.fill = GridBagConstraints.NONE;
			gridC.anchor = GridBagConstraints.EAST;
			gridC.insets = labelInsets;
			JLabel socksHostLbl = new JLabel( "Host" );
				result.add( socksHostLbl, gridC );
			gridC.gridx++;
			gridC.weightx = 1.0;
			gridC.fill = GridBagConstraints.HORIZONTAL;
			gridC.anchor = GridBagConstraints.WEST;
			gridC.insets = defaultInsets;
			final JTextField socksHostField = new JTextField( "255.255.255.255 " );
				socksHostField.setPreferredSize( socksHostField.getPreferredSize() );
				socksHostField.setText( "" );
				socksHostField.setEnabled( false );
				result.add( socksHostField, gridC );

			gridC.gridx++;
			gridC.weightx = 0.0;
			gridC.fill = GridBagConstraints.NONE;
			gridC.anchor = GridBagConstraints.EAST;
			gridC.insets = labelInsets;
			JLabel socksPortLbl = new JLabel( "Port" );
				result.add( socksPortLbl, gridC );
			gridC.gridx++;
			gridC.weightx = 0.1;
			gridC.fill = GridBagConstraints.HORIZONTAL;
			gridC.anchor = GridBagConstraints.WEST;
			gridC.insets = defaultInsets;
			final JTextField socksPortField = new JTextField( "65536 " );
				socksPortField.setPreferredSize( socksPortField.getPreferredSize() );
				socksPortField.setText( "" );
				socksPortField.setEnabled( false );
				result.add( socksPortField, gridC );

		ActionListener settingsListener = new ActionListener() {
			@Override
			public void actionPerformed( ActionEvent e ) {
				Object source = e.getSource();
				if ( source == httpBox ) {
					boolean state = httpBox.isSelected();
					httpHostField.setEnabled( state );
					httpPortField.setEnabled( state );
				}
				else if ( source == socksBox ) {
					boolean state = socksBox.isSelected();
					socksHostField.setEnabled( state );
					socksPortField.setEnabled( state );
				}
			}
		};
		httpBox.addActionListener( settingsListener );
		socksBox.addActionListener( settingsListener );

		Properties prop = System.getProperties();
		String httpHost = prop.getProperty( "http.proxyHost" );
		String httpPort = prop.getProperty( "http.proxyPort" );
		if ( httpHost != null ) httpHostField.setText( httpHost );
		if ( httpPort != null ) httpPortField.setText( httpPort );
		if ( httpHost != null && httpPort != null ) {
			if ( httpHost.length() > 0 && httpPort.length() > 0 ) {
				httpBox.doClick();
			}
		}
		String socksHost = prop.getProperty( "socks.proxyHost" );
		String socksPort = prop.getProperty( "socks.proxyPort" );
		if ( socksHost != null ) socksHostField.setText( socksHost );
		if ( socksPort != null ) socksPortField.setText( socksPort );
		if ( socksHost != null && socksPort != null ) {
			if ( socksHost.length() > 0 && socksPort.length() > 0 ) {
				socksBox.doClick();
			}
		}

		Runnable applyAction = new Runnable() {
			@Override
			public void run() {
				Properties prop = System.getProperties();
				String httpHost = httpHostField.getText();
				String httpPort = httpPortField.getText();
				if ( httpBox.isSelected() && httpHost.length() > 0 && httpPort.length() > 0 ) {
					prop.setProperty( "http.proxyHost", httpHost );
					prop.setProperty( "http.proxyPort", httpPort );
				} else {
					prop.setProperty( "http.proxyHost", "" );
					prop.setProperty( "http.proxyPort", "" );
				}
				String socksHost = socksHostField.getText();
				String socksPort = socksPortField.getText();
				if ( socksBox.isSelected() && socksHost.length() > 0 && socksPort.length() > 0 ) {
					prop.setProperty( "socks.proxyHost", socksHost );
					prop.setProperty( "socks.proxyPort", socksPort );
				} else {
					prop.setProperty( "socks.proxyHost", "" );
					prop.setProperty( "socks.proxyPort", "" );
				}
			}
		};
		result.setApplyAction( applyAction );

		return result;
	}


	/**
	 * Calls setNerfed on the top-level ancestor, if nerfable.
	 *
	 * <p>A dedicated method was easier than passing the ancestor to runnables.</p>
	 */
	private void ancestorSetNerfed( boolean b ) {
		boolean nerfable = false;
		Component ancestorComponent = getAncestorComponent();
		if ( ancestorComponent != null ) {
			if ( ancestorComponent instanceof Nerfable ) nerfable = true;
		}

		if ( nerfable ) ((Nerfable)ancestorComponent).setNerfed( b );
	}

	/**
	 * Returns the top level ancestor, cast as a Component.
	 *
	 * <p>Otherwise returns null.</p>
	 */
	private Component getAncestorComponent() {
		Component ancestorComponent = null;
		Object ancestor = pronoun.getTopLevelAncestor();
		if ( ancestor != null ) {
			if ( ancestor instanceof Component ) ancestorComponent = (Component)ancestor;
		}
		return ancestorComponent;
	}
}
