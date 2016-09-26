package net.vhati.openuhs.desktopreader;

import java.io.InputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;


public class UHSUtil {
	private static List<FormatSignature> sigList = createDefaultSignatureList();
	private static int maxSigLength = getMaximumSignatureLength();


	private static List<FormatSignature> createDefaultSignatureList() {
		List<FormatSignature> result = new ArrayList<FormatSignature>();

		result.add( new FormatSignature( "jpg", new byte[] {(byte)0xFF, (byte)0xD8} ) );
		result.add( new FormatSignature( "gif", new byte[] {(byte)0x47, (byte)0x49, (byte)0x46, (byte)0x38, (byte)0x37, (byte)0x61} ) );
		result.add( new FormatSignature( "gif", new byte[] {(byte)0x47, (byte)0x49, (byte)0x46, (byte)0x38, (byte)0x39, (byte)0x61} ) );
		result.add( new FormatSignature( "png", new byte[] {(byte)0x89, (byte)0x50, (byte)0x4E, (byte)0x47, (byte)0x0D, (byte)0x0A, (byte)0x1A, (byte)0x0A} ) );
		result.add( new FormatSignature( "wav", new byte[] {(byte)0x52, (byte)0x49, (byte)0x46, (byte)0x46} ) );

		return result;
	}

	public static void registerSignature( FormatSignature sig ) {
		sigList.add( sig );
		maxSigLength = getMaximumSignatureLength();
	}

	/**
	 * Returns the number of bytes that will be read in a call to guessFileExtension().
	 *
	 * @return the length of the longest registered file format signature
	 * @see #guessFileExtension(InputStream)
	 */
	public static int getMaximumSignatureLength() {
		int result = 0;
		for ( FormatSignature sig : sigList ) {
			result = Math.max( result, sig.bytes.length );
		}
		return result;
	}


	/**
	 * Returns the appropriate extension, bsaed on recognizing file format signatures.
	 *
	 * <p>This method does not close the provided InputStream after the read
	 * operation has completed; it is the responsibility of the caller to
	 * close the stream, if desired.</p>
	 *
	 * @param is  an InputStream to inspect
	 * @return jpg, gif, png, wav, or bin (if unknown)
	 * @see #getMaximumSignatureLength()
	 */
	public static String guessFileExtension( InputStream is ) throws IOException {
		byte[] buf = new byte[maxSigLength];

		int offset = 0;
		int count = 0;
		while ( offset < buf.length && (count=is.read( buf, offset, buf.length-offset )) >= 0 ) {
			offset += count;
		}
		if ( offset < buf.length ) {
			throw new IOException( "Could not completely read signature bytes" );
		}

		for ( FormatSignature sig : sigList ) {
			if ( arrayContains( buf, 0, sig.bytes ) ) {
				return sig.extension;
			}
		}
		return "bin";
	}


	/**
	 * Returns true if an array's elements appear inside another array.
	 *
	 * @param a  haystack
	 * @param offset  starting index in haystack for the comparison
	 * @param b  needle
	 * @return true if every byte of needle matches haystack, beginning at start
	 */
	private static boolean arrayContains( byte[] a, int offset, byte[] b ) {
		if ( a.length < offset + b.length ) return false;

		for ( int i=0; i < b.length; i++ ) {
			if ( a[offset+i] != b[i] ) return false;
		}
		return true;
	}


	/**
	 * The signatire of a known file format.
	 *
	 * <p>This associates magic numbers with a file name extension.</p>
	 */
	public static class FormatSignature {
		public final String extension;
		public final byte[] bytes;

		public FormatSignature( String extension, byte[] bytes ) {
			this.extension = extension;
			this.bytes = bytes;
		}
	}
}
