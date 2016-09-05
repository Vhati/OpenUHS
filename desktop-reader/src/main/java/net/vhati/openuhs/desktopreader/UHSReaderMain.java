package net.vhati.openuhs.desktopreader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import net.vhati.openuhs.core.DefaultUHSErrorHandler;
import net.vhati.openuhs.core.Proto4xUHSParser;
import net.vhati.openuhs.core.UHSErrorHandler;
import net.vhati.openuhs.core.UHSErrorHandlerManager;
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.core.UHSParser;
import net.vhati.openuhs.core.UHSRootNode;
import net.vhati.openuhs.core.UHSWriter;
import net.vhati.openuhs.desktopreader.UHSReaderFrame;
import net.vhati.openuhs.desktopreader.UHSXML;


public class UHSReaderMain {
	public static final String VERSION = "0.7.0";

	private static final String OPTION_CLI          = "OPTION_CLI";
	private static final String OPTION_HELP         = "OPTION_HELP";
	private static final String OPTION_VERSION      = "OPTION_VERSION";
	private static final String OPTION_OPEN_88A     = "OPTION_OPEN_88A";
	private static final String OPTION_TEST         = "OPTION_TEST";
	private static final String OPTION_HINT_TITLE   = "OPTION_HINT_TITLE";
	private static final String OPTION_HINT_VERSION = "OPTION_HINT_VERSION";
	private static final String OPTION_PRINT_TEXT   = "OPTION_PRINT_TEXT";
	private static final String OPTION_SAVE_XML     = "OPTION_SAVE_XML";
	private static final String OPTION_SAVE_BIN     = "OPTION_SAVE_BIN";
	private static final String OPTION_SAVE_88A     = "OPTION_SAVE_88A";
	private static final String OPTION_SAVE_9X      = "OPTION_SAVE_9X";

	private static DefaultUHSErrorHandler errorHandler = new DefaultUHSErrorHandler( System.err );
	private static UHSReaderFrame frame = null;

	private static String fileArg = null;


