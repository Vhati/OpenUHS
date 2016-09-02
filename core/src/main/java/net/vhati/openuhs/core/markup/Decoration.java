package net.vhati.openuhs.core.markup;


public class Decoration {
	public String name = null;
	public char[] prefix = null;
	public char[] suffix = null;


	public Decoration( String name, char[] prefix, char[] suffix ) {
		this.name = name;
		this.prefix = prefix;
		this.suffix = suffix;
	}

	public boolean prefixMatches( char[] s, int index ) {
		if ( prefix == null || prefix.length == 0 ) return false;
		if ( index + prefix.length > s.length ) return false;
		for ( int i=0; i < prefix.length; i++ ) {
			if ( prefix[i] != s[index+i] ) return false;
		}
		return true;
	}

	public boolean suffixMatches( char[] s, int index ) {
		if ( suffix == null || suffix.length == 0 ) return false;
		if ( index + suffix.length > s.length ) return false;
		for ( int i=0; i < suffix.length; i++ ) {
			if ( suffix[i] != s[index+i] ) return false;
		}
		return true;
	}
}
