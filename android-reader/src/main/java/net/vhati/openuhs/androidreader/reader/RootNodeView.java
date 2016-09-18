package net.vhati.openuhs.androidreader.reader;

import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ListView;

import net.vhati.openuhs.androidreader.R;
import net.vhati.openuhs.androidreader.reader.NodeView;
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.core.UHSRootNode;


public class RootNodeView extends NodeView {
	public static final int SCROLL_TO_TOP = 0;
	public static final int SCROLL_TO_BOTTOM = 1;
	public static final int SCROLL_IF_INCOMPLETE = 2;

	protected UHSRootNode rootNode = null;
	protected List<UHSNode> nodeList = new ArrayList<UHSNode>();
	private BaseAdapter listAdapter = null;
	private OnItemClickListener listClickListener = null;
	private ListView listView = null;


	public RootNodeView( Context context ) {
		super( context );
		this.setClickable( false );
		this.setFocusable( false );

		listAdapter = new BaseAdapter() {
			@Override
			public int getCount() {
				if ( rootNode == null ) return 0;

				return nodeList.size();
			}

			@Override
			public Object getItem( int position ) {
				return nodeList.get( position );
			}

			@Override
			public long getItemId( int position ) {
				return position;
			}

			/**
			 * Returns the number of potential View classes in this list.
			 */
			@Override
			public int getViewTypeCount() {
				return 1;
			}

			/**
			 * Returns an int for categorizing View classes for getView() recycling.
			 *
			 * @return a number from 0 through getViewTypeCount()-1
			 */
			@Override
			public int getItemViewType( int position ) {
				return 0;  // No if-else for alternate node types. Just the one view type.
			}

			/**
			 * Get a View that displays the data at the specified position in the data set.
			 *
			 * @param position  the position of the item within the adapter's data set whose view we want
			 * @param convertView  the old view to reuse, if possible (if non-null and the right type).
			 * @param parent  the parent that this view will eventually be attached to
			 */
			@Override
			public View getView( int position, View convertView, ViewGroup parent ) {
				View childView;
				//final LayoutInflater inflater = (LayoutInflater)context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );

				if ( true ) {  // No if-else for alternate node types.
					if ( convertView instanceof UHSTextView ) {
						childView = convertView;
					}
					else {
						childView = new UHSTextView( RootNodeView.this.getContext() );
					}
					UHSNode childNode = nodeList.get( position );
					((UHSTextView)childView).setNode( childNode, RootNodeView.this.getNavCtrl().isNodeVisitable( childNode ) );

					if ( "Version".equals( childNode.getType() ) ) {
						((UHSTextView)childView).setOverrideText( String.format( "%s: %s", childNode.getType(), childNode.getRawStringContent() ) );
					}
					else if ( "Incentive".equals( childNode.getType() ) ) {
						((UHSTextView)childView).setOverrideText( String.format( "%s: %s", childNode.getType(), childNode.getRawStringContent() ) );
					}
					else if ( "Info".equals( childNode.getType() ) ) {
						((UHSTextView)childView).setOverrideText( String.format( "%s: %s", childNode.getType(), childNode.getRawStringContent() ) );
					}
					else if ( "Credit".equals( childNode.getType() ) ) {
						((UHSTextView)childView).setOverrideText( String.format( "%s: %s", childNode.getType(), childNode.getRawStringContent() ) );
					}
					else {
						((UHSTextView)childView).setOverrideText( null );
					}
				}

				return childView;
			}
		};

		listClickListener = new OnItemClickListener() {
			@Override
			public void onItemClick( AdapterView<?> parent, View view, int position, long id ) {
				//logger.info( "Clicked list item #{}", position );
				if ( parent != listView ) return;

				Object o = listAdapter.getItem( position );
				if ( (o instanceof UHSNode) == false ) return;

				UHSNode childNode = (UHSNode)o;
				if ( childNode.isLink() ) {
					int targetIndex = childNode.getLinkTarget();
					RootNodeView.this.getNavCtrl().setReaderNode( targetIndex );
				}
				else if ( RootNodeView.this.getNavCtrl().isNodeVisitable( childNode ) ) {
					RootNodeView.this.getNavCtrl().setReaderNode( childNode );
				}
			}
		};
		// When list items are focusable or clickable, they preempt the list's click listener.
		// This is a workaround reminder, in case it's ever needed.
		//   android:descendantFocusability="blocksDescendants"
		//   listView.setDescendantFocusability( ViewGroup.FOCUS_BLOCK_DESCENDANTS );

		listView = (ListView)LayoutInflater.from( this.getContext() ).inflate( R.layout.reader_default_node_view_list, null, false );
		listView.setAdapter( listAdapter );
		this.addView( listView );

		listView.setOnItemClickListener( listClickListener );
	}

	@Override
	public boolean accept( UHSNode node ) {
		if ( node instanceof UHSRootNode ) return true;
		return false;
	}

	@Override
	public void setNode( UHSNode node, boolean showAll ) {
		reset();
		if ( !accept( node ) ) return;

		super.setNode( node, showAll );
		rootNode = (UHSRootNode)node;

		// Collect master Subject node's children.
		UHSNode masterSubjectNode = rootNode.getMasterSubjectNode();
		if ( masterSubjectNode != null ) {
			for ( int i=0; i < masterSubjectNode.getChildCount(); i++ ) {
				UHSNode tmpNode = masterSubjectNode.getChild( i );
				nodeList.add( tmpNode );
			}
		}

		// Divider.
		UHSNode blankNode = new UHSNode( "Blank" );
		blankNode.setRawStringContent( "--=File Info=--" );
		nodeList.add( blankNode );

		// Collect auxiliary nodes.
		for ( int i=0; i < rootNode.getChildCount(); i++ ) {
			UHSNode tmpNode = rootNode.getChild( i );
			if ( tmpNode.equals( masterSubjectNode ) ) continue;

			nodeList.add( tmpNode );
		}

		//for ( UHSNode tmpNode : nodeList ) {
		//	if ( tmpNode.isLink() || this.getNavCtrl().isNodeVisitable( tmpNode ) ) {
				// Clickable.
		//	}
		//}

		// Reveal isn't used, but setting it regardless.
		rootNode.setCurrentReveal( rootNode.getMaximumReveal() );

		listAdapter.notifyDataSetChanged();
		scrollTo( SCROLL_TO_TOP );
	}

	@Override
	public String getTitle() {
		return "";
	}

	@Override
	public void reset() {
		rootNode = null;
		nodeList.clear();
		super.reset();
		listAdapter.notifyDataSetChanged();
	}


	/**
	 * Scrolls to the top/bottom of the visible hints.
	 *
	 * @param position  either SCROLL_TO_TOP, SCROLL_TO_BOTTOM, or SCROLL_IF_INCOMPLETE
	 */
	public void scrollTo( int position ) {
		if ( position == SCROLL_IF_INCOMPLETE ) {
			if ( this.isComplete() ) {
				position = SCROLL_TO_TOP;
			} else {
				position = SCROLL_TO_BOTTOM;
			}
		}
		if ( position == SCROLL_TO_TOP ) {
			listView.smoothScrollToPosition( 0 );
		}
		else if ( position == SCROLL_TO_BOTTOM ) {
			listView.smoothScrollToPosition( listAdapter.getCount()-1 );
		}
	}
}