	public static void main( String[] args ) {
		Map<String, Boolean> optionMap = new HashMap<String, Boolean>();
			optionMap.put( OPTION_CLI, Boolean.FALSE );
			optionMap.put( OPTION_OPEN_88A, Boolean.FALSE );
			optionMap.put( OPTION_TEST, Boolean.FALSE );
			optionMap.put( OPTION_HINT_TITLE, Boolean.FALSE );
			optionMap.put( OPTION_HINT_VERSION, Boolean.FALSE );
			optionMap.put( OPTION_PRINT_TEXT, Boolean.FALSE );
			optionMap.put( OPTION_SAVE_XML, Boolean.FALSE );
			optionMap.put( OPTION_SAVE_BIN, Boolean.FALSE );
			optionMap.put( OPTION_SAVE_88A, Boolean.FALSE );
			optionMap.put( OPTION_SAVE_9X, Boolean.FALSE );
		parseArgs( args, optionMap );

		UHSErrorHandlerManager.setErrorHandler( errorHandler );

		if ( optionMap.get( OPTION_OPEN_88A ) == Boolean.TRUE ) UHSParser.setForce88a( true );

		if ( optionMap.get( OPTION_CLI ) == Boolean.TRUE ) {
			if (optionMap.get( OPTION_TEST ) == Boolean.TRUE) {
				errorHandler = null;
				UHSErrorHandlerManager.setErrorHandler( errorHandler );
			}

      
			UHSRootNode rootNode = null;
			if ( fileArg.matches( "(?i).*[.]uhs$" ) ) {
				UHSParser uhsParser = new UHSParser();
				rootNode = uhsParser.parseFile( new File( fileArg ), UHSParser.AUX_NEST );
			}
			else if ( fileArg.matches( "(?i).*[.]puhs" ) ) {
				Proto4xUHSParser protoParser = new Proto4xUHSParser();
				rootNode = protoParser.parseFile( new File( fileArg ), Proto4xUHSParser.AUX_NEST );
			}

			if ( rootNode == null ) {
				if ( optionMap.get( OPTION_TEST ) == Boolean.TRUE ) {
					System.out.println( "Test: Parsing failed" );
				} else {
					System.out.println( "Error: Unreadable file or parsing error" );
				}
				System.exit( 1 );
			} else {
				if ( optionMap.get( OPTION_TEST ) == Boolean.TRUE )
					System.out.println( "Test: Parsing succeeded" );
				if ( optionMap.get( OPTION_HINT_TITLE ) == Boolean.TRUE ) {
					String hintTitle = rootNode.getUHSTitle();
					System.out.println( String.format( "Title: %s", ((hintTitle != null) ? hintTitle : "Unknown") ) );
				}
				if ( optionMap.get( OPTION_HINT_VERSION ) == Boolean.TRUE ) {
					String hintVersion = rootNode.getUHSVersion();
					System.out.println( String.format( "Version: %s", ((hintVersion != null) ? hintVersion : "Unknown") ) );
				}
				if ( optionMap.get( OPTION_PRINT_TEXT ) == Boolean.TRUE ) {
					rootNode.printNode("", "\t", System.out);
				}
				if ( optionMap.get( OPTION_SAVE_XML ) == Boolean.TRUE ) {
					String basename = (new File( fileArg )).getName().replaceAll( "[.][^.]*$", "" );
					FileOutputStream xmlOS = null;
					try {
						xmlOS = new FileOutputStream( "./"+ basename +".xml" );
						UHSXML.exportTree( rootNode, basename +"_", xmlOS );
					}
					catch ( IOException e ) {
						if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, null, "Could not export xml", 0, e );
					}
					finally {
						try {if ( xmlOS != null ) xmlOS.close();} catch ( IOException e ) {}
					}
				}
				if ( optionMap.get( OPTION_SAVE_BIN ) == Boolean.TRUE ) {
					String basename = (new File( fileArg )).getName().replaceAll( "[.][^.]*$", "" );
					extractNode( rootNode, new File( "./" ), basename +"_", 1 );
				}
				if ( optionMap.get( OPTION_SAVE_88A ) == Boolean.TRUE ) {
					UHSWriter uhsWriter = new UHSWriter();
					String basename = (new File( fileArg )).getName().replaceAll( "[.][^.]*$", "" );
					FileOutputStream fos = null;
					try {
						fos = new FileOutputStream( "./"+ basename +".uhs" );
						uhsWriter.write88Format( rootNode, fos );
					}
					catch ( IOException e ) {
						if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, null, "Could not write 88a file", 0, e );
					}
					finally {
						try {if ( fos != null ) fos.close();} catch ( IOException e ) {}
					}
				}
				if ( optionMap.get( OPTION_SAVE_9X ) == Boolean.TRUE ) {
					UHSWriter uhsWriter = new UHSWriter();
					String basename = (new File( fileArg )).getName().replaceAll( "[.][^.]*$", "" );
					File outFile = new File( "./"+ basename +".uhs" );

					try {
						uhsWriter.write9xFormat( rootNode, outFile );
					}
					catch ( IOException e ) {
						if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, null, "Could not write 9x file", 0, e );
					}
				}
				System.exit( 0 );
			}
		}


		FileOutputStream logOS = null;
		try {
			logOS = new FileOutputStream( "./log.txt" );
			errorHandler = new DefaultUHSErrorHandler( new PrintStream[] {new PrintStream( logOS, true ), System.err} );
		}
		catch ( IOException e ) {
			e.printStackTrace();
		}
		finally {
			try {if ( logOS != null ) logOS.close();} catch ( IOException e ) {}
		}
		if ( errorHandler != null ) errorHandler.logWelcomeMessage();

		try {
			//for ( UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels() ) {
			//  if ( "Nimbus".equals( info.getName() ) ) {
			//    UIManager.setLookAndFeel( info.getClassName() );
			//    break;
			//  }
			//}

			//if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.INFO, null, "Using system Look and Feel", 0, null );
			//UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
		}
		catch ( Exception e ) {
			if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, null, "Error setting system Look and Feel.", 0, e );
		}

		final File guiFile = (( fileArg != null ) ? new File( fileArg ) : null);
		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				guiInit( guiFile );
			}
		});
	}

	private static void guiInit( File guiFile ) {
		UHSReaderFrame frame = new UHSReaderFrame();
		frame.setTitlePrefix( "OpenUHS "+ UHSReaderMain.VERSION );
		frame.setTitle( null );
		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		frame.setSize( 400, 400 );
		frame.setLocationRelativeTo( null );
		frame.setVisible( true );

		if ( guiFile != null ) {
			frame.getUHSReaderPanel().openFile( guiFile );
		}

		// Get the JFileChooser cached.
		try {
			Class.forName( "javax.swing.JFileChooser" );
		}
		catch( ClassNotFoundException e ) {
			if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, null, "Could not cache JFileChooser", 0, e );
		}
	}


	/**
	 * Recursively extracts the contents of a node and its children to files.
	 *
	 * <p>Extensions are guessed.</p>
	 *
	 * @param currentNode  a node to start extracting from
	 * @param destDir  the destination dir
	 * @param basename  prefix for extracted files
	 * @param n  a number for uniqueness, incrementing with each file
	 * @return a new value for n
	 * @see net.vhati.openuhs.desktopreader.UHSUtil#getFileExtension(byte[])
	 */
	public static int extractNode( UHSNode currentNode, File destDir, String basename, int n ) {
		boolean extractable = false;
		if (currentNode.getContentType() == UHSNode.IMAGE) extractable = true;
		else if (currentNode.getContentType() == UHSNode.AUDIO) extractable = true;

		if ( extractable == true ) {
			int id = currentNode.getId();
			String idStr = (( id == -1 ) ? "" : "_"+id);

			byte[] content = (byte[])currentNode.getContent();
			String extension = UHSUtil.getFileExtension( content );

			FileOutputStream fos = null;
			try {
				File destFile = new File( destDir, (basename + n + idStr +"."+ extension) );
				fos = new FileOutputStream( destFile );
				fos.write( content );
			}
			catch ( IOException e ) {
				if ( errorHandler != null ) errorHandler.log( UHSErrorHandler.ERROR, null, "Could not save a binary", 0, e );
			}
			finally {
				try {if ( fos != null ) fos.close();} catch ( IOException e ) {}
			}
			n++;
		}

		for (int i=0; i < currentNode.getChildCount(); i++) {
			n = extractNode( currentNode.getChild( i ), destDir, basename, n );
		}

		return n;
	}


	/**
	 * Does some getopt magic.
	 *
	 * @param argv  app args
	 * @param optionMap  a map to fill with Boolean.TRUE values
	 */
	private static void parseArgs( String[] argv, Map<String, Boolean> optionMap ) {
		boolean needFileArg = false;

		//StringBuffer sb = new StringBuffer();
		LongOpt[] longopts = new LongOpt[] {
			new LongOpt( "help", LongOpt.NO_ARGUMENT, null, 'h' ),
			new LongOpt( "version", LongOpt.NO_ARGUMENT, null, 2 ),
			new LongOpt( "open-as-88a", LongOpt.NO_ARGUMENT, null, 3 ),
			new LongOpt( "test", LongOpt.NO_ARGUMENT, null, 't' ),
			new LongOpt( "hint-title", LongOpt.NO_ARGUMENT, null, 4 ),
			new LongOpt( "hint-version", LongOpt.NO_ARGUMENT, null, 5 ),
			new LongOpt( "save-xml", LongOpt.NO_ARGUMENT, null, 6 ),
			new LongOpt( "save-bin", LongOpt.NO_ARGUMENT, null, 7 ),
			new LongOpt( "save-88a", LongOpt.NO_ARGUMENT, null, 8 ),
			new LongOpt( "save-9x", LongOpt.NO_ARGUMENT, null, 9 ),
			new LongOpt( "print", LongOpt.NO_ARGUMENT, null, 'p' )
			//longopts[1] = new LongOpt( "outputdir", LongOpt.REQUIRED_ARGUMENT, sb, 'o' );
			//longopts[2] = new LongOpt( "maximum", LongOpt.OPTIONAL_ARGUMENT, null, 2 );
		};

		// One Colon: Req Arg; Two colons: Optional Arg; W+Semicolon: allow -Wsome-argarg

		Getopt g = new Getopt( "OpenUHS", argv, "-:tpehW;", longopts );
			g.setOpterr( false );  // We'll do our own error handling

		boolean optFailed = false;
		boolean optDone = false;
		int c;
		String arg;
		while ( optDone == false && (c = g.getopt()) != -1 ) {
			switch ( c ) {
				//case 0:
				// Long opts with a StringBuffer have their char stored there, and trigger the 0 case
				//arg = g.getOptarg();
				//System.err.println("Got long option with value '"+ (char)(new Integer( sb.toString() )).intValue() +"' with argument "+ (( arg != null ) ? arg : "null"));
				//break;

				case 1:
					// Non-options (no leading dash) trigger 1, and their invalid letter is 1's arg
					//   This case can happen when GetOpt's opt string starts with a "-" for return_in_order
					g.setOptind( g.getOptind()-1 );
					optDone = true;
					break;

				case 2:
					// Long opts without short equivs are declared with int vals (such as 2)
					optionMap.put( OPTION_VERSION, Boolean.TRUE );
					optionMap.put( OPTION_CLI, Boolean.TRUE );
					//arg = g.getOptarg();
					//System.err.println("We picked option "+ longopts[g.getLongind()].getName() +" with value "+ (( arg != null ) ? arg : "null"));
					break;

				//case 'd':
				// An opt with an arg
				//arg = g.getOptarg();
				//System.err.println("You picked option '"+ (char)c +"' with argument "+ (( arg != null ) ? arg : "null"));
				//break;

				case 3:
					optionMap.put( OPTION_OPEN_88A, Boolean.TRUE );
					needFileArg = true;
					break;

				case 't':
					optionMap.put( OPTION_TEST, Boolean.TRUE );
					optionMap.put( OPTION_CLI, Boolean.TRUE );
					needFileArg = true;
					break;

				case 4:
					optionMap.put( OPTION_HINT_TITLE, Boolean.TRUE );
					optionMap.put( OPTION_CLI, Boolean.TRUE );
					needFileArg = true;
					break;

				case 5:
					optionMap.put( OPTION_HINT_VERSION, Boolean.TRUE );
					optionMap.put( OPTION_CLI, Boolean.TRUE );
					needFileArg = true;
					break;

				case 6:
					optionMap.put( OPTION_SAVE_XML, Boolean.TRUE );
					optionMap.put( OPTION_CLI, Boolean.TRUE );
					needFileArg = true;
					break;

				case 7:
					optionMap.put( OPTION_SAVE_BIN, Boolean.TRUE );
					optionMap.put( OPTION_CLI, Boolean.TRUE );
					needFileArg = true;
					break;

				case 8:
					optionMap.put( OPTION_SAVE_88A, Boolean.TRUE );
					optionMap.put( OPTION_CLI, Boolean.TRUE );
					needFileArg = true;
					break;

				case 9:
					optionMap.put( OPTION_SAVE_9X, Boolean.TRUE );
					optionMap.put( OPTION_CLI, Boolean.TRUE );
					needFileArg = true;
					break;

				case 'p':
					optionMap.put( OPTION_PRINT_TEXT, Boolean.TRUE );
					optionMap.put( OPTION_CLI, Boolean.TRUE );
					needFileArg = true;
					break;

				case 'h':
					optionMap.put( OPTION_HELP, Boolean.TRUE );
					optionMap.put( OPTION_CLI, Boolean.TRUE );
					break;

				case 'W':
					System.err.println( "Error: -W did not set a valid a long option" );
					optFailed = true;
					break;

				case ':':
					// Invalid long opts will be seen as '0' and can't be identified
					System.err.println( "Error: Option requires an argument: "+ (char)g.getOptopt() );
					optFailed = true;
					break;

				case '?':
					// Invalid long opts will be seen as '0' and can't be identified
					System.err.println( "Error: Option is not valid: "+ (char)g.getOptopt() );
					optFailed = true;
					break;

				default:
					System.err.println( "Error: Unexpected case: getopt() returned "+ c );
					optFailed = true;
					break;
			}
		}
		// These are non-opts or anything after "--"
		for ( int i=g.getOptind(); i < argv.length; i++ ) {
			if ( fileArg == null ) {
				fileArg = argv[i];
			} else {
				System.err.println( "Error: Extraneous argument: '"+ argv[i] +"'" );
				optFailed = true;
			}
		}


		if ( needFileArg && fileArg == null ) {
			optFailed = true;
			System.err.println( "Error: No file was specified" );
		}

		if ( optFailed == true ) {
			System.err.println(""); showHelp(1);
		}
		else if ( optionMap.get( OPTION_HELP ) == Boolean.TRUE ) {
			showHelp(0);
		}
		else if ( optionMap.get( OPTION_VERSION ) == Boolean.TRUE ) {
			showVersion();
		}
	}


	public static void showHelp( int exitCode ) {
		System.out.println( "Usage: OpenUHS [OPTION] [FILE]" );
		System.out.println( "Reader for UHS files." );
		System.out.println( "" );
		System.out.println( "General Options:" );
		System.out.println( "      --open-as-88a    parse hidden 88a section of 9x hint files" );
		System.out.println( "" );
		System.out.println( "CLI Options:" );
		System.out.println( "  -t, --test           quietly test parse and report success/failure" );
		System.out.println( "      --hint-title     print the hint file's title" );
		System.out.println( "      --hint-version   print the hint file's declared version" );
		System.out.println( "  -p, --print          print hints as indented plain text" );
		System.out.println( "      --save-xml       extract text as xml" );
		System.out.println( "      --save-bin       extract embedded binaries" );
		System.out.println( "      --save-88a       save as 88a UHS" );
		System.out.println( "      --save-9x        save as 9x UHS (not ready for use)" );
		System.out.println( "" );
		System.out.println( "  -h, --help           display this help and exit" );
		System.out.println( "      --version        output version information and exit" );
		System.out.println( "" );
		System.out.println( "Extracted/saved files are written to the current directory." );
		System.out.println( "" );
		System.exit( exitCode );
	}

	public static void showVersion() {
		System.out.println( "OpenUHS "+ UHSReaderMain.VERSION );
		System.out.println( "Copyright (C) 2007-2009, 2011, 2012, 2016 David Millis" );
		System.out.println( "" );
		System.out.println( "This program is free software; you can redistribute it and/or modify" );
		System.out.println( "it under the terms of the GNU General Public License as published by" );
		System.out.println( "the Free Software Foundation; version 2." );
		System.out.println( "" );
		System.out.println( "This program is distributed in the hope that it will be useful," );
		System.out.println( "but WITHOUT ANY WARRANTY; without even the implied warranty of" );
		System.out.println( "MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the" );
		System.out.println( "GNU General Public License for more details." );
		System.out.println( "" );
		System.out.println( "You should have received a copy of the GNU General Public License" );
		System.out.println( "along with this program. If not, see http://www.gnu.org/licenses/." );
		System.out.println( "" );
		System.exit( 0 );
	}
}
