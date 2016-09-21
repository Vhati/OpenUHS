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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.ProgressMonitor;
import javax.swing.SwingWorker;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.downloader.CatalogParser;
import net.vhati.openuhs.core.downloader.DownloadableUHS;
import net.vhati.openuhs.core.downloader.DownloadableUHSComparator;
import net.vhati.openuhs.desktopreader.Nerfable;
import net.vhati.openuhs.desktopreader.downloader.DownloadableUHSTableModel;
import net.vhati.openuhs.desktopreader.downloader.StringFetchTask;
import net.vhati.openuhs.desktopreader.downloader.StringFetchTask.StringFetchResult;
import net.vhati.openuhs.desktopreader.downloader.UHSFetchTask.UHSFetchResult;
import net.vhati.openuhs.desktopreader.downloader.UHSTableCellRenderer;
import net.vhati.openuhs.desktopreader.reader.UHSReaderPanel;


public class UHSDownloaderPanel extends JPanel implements ActionListener {

	private final Logger logger = LoggerFactory.getLogger( UHSDownloaderPanel.class );

	private DownloadableUHSTableModel uhsTableModel = new DownloadableUHSTableModel( new String[] {"Title","Date","FullSize","Name"} );
	private JTable uhsTable = null;
	private JScrollPane uhsTableScroll = null;

	private JButton reloadBtn = null;
	private JButton downloadBtn = null;
	private JTextField findField = null;
	private JButton findBtn = null;

	private UHSReaderPanel readerPanel = null;
	private MouseListener readerClickListener = null;

	private File hintsDir = new File( "./" );

	private StringFetchTask catalogFetchTask = null;
	private UHSFetchTask uhsFetchTask = null;


	public UHSDownloaderPanel() {
		super( new BorderLayout() );

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
				logger.error( "Could not set table renderer for download panel", e );
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

		this.add( ctrlPanel, BorderLayout.NORTH );
		this.add( uhsTableScroll, BorderLayout.CENTER );
		this.add( bottomPanel, BorderLayout.SOUTH );
	}


