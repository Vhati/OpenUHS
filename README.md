OpenUHS
=======

This is an open source reader for Universal Hint System files. It's a game assistant that gradually reveals hints/images in a non-linear FAQ-like manner rather than spoiling everything as a walkthrough would. Over 500 games are known to the UHS repository.

The core features are all here: sections, text, hints, clickable images, links, and sounds. It can decrypt all hints from current UHS files (88a-96a, something no previous unofficial readers could do), but it safely ignores parts it doesn't recognize yet.

The end-user features are essentially complete. Switch to the Downloader tab, refresh the catalog, download a few hint files, and open them in the reader (double-clicking their titles is a shortcut).

The underlying parser code is separated to be useful as a library in other developers' projects, though that aspect is beta.

The official Windows-only shareware reader and hint repository that inspired this project can be found at [http://uhs-hints.com/](http://uhs-hints.com/).

<a href="https://raw.github.com/Vhati/OpenUHS/master/img/reader_mi2.png"><img src="https://raw.github.com/Vhati/OpenUHS/master/img/reader_mi2.png" width="145px" height="auto" /></a>
<a href="https://raw.github.com/Vhati/OpenUHS/master/img/downloader_catalog.png"><img src="https://raw.github.com/Vhati/OpenUHS/master/img/downloader_catalog.png" width="145px" height="auto" /></a>
<a href="https://raw.github.com/Vhati/OpenUHS/master/img/reader_tlj.png"><img src="https://raw.github.com/Vhati/OpenUHS/master/img/reader_tlj.png" width="145px" height="auto" /></a>
<a href="https://raw.github.com/Vhati/OpenUHS/master/img/reader_overseer.png"><img src="https://raw.github.com/Vhati/OpenUHS/master/img/reader_overseer.png" width="145px" height="auto" /></a>

To download compiled binaries, [click here](https://sourceforge.net/projects/openuhs/).

I can accept PayPal donations [here](http://vhati.github.io/donate.html).
That would be fantastic.


Requirements
------------
* Windows/OSX/Linux
* Java (1.6 or higher).
    * http://www.java.com/en/download/
* WinXP SP1 can't run Java 1.7.
    * (1.7 was built with VisualStudio 2010, causing a DecodePointer error.)
    * To get 1.6, you may have to google "jdk-6u45-windows-i586.exe".
