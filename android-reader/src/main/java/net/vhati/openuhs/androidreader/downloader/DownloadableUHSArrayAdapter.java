package net.vhati.openuhs.androidreader.downloader;

import java.util.ArrayList;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import net.vhati.openuhs.androidreader.downloader.*;


public class DownloadableUHSArrayAdapter extends ArrayAdapter {
  private int resXmlId = -1;
  private int resImgId = -1;
  private int resLblId = -1;


  public DownloadableUHSArrayAdapter(Context context, int resource, int imageViewResourceId, int textViewResourceId, ArrayList objects) {
    super(context, resource, textViewResourceId, objects);
    resXmlId = resource;
    resImgId = imageViewResourceId;
    resLblId = textViewResourceId;
  }


  @Override
  public View getView(int position, View convertView, ViewGroup parent) {
    LayoutInflater inflater = (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View row = inflater.inflate(resXmlId, parent, false);
    Object rowItem = getItem(position);

    TextView label = (TextView)row.findViewById(resLblId);
    ImageView icon = (ImageView)row.findViewById(resImgId);

    if (rowItem instanceof DownloadableUHS) {
      DownloadableUHS rowUHS = (DownloadableUHS)rowItem;

      label.setText(rowUHS.getTitle());

      //if (position%2 == 0) icon.setImageResource(android.R.drawable.checkbox_on_background);

      icon.setImageResource(android.R.drawable.checkbox_off_background);
      if (rowUHS.getColor() != -1) icon.setBackgroundColor(rowUHS.getColor());
    } else {
      label.setText("??? #"+ position);
    }

    return row;
  }
}
