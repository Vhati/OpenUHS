package net.vhati.openuhs.androidreader.downloader;

import java.util.List;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import net.vhati.openuhs.androidreader.downloader.DownloadableUHS;


public class DownloadableUHSArrayAdapter extends ArrayAdapter<DownloadableUHS> {
  private int remoteColor = android.graphics.Color.BLACK;
  private int localColor = android.graphics.Color.DKGRAY;
  private int newerColor = android.graphics.Color.LTGRAY;

  private int resXmlId = -1;
  private int resImgId = -1;
  private int resLblId = -1;


  public DownloadableUHSArrayAdapter(Context context, int resource, int imageViewResourceId, int textViewResourceId, List<DownloadableUHS> objects) {
    super(context, resource, textViewResourceId, objects);
    resXmlId = resource;
    resImgId = imageViewResourceId;
    resLblId = textViewResourceId;
  }


  public void setRemoteColor(int c) {
    remoteColor = c;
  }

  public void setLocalColor(int c) {
    localColor = c;
  }

  public void setNewerColor(int c) {
    newerColor = c;
  }


  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View row = inflater.inflate(resXmlId, parent, false);
    DownloadableUHS rowUHS = getItem(position);

    TextView label = (TextView)row.findViewById(resLblId);
    ImageView icon = (ImageView)row.findViewById(resImgId);

    label.setText(rowUHS.getTitle());

    //if (position%2 == 0) icon.setImageResource(android.R.drawable.checkbox_on_background);

    icon.setImageResource(android.R.drawable.checkbox_off_background);
    if (rowUHS.isNewer()) {
      icon.setBackgroundColor(newerColor);
    }
    else if (rowUHS.isLocal()) {
      icon.setBackgroundColor(localColor);
    }
    else {
      icon.setBackgroundColor(remoteColor);
    }

    return row;
  }
}
