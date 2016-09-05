package net.vhati.openuhs.androidreader.reader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.ImageView;
import android.widget.TextView;

import net.vhati.openuhs.core.UHSHotSpotNode;
import net.vhati.openuhs.core.UHSNode;


public class UHSHotSpotView extends RelativeLayout {

	public UHSHotSpotView( Context context ) {
		super( context );
		setClickable( false );
		setFocusable( false );
	}

	public void setNode( UHSNode node ) {
		this.removeAllViews();

		if ( (node instanceof UHSHotSpotNode) == false ) {
			TextView tv = new TextView( this.getContext() );
			tv.setText( "^NON-HOTSPOT NODE^" );
			RelativeLayout.LayoutParams tvLP = new RelativeLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT );
			this.addView( tv, tvLP );
		}
		else {
			// TODO: Handle child nodes.
			if ( node.getChildCount() > 0 && node.getChild( 0 ).getContentType() == UHSNode.IMAGE ) {
				UHSNode firstChildNode = node.getChild( 0 );
				byte[] imageBytes = (byte[])firstChildNode.getContent();
				Bitmap bMap = BitmapFactory.decodeByteArray( imageBytes, 0, imageBytes.length );

				ImageView baseImage = new ImageView( this.getContext() );
				baseImage.setImageBitmap( bMap );
				RelativeLayout.LayoutParams baseImageLP = new RelativeLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT );
				this.addView( baseImage, baseImageLP );

				// http://stackoverflow.com/questions/28578701/create-android-shape-background-programmatically

				// http://www.higherpass.com/Android/Tutorials/Working-With-Images-In-Android/2/

				// http://stackoverflow.com/questions/19538747/how-to-use-both-ontouch-and-onclick-for-an-imagebutton

				// http://stackoverflow.com/questions/11847111/how-to-get-image-width-that-has-been-scaled

				// http://stackoverflow.com/questions/10180336/wrong-imageview-dimensions
			}
			else {
				TextView tv = new TextView( this.getContext() );
				tv.setText( "^UNEXPECTED HOTSPOT NODE STRUCTURE^" );
				RelativeLayout.LayoutParams tvLP = new RelativeLayout.LayoutParams( ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT );
				this.addView( tv, tvLP );
			}
		}
	}
}
