package net.vhati.openuhs.core.markup;

import java.util.LinkedHashMap;


public class DecoratedFragment {
  public String fragment = null;
  public String[] attributes = null;
  public LinkedHashMap[] argMaps = null;

  public DecoratedFragment(String fragment, String[] attributes, LinkedHashMap[] argMaps) {
    this.fragment = fragment;
    this.attributes = attributes;
    this.argMaps = argMaps;
  }
}
