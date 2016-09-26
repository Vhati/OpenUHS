package net.vhati.openuhs.androidreader.reader;

import java.io.InputStream;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.androidreader.AndroidUHSConstants;
import net.vhati.openuhs.androidreader.reader.NodeView;
import net.vhati.openuhs.core.ByteReference;
import net.vhati.openuhs.core.HotSpot;
import net.vhati.openuhs.core.UHSImageNode;
import net.vhati.openuhs.core.UHSNode;


public class ImageNodeView extends NodeView {

	private final Logger logger = LoggerFactory.getLogger( AndroidUHSConstants.LOG_TAG );

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

		InputStream is = null;
		try {
			ByteReference imageRef = imageNode.getRawImageContent();
			is = imageRef.getInputStream();
			Bitmap imageBitmap = BitmapFactory.decodeStream( is );
			imageView.setImageBitmap( imageBitmap );
			this.addView( imageView );
		}
		catch ( IOException e ) {
			logger.error( "Error loading binary content of {} node (\"{}\"): {}", imageNode.getType(), imageNode.getRawStringContent(), e );
			Toast.makeText( this.getContext(), "Error loading image", Toast.LENGTH_LONG ).show();
		}
		finally {
			try {if ( is != null ) is.close();} catch ( IOException e ) {}
		}

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
