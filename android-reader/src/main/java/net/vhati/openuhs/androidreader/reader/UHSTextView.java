package net.vhati.openuhs.androidreader.reader;

import android.content.Context;
import android.graphics.Color;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.vhati.openuhs.androidreader.R;
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.core.markup.DecoratedFragment;


public class UHSTextView extends LinearLayout {
	private int textColorDefault = 0xffffffff;  // Clobbered later.
	private int textColorVisitable = 0xff33b5e5;    // or 0xff177bbd;
	private int textColorLink = 0xff33e5b5;
	private int textColorHyperlink = Color.MAGENTA;
	// TODO: Fetch from xml. context.getResources().getColor(R.color.red)

	private TextView contentLbl;
	private UHSNode node = null;
	private boolean visitable = false;
	private String overrideText = null;


	public UHSTextView( Context context ) {
		super( context );

		this.setOrientation( LinearLayout.VERTICAL );
		this.setClickable( false );
		this.setFocusable( false );

		this.inflate( context, R.layout.uhs_text_view, this );
		contentLbl = (TextView)this.findViewById( R.id.contentText );
		textColorDefault = contentLbl.getTextColors().getDefaultColor();
	}

	/**
	 * Sets a node to represent.
	 *
	 * @param node  the node
	 * @param visitable  true if the reader supports visiting this node, false otherwise
	 */
	public void setNode( UHSNode node, boolean visitable ) {
		this.node = node;
		this.visitable = visitable;
		contentChanged();
	}

	/**
	 * Sets a style indicating to the user that this node can be visited.
	 */
	public void setVisitable( boolean b ) {
		if ( visitable == b ) return;

		visitable = b;
		contentChanged();
	}

	public boolean isVisitable() {
		return visitable;
	}

	/**
	 * Sets a string to display instead of a UHSNode's content.
	 * <p>
	 * The string will be styled /as if/ that were the node's content.
	 *
	 * @param s  the text, or null for none
	 */
	public void setOverrideText( String s ) {
		overrideText = s;
		contentChanged();
	}

	public String getOverrideText() {
		return overrideText;
	}


	public void contentChanged() {
		if ( visitable ) {
			contentLbl.setTextColor( textColorVisitable );
		}
		else if ( node != null && node.isLink() ) {
			contentLbl.setTextColor( textColorLink );
		}
		else {
			contentLbl.setTextColor( textColorDefault );
		}

		if ( overrideText != null ) {
			contentLbl.setText( overrideText );
		}
		else if ( node != null ) {
			DecoratedFragment[] fragments = node.getDecoratedStringFragments();

			if ( fragments != null ) {
				SpannableStringBuilder buf = new SpannableStringBuilder();

				for ( int i=0; i < fragments.length; i++ ) {
					Object styleObj = null;
					for ( int a=0; a < fragments[i].attributes.length; a++ ) {
						if ( "Monospaced".equals( fragments[i].attributes[a] ) ) {
							styleObj = new TypefaceSpan( "monospace" );
							break;
						}
						else if ( "Hyperlink".equals( fragments[i].attributes[a] ) ) {
							styleObj = new ForegroundColorSpan( textColorHyperlink );
							break;
						}
					}
					buf.append( fragments[i].fragment );

					if ( styleObj != null ) {
						buf.setSpan( styleObj, buf.length()-fragments[i].fragment.length(), buf.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE );
					}
				}
				contentLbl.setText( buf );
			}
			else {
				contentLbl.setText( (String)node.getRawStringContent() );
			}
		}
		else {
			contentLbl.setText( "" );
		}
	}
}
