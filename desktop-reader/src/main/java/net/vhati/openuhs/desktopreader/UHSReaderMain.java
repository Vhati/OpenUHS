package net.vhati.openuhs.desktopreader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import joptsimple.util.RegexMatcher;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.filter.ThresholdFilter;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.filter.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.vhati.openuhs.core.Proto4xUHSParser;
import net.vhati.openuhs.core.UHSAudioNode;
import net.vhati.openuhs.core.UHSImageNode;
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.core.UHSParser;
import net.vhati.openuhs.core.UHSRootNode;
import net.vhati.openuhs.core.UHSWriter;
import net.vhati.openuhs.desktopreader.UHSReaderFrame;
import net.vhati.openuhs.desktopreader.UHSXML;


public class UHSReaderMain {
	public static final String VERSION = "0.7.0";

	private static final Logger logger = LoggerFactory.getLogger( UHSReaderMain.class );


	public static void main( String[] args ) {
		OptionParser parser = new OptionParser();
		OptionSpec<Void> optionHelp = parser.acceptsAll( Arrays.asList( "h", "help" ) ).forHelp();
		OptionSpec<Void> optionVersion = parser.accepts( "version" );
		OptionSpec<Void> optionForce88a = parser.accepts( "force-88a", "parse 9x hint files as if using an 88a reader" );
		OptionSpec<Void> optionBatch = parser.acceptsAll( Arrays.asList( "b", "batch" ), "disable the default GUI and log file" );
		OptionSpec<String> optionLoglevel = parser.acceptsAll( Arrays.asList( "v", "loglevel" ), "set console logging level" ).withRequiredArg().withValuesConvertedBy( new RegexMatcher( "debug|info|warn|error|off", Pattern.CASE_INSENSITIVE ) );
		OptionSpec<Void> optionHintTitle = parser.accepts( "hint-title", "print the hint file's title" );
		OptionSpec<Void> optionHintVersion = parser.accepts( "hint-version", "print the hint file's declared version" );
		OptionSpec<Void> optionSaveXml = parser.accepts( "save-xml", "extract text as xml (in this dir)" );
		OptionSpec<Void> optionSaveBin = parser.accepts( "save-bin", "extract embedded binaries (in this dir)" );
		OptionSpec<Void> optionSave88a = parser.accepts( "save-88a", "save as 88a format (in this dir)" );
		OptionSpec<Void> optionSave9x = parser.accepts( "save-9x", "save as 9x format (in this dir)" );
		OptionSpec<Void> optionPrint = parser.acceptsAll( Arrays.asList( "p", "print" ), "print the hint file's content as indented text" );
		OptionSpec<File> optionScanDir = parser.accepts( "scan-dir", "scan all files in a dir for parse errors" ).withRequiredArg().describedAs( "dir" ).ofType( File.class );
		OptionSpec<File> optionEtc = parser.nonOptions().ofType( File.class );

		try {
			OptionSet options = parser.parse( args );

			// A way to test multiple options.
			// List<OptionSpec<?>> foundOptions = options.specs();
			// if ( Collections.disjoint( foundOptions, Arrays.asList( optionTest, optionPrint ) ) ) ///...

			if ( options.has( optionHelp ) ) {
				try {
					parser.printHelpOn( System.out );
				}
				catch ( IOException e ) {
					logger.error( "Error printing help message", e );
					throw new ExitException();
				}
				System.exit( 0 );
			}
			else if ( options.has( optionVersion ) ) {
				showVersion();
				System.exit( 0 );
			}

			File etcFile = options.valueOf( optionEtc );  // Assume a single value, not a list.
			UHSRootNode rootNode = null;

			if ( options.has( optionLoglevel ) ) {
				Level newLevel = null;

				if ( "off".equalsIgnoreCase( options.valueOf( optionLoglevel ) ) ) {
					newLevel = Level.OFF;
				}
				else if ( "error".equalsIgnoreCase( options.valueOf( optionLoglevel ) ) ) {
					newLevel = Level.ERROR;
				}
				else if ( "warn".equalsIgnoreCase( options.valueOf( optionLoglevel ) ) ) {
					newLevel = Level.WARN;
				}
				else if ( "info".equalsIgnoreCase( options.valueOf( optionLoglevel ) ) ) {
					newLevel = Level.INFO;
				}
				else if ( "debug".equalsIgnoreCase( options.valueOf( optionLoglevel ) ) ) {
					newLevel = Level.DEBUG;
				}
				if ( newLevel != null ) {
					ch.qos.logback.classic.Logger classicRootLogger = (ch.qos.logback.classic.Logger)LoggerFactory.getLogger( Logger.ROOT_LOGGER_NAME );

					// Find the console's threshold filter.
					Iterator<Appender<ILoggingEvent>> appIt = classicRootLogger.iteratorForAppenders();
					boolean done = false;
					while ( appIt.hasNext() && !done ) {
						Appender<ILoggingEvent> appender = appIt.next();

						if ( appender instanceof ConsoleAppender ) {

							for ( Filter f : appender.getCopyOfAttachedFiltersList() ) {
								if ( f instanceof ThresholdFilter ) {
									((ThresholdFilter)f).setLevel( newLevel.toString() );
									done = true;
									break;
								}
							}
						}
					}
				}
			}

			// Scan  an entire dir for parse errors, discard rootNodes.
			if ( options.has( optionScanDir ) ) {
				File scanDir = options.valueOf( optionScanDir );

				for ( File f : scanDir.listFiles() ) {
					try {
						UHSRootNode tmpRootNode = null;
						if ( f.getName().matches( "(?i).*[.]uhs$" ) ) {
							logger.info( "Scanning \"{}\"", f.getName() );
							UHSParser uhsParser = new UHSParser();

							if ( options.has( optionForce88a ) ) {
								uhsParser.setForce88a( true );
							}
							tmpRootNode = uhsParser.parseFile( f );
						}
						else if ( f.getName().matches( "(?i).*[.]puhs" ) ) {
							logger.info( "Scanning \"{}\"", f.getName() );
							Proto4xUHSParser protoParser = new Proto4xUHSParser();

							tmpRootNode = protoParser.parseFile( f );
						}

						validateNode( tmpRootNode, tmpRootNode );
					}
					catch ( Exception e ) {
						logger.error( "Parsing \"{}\" failed", f.getName(), e );
					}
				}
			}

			if ( etcFile != null ) {
				try {
					if ( etcFile.getName().matches( "(?i).*[.]uhs$" ) ) {
						UHSParser uhsParser = new UHSParser();

						if ( options.has( optionForce88a ) ) {
							uhsParser.setForce88a( true );
						}
						rootNode = uhsParser.parseFile( etcFile );
					}
					else if ( etcFile.getName().matches( "(?i).*[.]puhs" ) ) {
						Proto4xUHSParser protoParser = new Proto4xUHSParser();

						rootNode = protoParser.parseFile( etcFile );
					}
				}
				catch ( Exception e ) {
					logger.error( "Parsing \"{}\" failed", etcFile.getName(), e );
				}

				if ( rootNode == null ) {
					throw new ExitException();
				}
			}

			if ( rootNode != null ) {
				logger.debug( "Parsing \"{}\" succeeded", etcFile.getName() );

				if ( options.has( optionHintTitle ) ) {
					String hintTitle = rootNode.getUHSTitle();
					System.out.println( String.format( "Title: %s", ((hintTitle != null) ? hintTitle : "Unknown") ) );
				}
				if ( options.has( optionHintVersion ) ) {
					String hintVersion = rootNode.getUHSVersion();
					System.out.println( String.format( "Version: %s", ((hintVersion != null) ? hintVersion : "Unknown") ) );
				}
				if ( options.has( optionPrint ) ) {
					rootNode.printNode("", "\t", System.out);
				}
				if ( options.has( optionSaveXml ) ) {
					String basename = etcFile.getName().replaceAll( "[.][^.]*$", "" );
					FileOutputStream xmlOS = null;
					try {
						xmlOS = new FileOutputStream( "./"+ basename +".xml" );
						UHSXML.exportTree( rootNode, basename +"_", xmlOS );
					}
					catch ( IOException e ) {
						logger.error( "Exporting to xml failed", e );
						throw new ExitException();
					}
					finally {
						try {if ( xmlOS != null ) xmlOS.close();} catch ( IOException e ) {}
					}
				}
				if ( options.has( optionSaveBin ) ) {
					String basename = etcFile.getName().replaceAll( "[.][^.]*$", "" );
					try {
						extractNode( rootNode, new File( "./" ), basename +"_", 1 );
					}
					catch ( IOException e ) {
						logger.error( "Extracting binary content failed", e );
						throw new ExitException();
					}
				}
				if ( options.has( optionSave88a ) ) {
					UHSWriter uhsWriter = new UHSWriter();
					String basename = etcFile.getName().replaceAll( "[.][^.]*$", "" );
					File outFile = new File( "./"+ basename +".uhs" );
					FileOutputStream fos = null;
					try {
						fos = new FileOutputStream( outFile );
						uhsWriter.write88Format( rootNode, fos );
					}
					catch ( IOException e ) {
						logger.error( "Exporting to 88a format failed", e );
						throw new ExitException();
					}
					finally {
						try {if ( fos != null ) fos.close();} catch ( IOException e ) {}
					}
				}
				if ( options.has( optionSave9x ) ) {
					UHSWriter uhsWriter = new UHSWriter();
					String basename = etcFile.getName().replaceAll( "[.][^.]*$", "" );
					File outFile = new File( "./"+ basename +".uhs" );
					FileOutputStream fos = null;
					try {
						fos = new FileOutputStream( outFile );
						uhsWriter.write9xFormat( rootNode, fos );
					}
					catch ( IOException e ) {
						logger.error( "Exporting to 9x format failed", e );
						throw new ExitException();
					}
					finally {
						try {if ( fos != null ) fos.close();} catch ( IOException e ) {}
					}
				}
			}
			// Done with CLI.

			if ( !options.has( optionBatch ) ) {
				// Fork log into a file.
				LoggerContext lc = (LoggerContext)LoggerFactory.getILoggerFactory();

				PatternLayoutEncoder encoder = new PatternLayoutEncoder();
				encoder.setContext( lc );
				encoder.setCharset( Charset.forName( "UTF-8" ) );
				encoder.setPattern( "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{5} - %msg%n" );
				encoder.start();

				FileAppender<ILoggingEvent> fileAppender = new FileAppender<ILoggingEvent>();
				fileAppender.setContext( lc );
				fileAppender.setFile( new File( "./openuhs-log.txt" ).getAbsolutePath() );
				fileAppender.setAppend( false );
				fileAppender.setEncoder( encoder );
				fileAppender.start();

				lc.getLogger( "net.vhati.openuhs" ).addAppender( fileAppender );

				// Log a welcome message.
				logger.debug( "Started: {}", (new Date()) );
				logger.debug( "OS: {} {}", System.getProperty( "os.name" ), System.getProperty( "os.version" ) );
				logger.debug( "VM: {}, {}, {}", System.getProperty( "java.vm.name" ), System.getProperty( "java.version" ), System.getProperty( "os.arch" ) );

				// Set a Swing Look and Feel.
				try {
					//for ( UIManager.LookAndFeelInfo info : UIManager.getInstalledLookAndFeels() ) {
					//  if ( "Nimbus".equals( info.getName() ) ) {
					//    UIManager.setLookAndFeel( info.getClassName() );
					//    break;
					//  }
					//}

					//logger.info( "Using system Look and Feel" );
					//UIManager.setLookAndFeel( UIManager.getSystemLookAndFeelClassName() );
				}
				catch ( Exception e ) {
					logger.error( "Error setting system Look and Feel.", e );
					throw new ExitException();
				}

				final UHSRootNode finalRootNode = rootNode;  // May be null.
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						guiInit( finalRootNode );
					}
				});
			}
		}
		catch ( ExitException e ) {
			System.exit( 1 );
		}
	}

	private static void guiInit( UHSRootNode rootNode ) {
		UHSReaderFrame frame = new UHSReaderFrame();
		frame.setTitlePrefix( "OpenUHS "+ UHSReaderMain.VERSION );
		frame.setTitle( null );
		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		frame.setSize( 400, 400 );
		frame.setLocationRelativeTo( null );
		frame.setVisible( true );

		if ( rootNode != null ) {
			frame.getUHSReaderPanel().setReaderRootNode( rootNode );
		}

		// Get the JFileChooser cached.
		try {
			Class.forName( "javax.swing.JFileChooser" );
		}
		catch( ClassNotFoundException e ) {
			logger.error( "Could not cache JFileChooser", e );
		}
	}


	/**
	 * Recursively scans a node and its descendents for log-worthy problems.
	 *
	 * <p>This method doesn't return anything. It just calls methods not
	 * encountered during basic parsing to give loggers a chance to complain.</p>
	 *
	 * <p><ul>
	 * <li>String content decorators may complain about markup.</li>
	 * <li>Nodes may have link target ids which have not been registered.</li>
	 * </ul></p>
	 */
	public static void validateNode( UHSRootNode rootNode, UHSNode currentNode ) {
		if ( currentNode.getStringContentDecorator() != null ) {
			currentNode.getDecoratedStringFragments();
		}

		int linkTarget = currentNode.getLinkTarget();
		if ( linkTarget != -1 ) {
			if ( rootNode.getNodeByLinkId( linkTarget ) == null ) {
				logger.warn( "Node has an unresolvable link target id: {}", linkTarget );
			}
		}
		else {
			if ( currentNode.getChildren() != null ) {
				for ( UHSNode tmpNode : currentNode.getChildren() ) {
					validateNode( rootNode, tmpNode );
				}
			}
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
	public static int extractNode( UHSNode currentNode, File destDir, String basename, int n ) throws IOException {
		boolean extractable = false;
		if ( currentNode instanceof UHSImageNode ) {
			extractable = true;
		} else if ( currentNode instanceof UHSAudioNode ) {
			extractable = true;
		}

		if ( extractable == true ) {
			int id = currentNode.getId();
			String idStr = (( id == -1 ) ? "" : "_"+id);

			byte[] content = null;
			if ( currentNode instanceof UHSImageNode ) {
				UHSImageNode imageNode = (UHSImageNode)currentNode;
				content = imageNode.getRawImageContent();
			}
			else if ( currentNode instanceof UHSAudioNode ) {
				UHSAudioNode audioNode = (UHSAudioNode)currentNode;
				content = audioNode.getRawAudioContent();
			}

			String extension = UHSUtil.getFileExtension( content );

			File destFile = new File( destDir, (basename + n + idStr +"."+ extension) );
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream( destFile );
				fos.write( content );
			}
			catch ( IOException e ) {
				throw new IOException( String.format( "Error extracting %s node's binary content to a file: %s", currentNode.getType(), destFile.getAbsolutePath() ), e );
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



	/**
	 * An exception to throw so finally blocks can finish before System.exit(1).
	 */
	private static class ExitException extends RuntimeException {
		public ExitException() {
		}

		public ExitException( String message ) {
			super( message );
		}

		public ExitException( Throwable cause ) {
			super( cause );
		}

		public ExitException( String message, Throwable cause ) {
			super( message, cause );
		}
	}
}
