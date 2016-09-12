package net.vhati.openuhs.androidreader.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import net.vhati.openuhs.androidreader.reader.NodeView;
import net.vhati.openuhs.core.HotSpot;
import net.vhati.openuhs.core.UHSImageNode;
import net.vhati.openuhs.core.UHSNode;


public class ImageNodeView extends NodeView {
	protected UHSImageNode imageNode = null;


	public ImageNodeView( Context context ) {
		super( context );
		setClickable( false );
		setFocusable( false );
	}

	@Override
	public boolean accept( UHSNode node ) {
		if ( node instanceof UHSImageNode ) return true;
		return false;
	}

	@Override
	public void setNode( UHSNode node, boolean showAll ) {
		reset();
		if ( !accept( node ) ) return;

		super.setNode( node, showAll );
		imageNode = (UHSImageNode)node;

		byte[] imageBytes = imageNode.getRawImageContent();
		Bitmap imageBitmap = BitmapFactory.decodeByteArray( imageBytes, 0, imageBytes.length );

		ImageView image = new ImageView( this.getContext() );
		image.setImageBitmap( imageBitmap );
		this.addView( image );
	}

	@Override
	public void reset() {
		imageNode = null;
		this.removeAllViews();
		super.reset();
		this.invalidate();
	}
}
