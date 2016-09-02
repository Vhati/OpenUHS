package net.vhati.openuhs.core.markup;

import java.util.Map;


public class DecoratedFragment {
	public String fragment = null;
	public String[] attributes = null;
	public Map[] argMaps = null;


	public DecoratedFragment( String fragment, String[] attributes, Map[] argMaps ) {
		this.fragment = fragment;
		this.attributes = attributes;
		this.argMaps = argMaps;
	}
}
