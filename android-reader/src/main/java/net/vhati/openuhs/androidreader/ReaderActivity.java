package net.vhati.openuhs.androidreader;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import android.support.v4.content.IntentCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.androidreader.R;
import net.vhati.openuhs.androidreader.AndroidUHSConstants;
import net.vhati.openuhs.androidreader.reader.AudioNodeView;
import net.vhati.openuhs.androidreader.reader.DefaultNodeView;
import net.vhati.openuhs.androidreader.reader.HotSpotNodeView;
import net.vhati.openuhs.androidreader.reader.ImageNodeView;
import net.vhati.openuhs.androidreader.reader.NodeView;
import net.vhati.openuhs.androidreader.reader.RootNodeView;
import net.vhati.openuhs.androidreader.reader.UHSReaderNavCtrl;
import net.vhati.openuhs.core.UHSHotSpotNode;
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.core.UHSParser;
import net.vhati.openuhs.core.UHSRootNode;
import net.vhati.openuhs.core.markup.DecoratedFragment;


public class ReaderActivity extends AppCompatActivity implements UHSReaderNavCtrl, View.OnClickListener {
	public static final String EXTRA_OPEN_FILE = "net.openuhs.androidreader.OpenFile";

	private final Logger logger = LoggerFactory.getLogger( AndroidUHSConstants.LOG_TAG );

	private Toolbar toolbar = null;

	private String readerTitle = "";

	private UHSRootNode rootNode = null;
	private UHSNode currentNode = null;

	private List<NodeView> nodeViewRegistry = new ArrayList<NodeView>();

	private NodeView currentNodeView = null;

	private List<UHSNode> historyArray = new ArrayList<UHSNode>();
	private List<UHSNode> futureArray = new ArrayList<UHSNode>();
	private boolean showAllOverride = false;

	private TextView nodeTitleLbl = null;
	private FrameLayout nodeViewHolder = null;
	private ImageButton backBtn = null;
	private ImageButton forwardBtn = null;
	private TextView revealedLbl = null;
	private ImageButton revealNextBtn = null;


	/** Called when the activity is first created. */
	@Override
	public void onCreate( Bundle savedInstanceState ) {
		super.onCreate( savedInstanceState );

		this.setContentView( R.layout.reader );

		toolbar = (Toolbar)findViewById( R.id.readerToolbar );
		toolbar.setTitle( "" );  // Ensure it's non-null so support will defer its text to Toolbar.
		this.setSupportActionBar( toolbar );

		nodeTitleLbl = (TextView)findViewById( R.id.nodeTitleLbl );
		nodeViewHolder = (FrameLayout)findViewById( R.id.nodeViewHolder );
		backBtn = (ImageButton)findViewById( R.id.backBtn );
		forwardBtn = (ImageButton)findViewById( R.id.forwardBtn );
		revealedLbl = (TextView)findViewById( R.id.revealedLbl );
		revealNextBtn = (ImageButton)findViewById( R.id.revealNextBtn );

		backBtn.setEnabled( false );
		forwardBtn.setEnabled( false );

		backBtn.setOnClickListener( this );
		forwardBtn.setOnClickListener( this );
		revealNextBtn.setOnClickListener( this );

		registerNodeView( new DefaultNodeView( this ) );
		registerNodeView( new ImageNodeView( this ) );
		registerNodeView( new AudioNodeView( this ) );
		registerNodeView( new HotSpotNodeView( this ) );
		registerNodeView( new RootNodeView( this ) );

		reset();

		// Check bundle args for a file to open.
		String uhsPath = null;
		if ( savedInstanceState == null ) {
			Bundle extras = this.getIntent().getExtras();
			if ( extras == null ) {
				uhsPath = null;
			} else {
				uhsPath = extras.getString( EXTRA_OPEN_FILE );
			}
		}
		else {
			uhsPath = (String)savedInstanceState.getSerializable( EXTRA_OPEN_FILE );
		}

		if ( uhsPath != null ) openFile( new File( uhsPath ) );
	}


	@Override
	public boolean onCreateOptionsMenu( Menu menu ) {
		MenuInflater inflater = this.getMenuInflater();
		inflater.inflate( R.menu.reader_menu, menu );
		return true;
	}

	@Override
	public boolean onOptionsItemSelected( MenuItem item ) {
		switch ( item.getItemId() ) {
			case R.id.showAllHintsAction:
				showAllOverride = !item.isChecked();
				item.setChecked( showAllOverride );

				if ( showAllOverride ) {  // Reveal all hints here if it is now checked.
					while ( revealNext() != false );
				}
				return true;

			case R.id.switchToDownloaderAction:
				Intent intent = new Intent().setClass( this, DownloaderActivity.class );
				intent.setFlags( Intent.FLAG_ACTIVITY_NEW_TASK | IntentCompat.FLAG_ACTIVITY_CLEAR_TASK | IntentCompat.FLAG_ACTIVITY_TASK_ON_HOME );
				this.startActivity( intent );
				finish();
				return true;

			default:
				return super.onOptionsItemSelected( item );
		}
	}


	@Override
	public void onClick( View v ) {
		switch ( v.getId() ) {
			case R.id.backBtn:
				setReaderNode( historyArray.get( historyArray.size()-1 ) );
				break;

			case R.id.forwardBtn:
				setReaderNode( futureArray.get( futureArray.size()-1 ) );
				break;

			case R.id.revealNextBtn:
				revealNext();
				break;
		}
	}


