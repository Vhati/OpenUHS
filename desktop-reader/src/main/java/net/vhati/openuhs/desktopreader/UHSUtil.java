package net.vhati.openuhs.desktopreader;


public class UHSUtil {


	/**
	 * Returns the appropriate extension, given raw bytes.
	 *
	 * @return jpg, gif, png, wav, or bin
	 */
	public static String getFileExtension( byte[] content ) {
		String extension = null;

		if ( content.length > 4 && arrayContains( content, 0, new byte[]{(byte)0xFF, (byte)0xD8} ) && arrayContains( content, content.length-2, new byte[]{(byte)0xFF, (byte)0xD9} ) ) {
			extension = "jpg";
		}
		else if ( content.length > 6 && arrayContains( content, 0, new byte[]{(byte)0x47, (byte)0x49, (byte)0x46, (byte)0x38} ) && (content[4]==(byte)0x37 || content[4]==(byte)0x39) && content[5]==(byte)0x61 ) {
			extension = "gif";
		}
		else if ( arrayContains( content, 0, new byte[]{(byte)0x89, (byte)0x50, (byte)0x4E, (byte)0x47, (byte)0x0D, (byte)0x0A, (byte)0x1A, (byte)0x0A} ) ) {
			extension = "png";
		}
		else if ( arrayContains( content, 0, new byte[]{(byte)0x52, (byte)0x49, (byte)0x46, (byte)0x46} ) && arrayContains(content, 8, new byte[]{(byte)0x57, (byte)0x41, (byte)0x56, (byte)0x45, (byte)0x66, (byte)0x6D, (byte)0x74, (byte)0x20}) ) {
			extension = "wav";
		}
		else extension = "bin";

		return extension;
	}


	/**
	 * Returns true if an array's contents appears inside another array.
	 *
	 * @param a  haystack
	 * @param start  starting index for comparison in haystack
	 * @param b  needle
	 */
	private static boolean arrayContains( byte[] a, int start, byte[] b ) {
		if ( a.length < start + b.length ) return false;
		for ( int i=0; i < b.length; i++ ) {
			if ( a[start+i] != b[i] ) return false;
		}
		return true;
	}
}
