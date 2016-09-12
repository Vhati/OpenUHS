package net.vhati.openuhs.androidreader.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import net.vhati.openuhs.androidreader.reader.NodeView;
import net.vhati.openuhs.core.HotSpot;
import net.vhati.openuhs.core.UHSHotSpotNode;
import net.vhati.openuhs.core.UHSImageNode;
import net.vhati.openuhs.core.UHSNode;


public class HotSpotNodeView extends NodeView {
	protected UHSHotSpotNode hotspotNode = null;


	public HotSpotNodeView( Context context ) {
		super( context );
		setClickable( false );
		setFocusable( false );
	}

	@Override
	public boolean accept( UHSNode node ) {
		if ( node instanceof UHSHotSpotNode ) return true;
		return false;
	}

	@Override
	public void setNode( UHSNode node, boolean showAll ) {
		reset();
		if ( !accept( node ) ) return;

		super.setNode( node, showAll );
		hotspotNode = (UHSHotSpotNode)node;

		byte[] mainImageBytes = hotspotNode.getRawImageContent();
		Bitmap mainImageBitmap = BitmapFactory.decodeByteArray( mainImageBytes, 0, mainImageBytes.length );

		ImageView mainImage = new ImageView( this.getContext() );
		mainImage.setImageBitmap( mainImageBitmap );
		this.addView( mainImage );

			//RelativeLayout.LayoutParams mainImageLP = new RelativeLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT );
			//this.addView( mainImage, mainImageLP );

			// http://stackoverflow.com/questions/28578701/create-android-shape-background-programmatically

			// http://www.higherpass.com/Android/Tutorials/Working-With-Images-In-Android/2/

			// http://stackoverflow.com/questions/19538747/how-to-use-both-ontouch-and-onclick-for-an-imagebutton

			// http://stackoverflow.com/questions/11847111/how-to-get-image-width-that-has-been-scaled

			// http://stackoverflow.com/questions/10180336/wrong-imageview-dimensions

		for ( int i=0; i < node.getChildCount(); i++ ) {
			UHSNode childNode = node.getChild( i );
			String childType = childNode.getType();

			if ( "Overlay".equals( childType ) && childNode instanceof UHSImageNode ) {
				UHSImageNode overlayNode = (UHSImageNode)childNode;
				String title = overlayNode.getDecoratedStringContent();

				HotSpot spot = hotspotNode.getSpot( overlayNode );

				byte[] overlayImageBytes = overlayNode.getRawImageContent();

				// TODO.
			}
			else {
				String text = childNode.getDecoratedStringContent();

				HotSpot spot = hotspotNode.getSpot( childNode );

				// TODO.
			}
		}
	}

	@Override
	public void reset() {
		hotspotNode = null;
		this.removeAllViews();
		super.reset();
		this.invalidate();
	}
}
