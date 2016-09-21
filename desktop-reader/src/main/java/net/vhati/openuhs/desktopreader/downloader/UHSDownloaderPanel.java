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

import net.vhati.openuhs.core.downloader.CatalogItem;
import net.vhati.openuhs.core.downloader.CatalogItemComparator;
import net.vhati.openuhs.core.downloader.CatalogParser;
import net.vhati.openuhs.desktopreader.Nerfable;
import net.vhati.openuhs.desktopreader.downloader.CatalogTableCellRenderer;
import net.vhati.openuhs.desktopreader.downloader.CatalogTableModel;
import net.vhati.openuhs.desktopreader.downloader.StringFetchTask;
import net.vhati.openuhs.desktopreader.downloader.StringFetchTask.StringFetchResult;
import net.vhati.openuhs.desktopreader.downloader.UHSFetchTask.UHSFetchResult;
import net.vhati.openuhs.desktopreader.reader.UHSReaderPanel;


public class UHSDownloaderPanel extends JPanel implements ActionListener {

	private final Logger logger = LoggerFactory.getLogger( UHSDownloaderPanel.class );

	private CatalogTableModel catalogTableModel = new CatalogTableModel( new String[] {"Title","Date","FullSize","Name"} );
	private JTable catalogTable = null;
	private JScrollPane catalogTableScroll = null;

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


		catalogTable = new JTable();
			catalogTable.setModel( catalogTableModel );
			try {
				catalogTable.setDefaultRenderer( Class.forName( "java.lang.Object" ), new CatalogTableCellRenderer() );
			}
			catch( ClassNotFoundException e ) {
				logger.error( "Could not set table renderer for download panel", e );
			}
			catalogTable.setSelectionMode( ListSelectionModel.MULTIPLE_INTERVAL_SELECTION );
			catalogTable.getTableHeader().setReorderingAllowed( false );
			catalogTable.getColumn( "Title" ).setPreferredWidth( 80 );
			catalogTable.getColumn( "Date" ).setMaxWidth( 80 );
			catalogTable.getColumn( "Date" ).setPreferredWidth( 70 );
			catalogTable.getColumn( "FullSize" ).setMaxWidth( 75 );
			catalogTable.getColumn( "FullSize" ).setPreferredWidth( 50 );
			catalogTable.getColumn( "Name" ).setMaxWidth( 130 );
			catalogTable.getColumn( "Name" ).setPreferredWidth( 100 );
		catalogTableScroll = new JScrollPane( catalogTable );
			catalogTable.addNotify();


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

		catalogTable.getTableHeader().addMouseListener(new MouseAdapter() {
			@Override
			public void mouseReleased( MouseEvent e ) {
				int index = catalogTable.getColumnModel().getColumnIndexAtX( e.getX() );
				int col = catalogTable.convertColumnIndexToModel( index );

				int sortBy = CatalogItemComparator.SORT_TITLE;
				boolean reverse = false;
				if ( "Title".equals( catalogTableModel.getColumnName( col ) ) ) {
					sortBy = CatalogItemComparator.SORT_TITLE;
				}
				else if ( "Date".equals( catalogTableModel.getColumnName( col ) ) ) {
					sortBy = CatalogItemComparator.SORT_DATE;
					reverse = true;
				}
				else if ( "FullSize".equals( catalogTableModel.getColumnName( col ) ) ) {
					sortBy = CatalogItemComparator.SORT_FULLSIZE;
				}
				else if ( "Name".equals( catalogTableModel.getColumnName( col ) ) ) {
					sortBy = CatalogItemComparator.SORT_NAME;
				}
				Comparator<CatalogItem> c = new CatalogItemComparator( sortBy );
				if ( reverse ) c = Collections.reverseOrder( c );
				catalogTableModel.sort( c );
			}
		});

		catalogTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
			@Override
			public void valueChanged( ListSelectionEvent e ) {
				if ( e.getValueIsAdjusting() ) return;
				boolean state = false;
				if ( catalogTable.getSelectedRow() != -1 ) state = true;
				downloadBtn.setEnabled( state );
			}
		});

		this.add( ctrlPanel, BorderLayout.NORTH );
		this.add( catalogTableScroll, BorderLayout.CENTER );
		this.add( bottomPanel, BorderLayout.SOUTH );
	}


	@Override
	public void actionPerformed( ActionEvent e ) {
		Object source = e.getSource();
		if ( source == reloadBtn ) {
			fetchCatalog();
		}
		else if ( source == downloadBtn ) {
			List<CatalogItem> wantedItems = getWantedDownloads();
			fetchUHS( wantedItems );
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
	private List<CatalogItem> getWantedDownloads() {
		int[] rows = catalogTable.getSelectedRows();

		List<CatalogItem> wantedItems = new ArrayList<CatalogItem>(rows.length);
		List<CatalogItem> existingItems = new ArrayList<CatalogItem>(rows.length);

		String[] hintNames = hintsDir.list();
		Arrays.sort( hintNames );

		for ( int i=0; i < rows.length; i++ ) {
			CatalogItem catItem = catalogTableModel.getUHS( rows[i] );
			if ( catItem.getName().length() == 0 ) continue;

			if ( Arrays.binarySearch( hintNames, catItem.getName() ) >= 0 ) {
				existingItems.add( catItem );
			}
			wantedItems.add( catItem );
		}
		if ( !existingItems.isEmpty() ) {
			String message;
			if ( existingItems.size() == 1 ) {
				message = String.format( "That file exists (\"%s\"), overwrite?", existingItems.get( 0 ).getName() );
			}
			else {
				message = String.format( "Some of these files exist (%d), overwrite?", existingItems.size() );
			}

			int choice = JOptionPane.showConfirmDialog( getAncestorComponent(), message, "Overwrite?", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
			if ( choice == JOptionPane.YES_OPTION ) {
				for ( CatalogItem catItem : existingItems ) {
					File uhsFile = new File( hintsDir, catItem.getName() );
					uhsFile.delete();
				}
				existingItems.clear();
			}
			else if ( choice == JOptionPane.NO_OPTION ) {
				wantedItems.removeAll( existingItems );
			}
			else {
				wantedItems.clear();
			}
		}

		return wantedItems;
	}

	private void fetchUHS( List<CatalogItem> catItems ) {
		cancelFetching();
		if ( catItems.isEmpty() ) return;

		uhsFetchTask = new UHSFetchTask( hintsDir, catItems.toArray( new CatalogItem[catItems.size()] ) );
		uhsFetchTask.setUserAgent( CatalogParser.DEFAULT_USER_AGENT );

		final boolean single = ( catItems.size() == 1 );
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
								List<CatalogItem> catalog = catalogParser.parseCatalog( fetchResult.content );
								if ( catalog.size() > 0 ) {
									catalogTableModel.clear();

									for ( CatalogItem catItem : catalog ) {
										catalogTableModel.addUHS( catItem );
									}
									catalogTableModel.sort();
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
		catalogTable.clearSelection();

		String[] hintNames = hintsDir.list();
		Arrays.sort( hintNames );

		for ( int i=0; i < catalogTableModel.getRowCount(); i++ ) {
			CatalogItem catItem = catalogTableModel.getUHS( i );
			catItem.resetState();

			if ( Arrays.binarySearch( hintNames, catItem.getName()) >= 0 ) {
				catItem.setLocal( true );
				File uhsFile = new File( hintsDir, catItem.getName() );
				Date localDate = new Date( uhsFile.lastModified() );

				if ( catItem.getDate() != null && catItem.getDate().after( localDate ) ) {
					catItem.setNewer( true );
				}
			}
		}
		catalogTable.repaint();
	}


	private void find( String s ) {
		if ( s.length() == 0 ) return;
		String findString = s.toLowerCase();

		Rectangle viewRect = catalogTableScroll.getViewport().getViewRect();
		int selRow = catalogTable.getSelectedRow();
		int rowCount = catalogTableModel.getRowCount();
		int foundRow = -1;

		if ( selRow >= 0 ) {
			for ( int i=selRow+1; i < rowCount; i++ ) {
				CatalogItem catItem = catalogTableModel.getUHS( i );
				if ( catItem.getTitle().toLowerCase().indexOf( findString ) != -1 ) {
					foundRow = i;
					break;
				}
			}
		}
		if ( foundRow == -1 ) {
			for ( int i=0; i < (selRow >= 0 ? selRow : rowCount); i++ ) {
				CatalogItem catItem = catalogTableModel.getUHS( i );
				if ( catItem.getTitle().toLowerCase().indexOf( findString ) != -1 ) {
					foundRow = i;
					break;
				}
			}
		}
		if ( foundRow != -1 ) {
			catalogTable.scrollRectToVisible( new Rectangle( catalogTable.getCellRect( foundRow, 0, true ) ) );
			catalogTable.setRowSelectionInterval( foundRow, foundRow );
		}
	}


	/**
	 * Returns the table displaying the catalog.
	 *
	 * <p>This is so parent containers can add listeners to the downloader's GUI.</p>
	 *
	 * @return the table
	 */
	public JTable getCatalogTable() {
		return catalogTable;
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
