package net.vhati.openuhs.androidreader.reader;

import android.content.Context;
import android.widget.TextView;

import net.vhati.openuhs.core.UHSHotSpotNode;
import net.vhati.openuhs.core.UHSNode;


public class UHSHotSpotView extends TextView {

  public UHSHotSpotView(Context context) {
    super(context);
    setClickable(false);
    setFocusable(false);
  }

  public void setNode(UHSNode node) {
    if ((node instanceof UHSHotSpotNode) == false) {
      this.setText("^NON-HOTSPOT NODE^");
    }
    else {
      // TODO: Handle child nodes.
      this.setText("^IMAGES AREN'T SUPPORTED YET^");
    }
  }
}