	@Override
	public void actionPerformed( ActionEvent e ) {
		Object source = e.getSource();
		if ( source == reloadBtn ) {
			fetchCatalog();
		}
		else if ( source == downloadBtn ) {
			List<DownloadableUHS> wantedDuhs = getWantedDownloads();
			fetchUHS( wantedDuhs );
		}
		else if ( source == findBtn ) {
			find( findField.getText() );
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


	private void cancelFetching() {
		if ( catalogFetchTask != null && !catalogFetchTask.isDone() ) {
			catalogFetchTask.abortTask();
		}
		if ( uhsFetchTask != null && !uhsFetchTask.isDone() ) {
			uhsFetchTask.abortTask();
		}
	}

	/**
	 * Returns selected catalog entries, prompting to delete if local files exist.
	 *
	 * @return a List of entries, after pruning any local ones the user did not wish to delete
	 */
	private List<DownloadableUHS> getWantedDownloads() {
		int[] rows = uhsTable.getSelectedRows();

		List<DownloadableUHS> wantedDuhs = new ArrayList<DownloadableUHS>(rows.length);
		List<DownloadableUHS> existingDuhs = new ArrayList<DownloadableUHS>(rows.length);

		String[] hintNames = hintsDir.list();
		Arrays.sort( hintNames );

		for ( int i=0; i < rows.length; i++ ) {
			DownloadableUHS duh = uhsTableModel.getUHS( rows[i] );
			if ( duh.getName().length() == 0 ) continue;

			if ( Arrays.binarySearch( hintNames, duh.getName()) >= 0 ) {
				existingDuhs.add( duh );
			}
			wantedDuhs.add( duh );
		}
		if ( !existingDuhs.isEmpty() ) {
			String message;
			if ( existingDuhs.size() == 1 ) {
				message = String.format( "That file exists (\"%s\"), overwrite?", existingDuhs.get( 0 ).getName() );
			}
			else {
				message = String.format( "Some of these files exist (%d), overwrite?", existingDuhs.size() );
			}

			int choice = JOptionPane.showConfirmDialog( getAncestorComponent(), message, "Overwrite?", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if ( choice == JOptionPane.YES_OPTION ) {
				for ( DownloadableUHS duh : existingDuhs ) {
					File uhsFile = new File( hintsDir, duh.getName() );
					uhsFile.delete();
				}
				existingDuhs.clear();
			}
			else if ( choice == JOptionPane.NO_OPTION ) {
				wantedDuhs.removeAll( existingDuhs );
			}
			else {
				wantedDuhs.clear();
			}
		}

		return wantedDuhs;
	}

	private void fetchUHS( List<DownloadableUHS> duhs ) {
		cancelFetching();
		if ( duhs.isEmpty() ) return;

		uhsFetchTask = new UHSFetchTask( hintsDir, duhs.toArray( new DownloadableUHS[duhs.size()] ) );
		uhsFetchTask.setUserAgent( CatalogParser.DEFAULT_USER_AGENT );

		final boolean single = ( duhs.size() == 1 );
		uhsFetchTask.addPropertyChangeListener(new PropertyChangeListener() {
			private ProgressMonitor progressDlg = null;
			private String unitName = "";
			private int unitProgress = 0;
			private int fetchProgress = 0;

			private void updateMonitor() {
					progressDlg.setNote( (( !single ) ? String.format( "%02d%%: ", fetchProgress) : "") + unitName );
					progressDlg.setProgress( ((fetchProgress < 100 ) ? Math.min( unitProgress, 99 ) : 100) );

					// The ProgressMonitor will disappear if its progress reaches 100.
					// So individual units are capped to 99.
			}

			@Override
			public void propertyChange( PropertyChangeEvent e ) {
				if ( UHSFetchTask.PROP_UNIT_NAME.equals( e.getPropertyName() ) ) {
					unitName = (String)e.getNewValue();
					updateMonitor();
					if ( progressDlg.isCanceled() ) uhsFetchTask.abortTask();
				}
				if ( UHSFetchTask.PROP_UNIT_PROGRESS.equals( e.getPropertyName() ) ) {
					unitProgress = ((Integer)e.getNewValue()).intValue();
					updateMonitor();
					if ( progressDlg.isCanceled() ) uhsFetchTask.abortTask();
				}
				else if ( "progress".equals( e.getPropertyName() ) ) {
					fetchProgress = ((Integer)e.getNewValue()).intValue();
					updateMonitor();
					if ( progressDlg.isCanceled() ) uhsFetchTask.abortTask();
				}
				else if ( "state".equals( e.getPropertyName() ) ) {
					if ( SwingWorker.StateValue.STARTED.equals( e.getNewValue() ) ) {
						ancestorSetNerfed( true );

						progressDlg = new ProgressMonitor( getAncestorComponent(), "Fetching...", "", 0, 100 );
						progressDlg.setProgress( 0 );
					}
					else if ( SwingWorker.StateValue.DONE.equals( e.getNewValue() ) ) {
						try {
							List<UHSFetchResult> fetchResults = uhsFetchTask.get();  // get() blocks!

							for ( UHSFetchResult fetchResult : fetchResults ) {
								if ( fetchResult.status != UHSFetchResult.STATUS_COMPLETED ) {

									if ( fetchResult.status != UHSFetchResult.STATUS_CANCELLED ) {
										// TODO: ...
									}
									if ( fetchResult.file != null && fetchResult.file.exists() ) {
										fetchResult.file.delete();
									}
								}
							}
						}
						catch ( Exception ex ) {
							// InterruptedException, while get() was blocking.
							// java.util.concurrent.ExecutionException, if SwingWorker threw something.

							logger.error( "Could not fetch hint files", ex );
						}

						colorizeTable();
						if ( progressDlg != null ) progressDlg.close();
						ancestorSetNerfed( false );
					}
				}
			}
		});

		uhsFetchTask.execute();
	}

	private void fetchCatalog() {
		cancelFetching();

		catalogFetchTask = new StringFetchTask( CatalogParser.DEFAULT_CATALOG_URL );
		catalogFetchTask.setUserAgent( CatalogParser.DEFAULT_USER_AGENT );
		catalogFetchTask.setEncoding( CatalogParser.DEFAULT_CATALOG_ENCODING );

		catalogFetchTask.addPropertyChangeListener(new PropertyChangeListener() {
			private ProgressMonitor progressDlg = null;

			@Override
			public void propertyChange( PropertyChangeEvent e ) {
				if ( "progress".equals( e.getPropertyName() ) ) {
					int progress = ((Integer)e.getNewValue()).intValue();

					progressDlg.setProgress( progress );
					progressDlg.setNote( String.format( "Catalog %d%%", progress ) );
				}
				else if ( "state".equals( e.getPropertyName() ) ) {
					if ( SwingWorker.StateValue.STARTED.equals( e.getNewValue() ) ) {
						ancestorSetNerfed( true );

						progressDlg = new ProgressMonitor( getAncestorComponent(), "Fetching...", "", 0, 100 );
						progressDlg.setProgress( 0 );

						if ( progressDlg.isCanceled() ) catalogFetchTask.abortTask();
					}
					else if ( SwingWorker.StateValue.DONE.equals( e.getNewValue() ) ) {
						try {
							StringFetchResult fetchResult = catalogFetchTask.get();  // get() blocks!

							if ( fetchResult.status == StringFetchResult.STATUS_COMPLETED ) {

								CatalogParser catalogParser = new CatalogParser();
								List<DownloadableUHS> catalog = catalogParser.parseCatalog( fetchResult.content );
								if ( catalog.size() > 0 ) {
									uhsTableModel.clear();

									for ( DownloadableUHS duh : catalog ) {
										uhsTableModel.addUHS( duh );
									}
									uhsTableModel.sort();
									colorizeTable();
								}
								else {
									// TODO: ...
								}
							}
							else {
								if ( fetchResult.status != StringFetchResult.STATUS_CANCELLED ) {
									// TODO: ...
								}
							}
						}
						catch ( Exception ex ) {
							// InterruptedException, while get() was blocking.
							// java.util.concurrent.ExecutionException, if SwingWorker threw something.

							logger.error( "Could not fetch/parse catalog", ex );
						}

						if ( progressDlg != null ) progressDlg.close();
						ancestorSetNerfed( false );
					}
				}
			}
		});

		catalogFetchTask.execute();
	}


	private void colorizeTable() {
		uhsTable.clearSelection();

		String[] hintNames = hintsDir.list();
		Arrays.sort( hintNames );

		for ( int i=0; i < uhsTableModel.getRowCount(); i++ ) {
			DownloadableUHS tmpUHS = uhsTableModel.getUHS( i );
			tmpUHS.resetState();

			if ( Arrays.binarySearch( hintNames, tmpUHS.getName()) >= 0 ) {
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
		Object ancestor = this.getTopLevelAncestor();
		if ( ancestor != null ) {
			if ( ancestor instanceof Component ) ancestorComponent = (Component)ancestor;
		}
		return ancestorComponent;
	}
}
