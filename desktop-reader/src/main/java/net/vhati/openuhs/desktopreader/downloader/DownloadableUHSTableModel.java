package net.vhati.openuhs.desktopreader.downloader;

import javax.swing.*;
import javax.swing.table.*;
import java.util.*;


public class DownloadableUHSTableModel extends AbstractTableModel {
  public static int SORT_TITLE = 0;
  public static int SORT_DATE = 1;
  public static int SORT_FULLSIZE = 2;
  public static int SORT_NAME = 3;

  private int sortOrder = SORT_TITLE;
  Vector dataVector = new Vector();
  Vector colVector = new Vector();


  public DownloadableUHSTableModel(String[] columnNames) {
    for (int i=0; i < columnNames.length; i++) {
      colVector.add(columnNames[i]);
    }
  }

  public int getRowCount() {return dataVector.size();}
  public int getColumnCount() {return colVector.size();}

  public String getColumnName(int column) {
    if (column < 0 || column >= colVector.size()) return null;

    return (String)colVector.get(column);
  }


  public Object getValueAt(int row, int column) {
    if (column < 0 || column >= colVector.size() || row < 0 || row >= dataVector.size()) return null;

    Object value = null;
    if (getColumnName(column).equals("Title")) {
      value = ((DownloadableUHS)dataVector.get(row)).getTitle();
    }
    else if (getColumnName(column).equals("Name")) {
      value = ((DownloadableUHS)dataVector.get(row)).getName();
    }
    else if (getColumnName(column).equals("Date")) {
      value = ((DownloadableUHS)dataVector.get(row)).getDate();
    }
    else if (getColumnName(column).equals("Size")) {
      value = ((DownloadableUHS)dataVector.get(row)).getCompressedSize();
    }
    else if (getColumnName(column).equals("FullSize")) {
      value = ((DownloadableUHS)dataVector.get(row)).getFullSize();
    }
    return value;
  }

  public boolean isCellEditable(int x, int y) {
    return false;
  }


  public void addUHS(DownloadableUHS du) {
    dataVector.add(du);
    this.fireTableDataChanged();
  }

  public void addUHSs(DownloadableUHS[] dus) {
    for (int i=0; i < dus.length; i++) {
      dataVector.add(dus[i]);
    }
    this.fireTableDataChanged();
  }


  public void removeUHSs(int[] indeces) {
    Vector indexVector = new Vector();
    for (int i=0; i < indeces.length; i++) {
      indexVector.add( new Integer(indeces[i]) );
    }
    Collections.sort(indexVector);

    for (int i=indexVector.size()-1; i >= 0; i--) {
      dataVector.remove( ((Integer)indexVector.get(i)).intValue() );
    }
    this.fireTableDataChanged();
  }


  public DownloadableUHS getUHS(int row) {
    if (row < 0 || row >= dataVector.size()) return null;
    return (DownloadableUHS)dataVector.get(row);
  }


  public void clear() {
    dataVector.clear();
    this.fireTableDataChanged();
  }


  public void sort() {
    Collections.sort(dataVector, new Comparator() {
      public int compare(Object a, Object b) {
        if (a != null && b != null && a instanceof DownloadableUHS && b instanceof DownloadableUHS) {
          DownloadableUHS dA = (DownloadableUHS)a;
          DownloadableUHS dB = (DownloadableUHS)b;
          if (sortOrder == SORT_TITLE) return dA.getTitle().compareTo(dB.getTitle());
          if (sortOrder == SORT_DATE) return dA.getDate().compareTo(dB.getDate()) * -1;
          if (sortOrder == SORT_FULLSIZE) {
            if (dA.getFullSize().matches("^[0-9]+$") && dB.getFullSize().matches("^[0-9]+$")) {
              if (dA.getFullSize().length() > dB.getFullSize().length())
                return 1;
              if (dA.getFullSize().length() < dB.getFullSize().length())
                return -1;
            }
            return dA.getFullSize().compareTo(dB.getFullSize());
          }
          if (sortOrder == SORT_NAME) return dA.getName().compareTo(dB.getName());
        }
        return 1;
      }
    });
    this.fireTableDataChanged();
  }

  public void sort(int n) {
    sortOrder = n;
    sort();
  }
}
