package net.vhati.openuhs.desktopreader;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.security.CodeSource;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
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

import net.vhati.openuhs.core.ByteReference;
import net.vhati.openuhs.core.Proto4xUHSParser;
import net.vhati.openuhs.core.UHSAudioNode;
import net.vhati.openuhs.core.UHSImageNode;
import net.vhati.openuhs.core.UHSNode;
import net.vhati.openuhs.core.UHSParser;
import net.vhati.openuhs.core.UHSRootNode;
import net.vhati.openuhs.core.UHSWriter;
import net.vhati.openuhs.desktopreader.UHSReaderConfig;
import net.vhati.openuhs.desktopreader.UHSReaderFrame;
import net.vhati.openuhs.desktopreader.UHSXML;


public class UHSReaderMain {
	public static final String APP_NAME = "OpenUHS";
	public static final String APP_VERSION = "0.7.0";
	public static final String APP_AUTHOR = "David Millis";

	private static final Logger logger = LoggerFactory.getLogger( UHSReaderMain.class );

	private static File appDir = new File( "./" );
	private static File appDataDir = new File( "./" );
	private static File userDataDir = new File( "./" );


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

		File jarDir = getJarDir( UHSReaderMain.class );
		if ( jarDir != null ) {
			appDir = jarDir.getParentFile();
			appDataDir = appDir;
			userDataDir = appDir;
		}

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

				long scanStartNano = System.nanoTime();

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