	/**
	 * Registers a reusable NodeView to handle a UHSNode class (and its subclasses).
	 *
	 * <p>When needed, views will be searched (in reverse order of
	 * registration) for the first one whose accept() method returns true.</p>
	 *
	 * <p>Registered views determine the result of isNodeVisitable().</p>
	 *
	 * @see isNodeVisitable(UHSNode)
	 * @see net.vhati.openuhs.androidreader.reader.NodeView.accept(UHSNode)
	 */
	public void registerNodeView( NodeView nodeView ) {
		nodeViewRegistry.add( nodeView );
	}

	/**
	 * Returns a previously registered NodeView capable of representing a UHSNode, or null.
	 *
	 * @see #registerNodeView(NodeView)
	 */
	protected NodeView getViewForNode( UHSNode node ) {
		for ( int i=nodeViewRegistry.size()-1; i >= 0 ; i-- ) {
			NodeView nv = nodeViewRegistry.get( i );
			if ( nv.accept( node ) ) return nv;
		}
		return null;
	}


	/**
	 * Clears everything.
	 */
	public void reset() {
		historyArray.clear();
		futureArray.clear();
		backBtn.setEnabled( false );
		forwardBtn.setEnabled( false );
		//findBtn.setEnabled( false );

		revealedLbl.setText( "" );
		revealNextBtn.setEnabled( false );

		rootNode = null;
		currentNode = null;

		if ( currentNodeView != null ) currentNodeView.reset();
		currentNodeView = null;

		setReaderTitle( null );
	}


	/**
	 * Opens a UHS file.
	 *
	 * @param f  the location of the file
	 */
	public void openFile( final File f ) {
		UHSRootNode newRootNode = null;
		try {
			logger.info( "Reader opened \"{}\"", f.getName() );

			UHSParser uhsParser = new UHSParser();
			newRootNode = uhsParser.parseFile( f );
		}
		catch ( Exception e ) {
			Toast.makeText( this, "Parsing failed.", Toast.LENGTH_LONG ).show();
			logger.error( "Unreadable file or parsing error", e );
			// TODO: Exit gracefully.
		}

		if ( newRootNode != null ) {
			reset();
			setReaderRootNode( newRootNode );
		}
	}


	/**
	 * Displays a new UHS tree.
	 *
	 * @param newRootNode  the new root node
	 */
	public void setReaderRootNode( UHSRootNode newRootNode ) {
		reset();
		rootNode = newRootNode;
		//findBtn.setEnabled( true );
		setReaderNode( rootNode );

		String title = rootNode.getUHSTitle();
		setReaderTitle( (( title != null ) ? title : "") );
	}


	/**
	 * Displays a new node within the current tree.
	 *
	 * <p>If the node is the same as the next/prev one, breadcrumbs will be traversed.</p>
	 *
	 * @param newNode  the new node
	 */
	@Override
	public void setReaderNode( UHSNode newNode ) {
		if ( newNode == null ) {return;}

		NodeView newNodeView = getViewForNode( newNode );
		if ( newNodeView == null ) {
			Toast.makeText( this, "That node is not supported by this reader.", Toast.LENGTH_LONG ).show();
			logger.error( "The reader has no registered NodeViews for node ({}) with content: {}", newNode.getClass().getCanonicalName(), newNode.getPrintableContent() );
			return;
		}
		else {
			logger.debug( "Setting reader's node view to: {}", newNodeView.getClass().getCanonicalName() );
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
		backBtn.setEnabled( (historyArray.size() > 0) );
		forwardBtn.setEnabled( (futureArray.size() > 0) );

		currentNode = newNode;

		if ( currentNodeView != null ) currentNodeView.reset();

		if ( currentNodeView != newNodeView ) {
			nodeViewHolder.removeAllViews();

			currentNodeView = newNodeView;
			currentNodeView.setNavCtrl( this );
			nodeViewHolder.addView( currentNodeView );
		}
		currentNodeView.setNode( currentNode, showAllOverride );

		nodeTitleLbl.setText( currentNodeView.getTitle() );

		if ( currentNodeView.isRevealSupported() ) {
			revealedLbl.setText( currentNode.getCurrentReveal() +"/"+ currentNode.getMaximumReveal() );
		} else {
			revealedLbl.setText( "" );
		}
		revealNextBtn.setEnabled( !currentNodeView.isComplete() );

		nodeViewHolder.invalidate();
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
			Toast.makeText( this, String.format( "Could not find link target: %d", id ), Toast.LENGTH_SHORT ).show();
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

		toolbar.setTitle( readerTitle );
	}

	@Override
	public String getReaderTitle() {
		return readerTitle;
	}

	@Override
	public boolean isNodeVisitable( UHSNode node ) {
		return ( getViewForNode( node ) != null );
	}


	/**
	 * Reveals the next hint of the current node view.
	 *
	 * @return true if successful, false otherwise
	 */
	public boolean revealNext() {
		if ( currentNodeView == null ) return false;
		if ( currentNodeView.isComplete() ) return false;

		currentNodeView.revealNext();

		revealedLbl.setText( currentNodeView.getCurrentReveal() +"/"+ currentNodeView.getMaximumReveal() );
		revealNextBtn.setEnabled( !currentNodeView.isComplete() );

		return true;
	}
}
