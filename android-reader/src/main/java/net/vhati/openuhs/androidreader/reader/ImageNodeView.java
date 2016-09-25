package net.vhati.openuhs.androidreader.reader;

import java.io.ByteArrayInputStream;

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

	protected ImageView imageView;


	public ImageNodeView( Context context ) {
		super( context );
		setClickable( false );
		setFocusable( false );

		imageView = new ImageView( context );
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
		Bitmap imageBitmap = BitmapFactory.decodeStream( new ByteArrayInputStream( imageBytes ) );

		imageView.setImageBitmap( imageBitmap );
		this.addView( imageView );

		this.requestLayout();
		this.invalidate();
	}

	@Override
	public void reset() {
		imageView.setImageBitmap( null );

		imageNode = null;
		this.removeAllViews();
		super.reset();
		this.invalidate();
	}
}
