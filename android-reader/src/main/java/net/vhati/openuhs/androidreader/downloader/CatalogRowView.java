package net.vhati.openuhs.androidreader.downloader;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.vhati.openuhs.androidreader.R;


public class CatalogRowView extends LinearLayout {
	protected ImageView icon;
	protected TextView titleLbl;


	public CatalogRowView( Context context ) {
		super( context );
		this.setOrientation( LinearLayout.HORIZONTAL );

		this.inflate( context, R.layout.catalog_row, this );
		this.icon = (ImageView)this.findViewById( R.id.catalogRowIcon );
		this.titleLbl = (TextView)this.findViewById( R.id.catalogRowTitleLbl );

		// The png was copied from the Android-10 platform.
		//((CatalogRowView)view).getIcon().setImageResource( android.R.drawable.checkbox_off_background );
	}

	public CatalogRowView( Context context, AttributeSet attrs ) {
		this( context );
	}

	public CatalogRowView( Context context, AttributeSet attrs, int defStyle ) {
		this( context );
	}


	public ImageView getIcon() {
		return icon;
	}

	public TextView getTitleLabel() {
		return titleLbl;
	}
}
