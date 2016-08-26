OpenUHS
https://github.com/Vhati/OpenUHS

By David Millis (tvtronix@yahoo.com)


About

  This is an open source reader for Universal Hint System files. It's a game assistant that gradually reveals hints/images in a non-linear FAQ-like manner rather than spoiling everything as a walkthrough would. Over 500 games are known to the UHS repository.

  The core features are all here: sections, text, hints, clickable images, links, and sounds. It can decrypt all hints from current UHS files (88a-96a, something no previous unofficial readers could do), but it safely ignores parts it doesn't recognize yet.

  The end-user features are essentially complete. Switch to the Downloader tab, refresh the catalog, download a few hint files, and open them in the reader (double-clicking their titles is a shortcut).

  The underlying parser code is separated to be useful as a library in other developers' projects, though that aspect is beta.


Requirements

  * Windows/OSX/Linux

  * Java (1.6 or higher).
    http://www.java.com/en/download/

  * WinXP SP1 can't run Java 1.7.
    (1.7 was built with VisualStudio 2010, causing a DecodePointer error.)
    To get 1.6, you may have to google "jdk-6u45-windows-i586.exe".


Usage

On Windows,   Double-click OpenUHS.bat to get straight to the GUI.
              OR
              Run OpenUHS.bat from commandline with arguments.

On Linux/OSX, Double-click OpenUHS.command to get straight to the GUI.
              OR
              Run OpenUHS.sh from commandline with arguments.


Troubleshooting

* If you get "java.lang.UnsupportedClassVersionError" on startup...
    You need a newer version of Java.


Addendum

  "Space Quest 4 (in German)"

  This file is non-standard and will not be supported. Four questions each contain a lone linefeed in their first hint. It's 88a, so a text editor can see the indented lines and remove the linebreak to make it parsable, at least.


Acknowledgments

  The official Windows-only shareware reader and hint repository that inspired this project can be found at http://uhs-hints.com/ .

  Stefan Wolff. His primer( http://www.swolff.dk/uhs/ ) on the file structure gave me somewhere to start.
