package net.vhati.openuhs.androidreader.reader;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import net.vhati.openuhs.androidreader.reader.UHSImageView;
import net.vhati.openuhs.androidreader.reader.UHSSoundView;
import net.vhati.openuhs.androidreader.reader.UHSTextView;
import net.vhati.openuhs.androidreader.reader.UHSUnknownView;
import net.vhati.openuhs.core.UHSHotSpotNode;
import net.vhati.openuhs.core.UHSNode;


public class NodeAdapter extends BaseAdapter {
	private Context context;
	private UHSNode node = null;


	public NodeAdapter( Context context, UHSNode node, boolean showAll ) {
		super();
		this.context = context;
		setNode( node, showAll );
	}


	public void setNode( UHSNode node, boolean showAll ) {
		this.node = node;

		boolean allgroup = true;
		if ( node instanceof UHSHotSpotNode ) {
			// TODO: ...
		}
		else {
			for ( int i=0; i < node.getChildCount(); i++ ) {
				UHSNode tmpNode = node.getChild(i);
				int contentType = node.getChild(i).getContentType();

				if ( contentType == UHSNode.STRING ) {
					if ( tmpNode.isGroup() || tmpNode.isLink() ) {
						// Nothing to do on Android.
					}
					else if ( "Blank".equals( tmpNode.getType() ) == false ) {
						allgroup = false;
					}
				}
			}
		}

		if ( allgroup || showAll ) {
			node.setRevealedAmount( node.getChildCount() );
		}
	}


	/**
	 * Reveals the next child hint.
	 *
	 * <p>Note: Remember to call notifyDataSetChanged() on this adapter afterward.</p>
	 *
	 * @return the new 1-based revealed amount, or -1 if there was no more to see
	 */
	public int showNext() {
		if ( isComplete() ) return -1;
		node.setRevealedAmount( node.getRevealedAmount()+1 );
		int currentRevealedAmount = node.getRevealedAmount();  // Let the node decide how much more was revealed.

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


	@Override
	public int getCount() {
		int revealedAmt = node.getRevealedAmount();
		return (( revealedAmt != -1 ) ? revealedAmt : 0);
	}

	@Override
	public Object getItem( int position ) {
		return node.getChild( position );
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
		return 4;
	}

	/**
	 * Returns an int for categorizing View classes for getView() recycling.
	 *
	 * @return a number from 0 through getViewTypeCount()-1
	 */
	@Override
	public int getItemViewType( int position ) {
		int childContentType = node.getChild( position ).getContentType();

		if ( childContentType == UHSNode.STRING ) return 1;
		if ( childContentType == UHSNode.IMAGE ) return 2;
		if ( childContentType == UHSNode.AUDIO ) return 3;

		return 0;
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
		final LayoutInflater inflater = (LayoutInflater)context.getSystemService( Context.LAYOUT_INFLATER_SERVICE );

		int childContentType = node.getChild( position ).getContentType();
		if ( childContentType == UHSNode.STRING ) {
			if ( convertView instanceof UHSTextView ) {
				childView = convertView;
			} else {
				childView = new UHSTextView( context );
			}
			((UHSTextView)childView).setNode( node.getChild( position ) );
		}
		else if ( childContentType == UHSNode.IMAGE ) {
			if ( convertView instanceof UHSImageView ) {
				childView = convertView;
			} else {
				childView = new UHSImageView( context );
			}
			((UHSImageView)childView).setNode( node.getChild( position ) );
		}
		else if ( childContentType == UHSNode.AUDIO ) {
			if ( convertView instanceof UHSSoundView ) {
				childView = convertView;
			} else {
				childView = new UHSSoundView( context );
			}
			((UHSSoundView)childView).setNode( node.getChild( position ) );
		}
		else {
			if ( convertView instanceof UHSUnknownView ) {
				childView = convertView;
			} else {
				childView = new UHSUnknownView( context );
			}
		}

		return childView;
	}
}
