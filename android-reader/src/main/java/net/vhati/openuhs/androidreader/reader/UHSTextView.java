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
	private int textColorGroup = 0xff33b5e5;    // or 0xff177bbd;
	private int textColorLink = 0xff33e5b5;
	private int textColorHyperlink = Color.MAGENTA;
	// TODO: Fetch from xml. context.getResources().getColor(R.color.red)

	private TextView contentLabel;


	public UHSTextView(Context context) {
		super(context);

		this.setOrientation(LinearLayout.VERTICAL);
		this.setClickable(false);
		this.setFocusable(false);

		//LayoutInflater inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		//View view = inflater.inflate(R.layout.uhs_text_view, null);

		this.inflate(context, R.layout.uhs_text_view, this);
		contentLabel = (TextView)this.findViewById(R.id.contentText);
		textColorDefault = contentLabel.getTextColors().getDefaultColor();
	}


	public void setNode(UHSNode node) {
		if (node.getContentType() != UHSNode.STRING) {
			contentLabel.setText("^NON-STRING CONTENT^");
		}
		else {
			if (node.getStringContentDecorator() != null) {
				SpannableStringBuilder buf = new SpannableStringBuilder();
				DecoratedFragment[] fragments = node.getDecoratedStringContent();
				for (int i=0; i < fragments.length; i++) {
					Object styleObj = null;
					for (int a=0; a < fragments[i].attributes.length; a++) {
						if (fragments[i].attributes[a].equals("Monospaced")) {
							styleObj = new TypefaceSpan("monospace");
							break;
						}
						else if (fragments[i].attributes[a].equals("Hyperlink")) {
							styleObj = new ForegroundColorSpan(textColorHyperlink);
							break;
						}
					}

					buf.append(fragments[i].fragment);
					if (styleObj != null) {
						buf.setSpan(styleObj, buf.length()-fragments[i].fragment.length(), buf.length(), Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
					}
				}
				contentLabel.setText(buf);
			}
			else {
				contentLabel.setText((String)node.getContent());
			}
		}

		if (node.isGroup()) {
			contentLabel.setTextColor(textColorGroup);
		} else if (node.isLink()) {
			contentLabel.setTextColor(textColorLink);
		} else {
			contentLabel.setTextColor(textColorDefault);
		}
	}
}
