package net.vhati.openuhs.androidreader.reader;

import android.content.Context;
import android.widget.TextView;

import net.vhati.openuhs.core.UHSNode;


public class UHSImageView extends TextView {

	public UHSImageView( Context context ) {
		super( context );
		setClickable( false );
		setFocusable( false );
	}

	public void setNode( UHSNode node ) {
		if ( node.getContentType() != UHSNode.IMAGE ) {
			this.setText( "^NON-IMAGE CONTENT^" );
		}
		else {
			// TODO: Show the image.
			this.setText( "^IMAGES AREN'T SUPPORTED YET^" );
		}
	}
}