						if ( tmpRootNode != null ) validateNode( tmpRootNode, tmpRootNode );
					}
					catch ( Exception e ) {
						logger.error( "Parsing/validating \"{}\" failed", f.getName(), e );
					}
				}

				long scanDurationNano = System.nanoTime() - scanStartNano;
				logger.info( "Dir scan completed ({} seconds)", String.format( "%.2f", ((double)scanDurationNano / 1000000000) ) );
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
				logger.debug( "{} v{}", APP_NAME, APP_VERSION );
				logger.debug( "OS: {} {}", System.getProperty( "os.name" ), System.getProperty( "os.version" ) );
				logger.debug( "VM: {}, {}, {}", System.getProperty( "java.vm.name" ), System.getProperty( "java.version" ), System.getProperty( "os.arch" ) );
				logger.debug( "App Dir: {}", appDir.getAbsolutePath() );
				logger.debug( "App data Dir: {}", appDataDir.getAbsolutePath() );
				logger.debug( "User data Dir: {}", userDataDir.getAbsolutePath() );

				// Config.
				File configFile = new File( appDataDir, "openuhs.cfg" );

				boolean writeConfig = false;
				UHSReaderConfig appConfig = new UHSReaderConfig( configFile );
				appConfig.setProperty( "font_size", "12" );

				// Read the config file.
				if ( configFile.exists() ) {
					logger.debug( "Loading config from \"{}\"", configFile.getAbsolutePath() );
					try {
						appConfig.load();
					}
					catch ( IOException e ) {
						logger.error( "Error loading config", e );
					}
				} else {
					writeConfig = true; // Create a new cfg, but only if necessary.
				}

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

				if ( writeConfig ) {
					try {
						appConfig.store();
					}
					catch ( IOException e ) {
						logger.error( "Error storing config at \"{}\"", configFile.getAbsolutePath() );
					}
				}

				final UHSReaderConfig finalAppConfig = appConfig;
				final UHSRootNode finalRootNode = rootNode;  // May be null.
				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						guiInit( finalAppConfig, finalRootNode );
					}
				});
			}
		}
		catch ( ExitException e ) {
			System.exit( 1 );
		}
	}

	private static void guiInit( UHSReaderConfig appConfig, UHSRootNode rootNode ) {
		UHSReaderFrame frame = new UHSReaderFrame( appConfig );
		frame.setTitlePrefix( APP_NAME +" "+ APP_VERSION );
		frame.setTitle( null );
		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		frame.setSize( 400, 400 );
		frame.setLocationRelativeTo( null );
		frame.setVisible( true );

		frame.setAppDir( appDir );
		frame.setAppDataDir( appDataDir );
		frame.setUserDataDir( userDataDir );
		frame.init();

		if ( rootNode != null ) {
			frame.getUHSReaderPanel().setReaderRootNode( rootNode );
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
	 * Recursively extracts the binary content of a node and its children to files.
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
		ByteReference contentRef = null;
		if ( currentNode instanceof UHSImageNode ) {  // Includes UHSHotSpotNode subclass.
			UHSImageNode imageNode = (UHSImageNode)currentNode;
			contentRef = imageNode.getRawImageContent();
		}
		else if ( currentNode instanceof UHSAudioNode ) {
			UHSAudioNode audioNode = (UHSAudioNode)currentNode;
			contentRef = audioNode.getRawAudioContent();
		}

		if ( contentRef != null ) {
			int id = currentNode.getId();
			String idStr = (( id == -1 ) ? "" : "_"+id);

			File destFile = null;
			InputStream sigStream = null;;
			InputStream contentStream = null;;
			FileOutputStream fos = null;
			try {
				sigStream = contentRef.getInputStream();
				String extension = UHSUtil.guessFileExtension( sigStream );
				sigStream.close();

				destFile = new File( destDir, (basename + n + idStr +"."+ extension) );

				contentStream = contentRef.getInputStream();
				fos = new FileOutputStream( destFile );
				byte[] buf = new byte[512];
				int count;
				while ( (count=contentStream.read( buf )) != -1 ) {
					fos.write( buf, 0, count );
				}
			}
			catch ( IOException e ) {
				throw new IOException( String.format( "Error extracting binary content of %s node (\"%s\") to a file: %s", currentNode.getType(), currentNode.getRawStringContent(), (( destFile != null ) ? destFile.getAbsolutePath() : null ), e ) );
			}
			finally {
				try {if ( sigStream != null ) sigStream.close();} catch ( IOException e ) {}
				try {if ( contentStream != null ) contentStream.close();} catch ( IOException e ) {}
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
	 * Returns the parent dir of the jar containing a given class, or null.
	 */
	public static File getJarDir( Class c ) {
		try {
			File jarFile = null;

			CodeSource codeSource = c.getProtectionDomain().getCodeSource();
			if ( codeSource.getLocation() != null ) {
				jarFile = new File( codeSource.getLocation().toURI() );
			}
			else {
				String cPath = c.getResource( c.getSimpleName() +".class" ).getPath();
				// If the class is not in a jar, cPath will be "file:c:\grr\arg.class"
				// If the class is inside a jar, cPath will be "file:c:\grr\app.jar!/pkg/arg.class"

				int colonIndex = cPath.indexOf(":");
				int bangIndex = cPath.indexOf("!");
				if ( colonIndex >= 0 && colonIndex + 1 < bangIndex ) {
					String jarPath = cPath.substring( colonIndex + 1, bangIndex );
					jarFile = new File( jarPath );
				}
			}
			if ( jarFile != null ) return jarFile.getParentFile();
		}
		catch ( SecurityException e ) {
			logger.error( "Error locating jar file", e );
		}
		catch ( URISyntaxException e ) {
			logger.error( "Error locating jar file", e );
		}

		return null;
	}


	public static void showVersion() {
		System.out.println( APP_NAME +" "+ APP_VERSION );
		System.out.println( "Copyright (C) 2007-2009, 2011, 2012, 2016 "+ APP_AUTHOR );
		System.out.println( "" );
		System.out.println( "This program is free software; you can redistribute it and/or modify" );
		System.out.println( "it under the terms of the GNU General Public License as published by" );
		System.out.println( "the Free Software Foundation, either version 3 of the License, or" );
		System.out.println( "(at your option) any later version." );
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
