package net.vhati.openuhs.androidreader.reader;

import android.content.Context;
import android.widget.TextView;

import net.vhati.openuhs.androidreader.reader.*;
import net.vhati.openuhs.core.*;


public class UHSUnknownView extends TextView {

  public UHSUnknownView(Context context) {
    super(context);
    setClickable(false);
    setFocusable(false);

    this.setText("^UNKNOWN CONTENT^");
    //this.setTextSize(12);
    //this.setTextColor(Color.BLACK);
    //this.setTypeface(Typeface.SANS_SERIF);
  }
}
