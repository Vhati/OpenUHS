package net.vhati.openuhs.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * UHS file writer (88a only, 9x isn't implemented).
 */
public class UHSWriter {
  private static final int ENCRYPT_NONE = 0;
  private static final int ENCRYPT_HINT = 1;
  private static final int ENCRYPT_NEST = 2;
  private static final int ENCRYPT_TEXT = 3;

  private static final Pattern crlfPtn = Pattern.compile("\r\n");


  public UHSWriter() {
  }


  /**
   * Generates an encryption key for formats after 88a.
   *
   * @param name the name of the master subject node of the UHS document (not the filename)
   * @return the key
   * @see #encryptNestString(String, int[]) encryptNestString(String, int[])
   * @see #encryptTextHunk(String, int[]) encryptTextHunk(String, int[])
   */
  public int[] generateKey(String name) {
    int[] key = new int[name.length()];
    int[] k = {'k', 'e', 'y'};
    for (int i=0; i < name.length(); i++) {
      key[i] = (int)name.charAt(i) + (k[i%3] ^ (i + 40));
      while (key[i] > 127) {
        key[i] -= 96;
      }
    }
    return key;
  }


  /**
   * Encrypts the content of standalone 'hint' hunks, and all 88a blocks.
   * <br />This is only necessary when saving a file.
   *
   * @param input plaintext
   * @return the encrypted text
   */
  public String encryptString(String input) {
    StringBuffer tmp = new StringBuffer(input.length());

    for (int i=0; i < input.length(); i++) {
      int mychar = (int)input.charAt(i);
      if (mychar < 32) {}
      else if (mychar%2 == 0) {mychar = (mychar+32)/2;}
      else {mychar = (mychar+127)/2;}

      tmp.append((char)mychar);
    }

    return tmp.toString();
  }


  /**
   * Encrypts the content of 'nesthint' and 'incentive' hunks.
   * <br />This is only necessary when saving a file.
   *
   * @param input plaintext
   * @param key this file's hint decryption key
   * @return the encrypted text
   */
  public String encryptNestString(String input, int[] key) {
    StringBuffer tmp = new StringBuffer(input.length());
    int tmpChar = 0;

    for (int i=0; i < input.length(); i++) {
      int codeoffset = i % key.length;
      tmpChar = input.charAt(i) + (key[codeoffset] ^ (i + 40));
      while (tmpChar > 126) {
        tmpChar -= 96;
      }
      tmp.append((char)tmpChar);
    }

    return tmp.toString();
  }


  /**
   * Encrypts the content of 'text' hunks.
   * <br />This is only necessary when saving a file.
   *
   * @param input plaintext
   * @param key this file's hint decryption key
   * @return the encrypted text
   */
  public String encryptTextHunk(String input, int[] key) {
    StringBuffer tmp = new StringBuffer(input.length());
    int tmpChar = 0;

    for (int i=0; i < input.length(); i++) {
      int codeoffset = i % key.length;
      tmpChar = input.charAt(i) + (key[codeoffset] ^ (codeoffset + 40));
      while (tmpChar > 126) {
        tmpChar -= 96;
      }
      tmp.append((char)tmpChar);
    }

    return tmp.toString();
  }


  /**
   * Tests whether a UHSRootNode can be expressed in 88a format.
   * <br />The rootNode must contain:
   * <pre> 0-or-more Subjects, containing:
   * - 1-or-more Questions, containing:
   * - - 1-or-more Hints
   * 1 Credit, containing a text node</pre>
   *
   * <br />All the nodes' content must be strings.
   * <br />Newlines are not part of 88a, and will be stripped.
   * <br />Accented and exotic characters will be asciified.
   * <br />Markup within text will be stripped.
   * <br />Version and Blank nodes will be omitted.
   *
   * @param rootNode an existing root node
   */
  public boolean isValid88Format(UHSRootNode rootNode) {
    boolean hasCredit = false;

    if (rootNode.getContentType() != UHSNode.STRING) return false;
    if (!rootNode.isGroup()) return false;

    List<UHSNode> levelOne = rootNode.getChildren();
    for (int o=0; o < levelOne.size(); o++) {
      UHSNode oNode = levelOne.get(o);
      String oType = oNode.getType();
      if (oType.equals("Subject")) {
        if (oNode.getContentType() != UHSNode.STRING) return false;
        if (!oNode.isGroup()) return false;

        // Check Question nodes
        List<UHSNode> levelTwo = oNode.getChildren();
        for (int t=0; t < levelTwo.size(); t++) {
          UHSNode tNode = levelTwo.get(t);
          if (tNode.getContentType() != UHSNode.STRING) return false;
          if (!tNode.isGroup()) return false;

          // Check Hint nodes
          List<UHSNode> levelThree = tNode.getChildren();
          for (int r=0; r < levelThree.size(); r++) {
            UHSNode rNode = levelThree.get(r);
            if (rNode.getContentType() != UHSNode.STRING) return false;
            if (rNode.isGroup()) return false;
          }
        }
      } else if (oType.equals("Blank")) {
      } else if (oType.equals("Version")) {
      } else if (oType.equals("Credit")) {
        if (hasCredit) return false;
        hasCredit = true;
        if (oNode.getChildCount() != 1) return false;
        // Check CreditData node
        if (oNode.getChild(0).getContentType() != UHSNode.STRING) return false;
        if (oNode.getChild(0).isGroup()) return false;
      }
      else return false;
    }
    if (!hasCredit) return false;
    return true;
  }


  /**
   * Writes the tree of a UHSRootnode in 88a format.
   * If it cannot be expressed in 88a, nothing will be written.
   * Newlines and "^break^" are replaced by " ".
   *
   * @param rootNode an existing root node
   * @see #isValid88Format(UHSRootNode) isValid88Format(UHSRootNode)
   */
  public void write88Format(UHSRootNode rootNode, OutputStream os) throws IOException, CharacterCodingException, UnsupportedEncodingException {
    if (!isValid88Format(rootNode)) {
      UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();
      if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, this, "This node tree cannot be saved as 88a", 0, null);
      return;
    }
    StringBuffer buf = new StringBuffer();

    String tmp = null;
    List<UHSNode> subjectNodes = rootNode.getChildren("Subject");
    List<UHSNode> questionNodes = new ArrayList<UHSNode>();
    List<UHSNode> hintNodes = new ArrayList<UHSNode>();
    for (int s=0; s < subjectNodes.size(); s++) {
      UHSNode tmpS = subjectNodes.get(s);
      List<UHSNode> tmpQs = tmpS.getChildren();
      questionNodes.addAll(tmpQs);
      for (int q=0; q < tmpQs.size(); q++) {
        hintNodes.addAll( (tmpQs.get(q)).getChildren() );
      }
    }

    int sSize = 2;
    int qSize = 2;
    int hSize = 1;
    int firstQ = sSize*subjectNodes.size() + 1;
    int firstH = sSize*subjectNodes.size() + qSize*questionNodes.size() + 1;
    int lastH = firstH + hSize*(hintNodes.size()-1);

    buf.append("UHS\r\n");

    String title = "";
    title = escapeText(rootNode, true);
    buf.append(title).append("\r\n");
    buf.append(firstH).append("\r\n");
    buf.append(lastH).append("\r\n");


    for (int s=0; s < subjectNodes.size(); s++) {
      UHSNode tmpSubject = subjectNodes.get(s);
      tmp = escapeText(tmpSubject, true);
        tmp = tmp.replaceAll("\\^break\\^", " ");  // 88a doesn't support newlines
        tmp = encryptString(tmp);
      int n = qSize*questionNodes.indexOf(tmpSubject.getChild(0)) + firstQ;
      buf.append(tmp).append("\r\n").append(n).append("\r\n");
    }

    for (int q=0; q < questionNodes.size(); q++) {
      UHSNode tmpQuestion = questionNodes.get(q);
      tmp = escapeText(tmpQuestion, true);
        if (tmp.endsWith("?")) tmp = tmp.substring(0, tmp.length()-1);
        tmp = tmp.replaceAll("\\^break\\^", " ");  // 88a doesn't support newlines
        tmp = encryptString(tmp);
      int n = hSize*hintNodes.indexOf(tmpQuestion.getChild(0)) + firstH;
      buf.append(tmp).append("\r\n").append(n).append("\r\n");
    }

    for (int h=0; h < hintNodes.size(); h++) {
      UHSNode tmpHint = hintNodes.get(h);
      tmp = escapeText(tmpHint, true);
        tmp = tmp.replaceAll("\\^break\\^", " ");  // 88a doesn't support newlines
        tmp = encryptString(tmp);
      buf.append(tmp).append("\r\n");
    }

    UHSNode creditNode = rootNode.getChildren("Credit").get(0);
    tmp = (String)creditNode.getChild(0).getContent();
    tmp = tmp.replaceAll("\\^break\\^", "\r\n");
    if (tmp.length() > 0) buf.append(tmp).append("\r\n");

    os.write(encodeAsciiBytes(buf.toString()));
  }


  /**
   * Writes the tree of a UHSRootnode in 9x format.
   *
   * @param rootNode an existing root node
   */
  public void write9xFormat(UHSRootNode rootNode, OutputStream os) throws IOException, CharacterCodingException, UnsupportedEncodingException {
    UHS9xInfo info = new UHS9xInfo();
    String uhsTitle = rootNode.getUHSTitle();
    if (uhsTitle != null) info.encryptionKey = generateKey(uhsTitle);

    StringBuffer buf = new StringBuffer();
    getLinesAndBinData(rootNode, info, buf);
    info.linesCollected = true;
    info.binCollected = true;
    writeAsciiLines(info, buf.toString());

    info.nodeStream.writeTo(os);
  }

  private void getLinesAndBinData(UHSNode currentNode, UHS9xInfo info, StringBuffer parentBuf) throws CharacterCodingException, UnsupportedEncodingException {
    String type = currentNode.getType();
    boolean hasTitle = false;

    if (!info.linesCollected) {
      if (currentNode.getId() != -1) {  // Associate id with current line
        info.putLine(currentNode.getId(), info.line);
      }
    }

    if (type.equals("Root")) {
      StringBuffer buf = new StringBuffer();
      buf.append("UHS").append("\r\n");
      buf.append("Important Message!").append("\r\n");

      // First and last line numbers, counting from the line after these (1-based).
      buf.append("7").append("\r\n");
      buf.append("18").append("\r\n");

      // Subject.
      buf.append(encryptString("Important!")).append("\r\n");
      buf.append("3").append("\r\n");

      // Questions.
      buf.append(encryptString("Ignore this!?")).append("\r\n");
      buf.append("7").append("\r\n");
      buf.append(encryptString("Why aren't there any more hints here?")).append("\r\n");
      buf.append("16").append("\r\n");

      // Hints for "Ignore this!?"
      buf.append(encryptString("The following text will appear encrypted -- it is intended as instructions")).append("\r\n");
      buf.append(encryptString("for people without UHS readers.")).append("\r\n");
      buf.append("-------------------------------------------------------------------------").append("\r\n");
      buf.append("This file is encoded in the Universal Hint System format.  The UHS is").append("\r\n");
      buf.append("designed to show you only the hints that you need so your game is not").append("\r\n");
      buf.append("spoiled.  You will need a UHS reader for your computer to view this").append("\r\n");
      buf.append("file.  You can find UHS readers and more information on the UHS on the").append("\r\n");
      buf.append("Internet at http://www.uhs-hints.com/ .").append("\r\n");
      buf.append("-------------------------------------------------------------------------").append("\r\n");

      // Hints for "Why aren't there any more hints here?"
      buf.append(encryptString("This file has been written for a newer UHS format which the reader that")).append("\r\n");
      buf.append(encryptString("you are using does not support.  Visit the UHS web site at")).append("\r\n");
      buf.append(encryptString("http://www.uhs-hints.com/ to see if a newer reader is available.")).append("\r\n");

      buf.append("** END OF 88A FORMAT **").append("\r\n");

      if (currentNode.getChildCount() > 0) {
        List<UHSNode> children = currentNode.getChildren();
        for (int i=0; i < children.size(); i++) {
          getLinesAndBinData(children.get(i), info, buf);
        }
      }

      parentBuf.append(buf);
    }
    else if (type.equals("Link")) {  // lines: 3
      StringBuffer buf = new StringBuffer();
      buf.append(/* lineCount */ " link").append("\r\n");
      buf.append(getEncryptedText(currentNode, info, ENCRYPT_NONE)).append("\r\n");

      int targetLine = info.getLine(currentNode.getLinkTarget());
      buf.append(targetLine).append("\r\n");

      buf.insert(0, crlfCount(buf.toString()));  // Insert lineCount at the beginning
      parentBuf.append(buf);
    }
    else if (type.equals("Text")) {  // lines: 3
      StringBuffer buf = new StringBuffer();
      buf.append(/* lineCount */ " text").append("\r\n");
      buf.append(getEncryptedText(currentNode, info, ENCRYPT_NONE)).append("\r\n");

      UHSNode tmpNode = currentNode.getFirstChild("TextData");
      if (tmpNode == null) {/* Throw an error */}
      byte[] tmpBytes = encodeAsciiBytes(getEncryptedText(tmpNode, info, ENCRYPT_TEXT));

      String offsetString = zeroPad(info.binOffset+info.binStream.size(), info.offsetNumberWidth);
      String lengthString = zeroPad(tmpBytes.length, info.lengthNumberWidth);
      buf.append("000000 0 ").append(offsetString).append(" ").append(lengthString).append("\r\n");

      info.binStream.write(tmpBytes, 0, tmpBytes.length);

      info.checkBinOffset(offsetString);
      info.checkBinLength(lengthString);

      buf.insert(0, crlfCount(buf.toString()));  // Insert lineCount at the beginning
      parentBuf.append(buf);
    }
    else if (type.equals("Sound")) {  // lines: 3
      StringBuffer buf = new StringBuffer();
      buf.append(/* lineCount */ " sound").append("\r\n");
      buf.append(getEncryptedText(currentNode, info, ENCRYPT_NONE)).append("\r\n");

      UHSNode tmpNode = currentNode.getFirstChild("SoundData");
      if (tmpNode == null) {/* Throw an error */}
      byte[] tmpBytes = (byte[])currentNode.getContent();

      String offsetString = zeroPad(info.binOffset+info.binStream.size(), info.offsetNumberWidth);
      String lengthString = zeroPad(tmpBytes.length, info.lengthNumberWidth);
      buf.append("000000 ").append(offsetString).append(" ").append(lengthString).append("\r\n");

      info.binStream.write(tmpBytes, 0, tmpBytes.length);

      info.checkBinOffset(offsetString);
      info.checkBinLength(lengthString);

      buf.insert(0, crlfCount(buf.toString()));  // Insert lineCount at the beginning
      parentBuf.append(buf);
    }
    else if (type.equals("Blank")) {  // lines: 2
      if ("--=File Info=--".equals(currentNode.getContent())) return;  // Meta: nested info node.

      StringBuffer buf = new StringBuffer();
      buf.append(" blank").append("\r\n");
      buf.append("-").append("\r\n");

      buf.insert(0, crlfCount(buf.toString()));  // Insert lineCount at the beginning
      parentBuf.append(buf);
    }
    else if (type.equals("Subject")) {  // lines: 2 + N*content lines
      StringBuffer buf = new StringBuffer();
      buf.append(/* lineCount */ " subject").append("\r\n");
      buf.append(getEncryptedText(currentNode, info, ENCRYPT_NONE)).append("\r\n");

      if (currentNode.getChildCount() > 0) {
        List<UHSNode> children = currentNode.getChildren();
        for (int i=0; i < children.size(); i++) {
          getLinesAndBinData(children.get(i), info, buf);
        }
      }

      buf.insert(0, crlfCount(buf.toString()));  // Insert lineCount at the beginning
      parentBuf.append(buf);
    }
    else if (type.equals("Comment")) {  // lines: 2 + content lines
      StringBuffer buf = new StringBuffer();
      buf.append(/* lineCount */ " comment").append("\r\n");
      buf.append(getEncryptedText(currentNode, info, ENCRYPT_NONE)).append("\r\n");

      UHSNode tmpNode = currentNode.getFirstChild("CommentData");
      if (tmpNode == null) {/* Throw an error */}

      buf.append(getEncryptedText(tmpNode, info, ENCRYPT_NONE)).append("\r\n");

      buf.insert(0, crlfCount(buf.toString()));  // Insert lineCount at the beginning
      parentBuf.append(buf);
    }
    else if (type.equals("Credit")) {  // lines: 2 + content lines
      StringBuffer buf = new StringBuffer();
      buf.append(/* lineCount */ " credit").append("\r\n");
      buf.append(getEncryptedText(currentNode, info, ENCRYPT_NONE)).append("\r\n");

      UHSNode tmpNode = currentNode.getFirstChild("CreditData");
      if (tmpNode == null) {/* Throw an error */}

      buf.append(getEncryptedText(tmpNode, info, ENCRYPT_NONE)).append("\r\n");

      buf.insert(0, crlfCount(buf.toString()));  // Insert lineCount at the beginning
      parentBuf.append(buf);
    }
    else if (type.equals("Version")) {  // lines: 2 + content lines
      StringBuffer buf = new StringBuffer();
      buf.append(/* lineCount */ " version").append("\r\n");
      buf.append(getEncryptedText(currentNode, info, ENCRYPT_NONE)).append("\r\n");

      UHSNode tmpNode = currentNode.getFirstChild("VersionData");
      if (tmpNode == null) {/* Throw an error */}

      buf.append(getEncryptedText(tmpNode, info, ENCRYPT_NONE)).append("\r\n");

      buf.insert(0, crlfCount(buf.toString()));  // Insert lineCount at the beginning
      parentBuf.append(buf);
    }
    else if (type.equals("Info")) {  // lines: 2 + content lines
      StringBuffer buf = new StringBuffer();
      buf.append(/* lineCount */ " info").append("\r\n");
      buf.append("-").append("\r\n");

      UHSNode tmpNode = currentNode.getFirstChild("InfoData");
      if (tmpNode == null) {/* Throw an error */}

      buf.append(getEncryptedText(tmpNode, info, ENCRYPT_NONE)).append("\r\n");

      buf.insert(0, crlfCount(buf.toString()));  // Insert lineCount at the beginning
      parentBuf.append(buf);
    }
    else if (type.equals("Incentive")) {  // lines: 2 + content lines
      StringBuffer buf = new StringBuffer();
      buf.append(/* lineCount */ " incentive").append("\r\n");
      buf.append("-").append("\r\n");

      UHSNode tmpNode = currentNode.getFirstChild("IncentiveData");
      if (tmpNode == null) {/* Throw an error */}

      buf.append(getEncryptedText(tmpNode, info, ENCRYPT_NEST)).append("\r\n");

      buf.insert(0, crlfCount(buf.toString()));  // Insert lineCount at the beginning
      parentBuf.append(buf);
    }
    else if (type.equals("Hint")) {  // TODO  // lines: 2 + N children's content lines + N-1
      StringBuffer buf = new StringBuffer();
      buf.append(/* lineCount */ " hint").append("\r\n");
      buf.append(getEncryptedText(currentNode, info, ENCRYPT_NONE)).append("\r\n");

      if (currentNode.getChildCount() > 0) {
        List<UHSNode> children = currentNode.getChildren("HintData");
        for (int i=0; i < children.size(); i++) {
          if (i > 0) {
            if (!info.linesCollected) info.line += 1;  // "-" divider
            buf.append("-").append("\r\n");
          }
          String encryptedChildContent = getEncryptedText(children.get(i), info, ENCRYPT_HINT);
          if (!info.linesCollected) info.line += crlfCount(encryptedChildContent) + 1;  // +1 is final line w/o crlf.
          buf.append(encryptedChildContent).append("\r\n");
        }
      }

      buf.insert(0, crlfCount(buf.toString()));  // Insert lineCount at the beginning
      parentBuf.append(buf);
    }
/*
    else if (type.equals("NestHint")) {
      if (!info.linesCollected) info.line += 2;
      hasTitle = true;
      if (currentNode.getChildCount() > 0) {
        ArrayList children = currentNode.getChildren();
        for (int i=0; i < children.size(); i++) {
          UHSNode tmpNode = (UHSNode)children.get(i);
          if (tmpNode.getType().equals("HintData")) {
            if (i > 0) {
              if (((UHSNode)children.get(i-1)).getType().equals("HintData")) {
                if (!info.linesCollected) info.line += 1;  // "-" divider
              }
            }
            getContentLines(tmpNode, info);
          } else {
            if (i > 0) {
              if (!info.linesCollected) info.line += 1;  // "=" divider
            }
            getLinesAndBinData(tmpNode, info);
          }
        }
      }
    }
    else if (type.equals("HotSpot")) {
      StringBuffer buf = new StringBuffer();
      if (!info.linesCollected) info.line += 3;
      hasTitle = true;
      if (currentNode.getChildCount() > 0) {
        // Add (each child's size + 1), ignore first child (Hyperpng/Hypergif)
        ArrayList children = currentNode.getChildren();

        getBinData((UHSNode)children.get(0), info, ENCRYPT_NONE);  // Image

        for (int i=1; i < children.size(); i++) {
          UHSNode tmpNode = (UHSNode)children.get(i);

          if (!info.linesCollected) info.line += 1;  // Zone info
          int[] coords = currentNode.getCoords(tmpNode);
          buf.append((coords[0]+1) +" "+ (coords[1]+1) +" "+ (coords[0]+1+coords[3]+1) +" "+ (coords[1]+1+coords[4]+1)).append("\r\n");

          // Let the child think it's on the zone info line for id purposes (inc after).
          getLinesAndBinData((UHSNode)children.get(i), info);
          if (!info.linesCollected) info.line += 1;
        }
      }
    }
    else if (type.equals("Overlay")) {
      if (!info.linesCollected) info.line += 3;  // Get this node's binary content
      hasTitle = false;  // Not present, will use a blank placeholder
      getBinData(currentNode, info, ENCRYPT_NONE);
    }
*/
  }

  /**
   * Adds zeroes to a number intil it has a minimum number of characters.
   */
  private String zeroPad(long n, int width) {
    String s = Long.toString(n);
    if (s.length() >= width) return s;
    char[] padding = new char[width - s.length()];
    Arrays.fill(padding, '0');
    return (padding + s);
  }

  private int crlfCount(String s) {
    Matcher m = crlfPtn.matcher(s);
    int lineCount = 0;
    while (m.find()) lineCount++;
    return lineCount;
  }

  /**
   * Encodes a string and writes the bytes to info.nodeStream.
   * If info.linesCollected is false, infi.line will increment
   * by the number of line breaks in the string.
   */
  private void writeAsciiLines(UHS9xInfo info, String s) throws CharacterCodingException, UnsupportedEncodingException {
    byte[] tmpBytes = encodeAsciiBytes(s);
    info.nodeStream.write(tmpBytes, 0, tmpBytes.length);
    if (!info.linesCollected) {
      info.line += crlfCount(s);
    }
  }

  /**
   * Increments a UHS9xInfo's line count according to string content.
   */
  private void getContentLines(UHSNode currentNode, UHS9xInfo info) throws CharacterCodingException, UnsupportedEncodingException {
    if (currentNode.getContentType() != UHSNode.STRING) return;
    String tmpString = (String)currentNode.getContent();
    info.line += tmpString.split("\\^break\\^").length;
  }

  /**
   * Encrypts the multiline string content of a node.
   * Linebreaks will be converted from "^break^" to "\r\n".
   */
  private String getEncryptedText(UHSNode currentNode, UHS9xInfo info, int encryption) throws CharacterCodingException, UnsupportedEncodingException {
    if (currentNode.getContentType() != UHSNode.STRING) return null;  // !?

    String tmpString = (String)currentNode.getContent();
    tmpString = tmpString.replaceAll("\\^break\\^", "\r\n");
    if (encryption == ENCRYPT_HINT) {
      tmpString = encryptString(tmpString);
    }
    else if (encryption == ENCRYPT_NEST || encryption == ENCRYPT_TEXT) {
      if (info.encryptionKey == null) {
        UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();
        if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, this, "Attempted to encrypt before a key was set", 0, null);
        return null;  // Throw a custom error
      }
      StringBuffer buf = new StringBuffer(tmpString.length());
      String[] lines = tmpString.split("\r\n");
      for (int i=0; i < lines.length; i++) {
        if (i > 0) buf.append("\r\n");
        if (encryption == ENCRYPT_NEST) buf.append(encryptNestString(lines[i], info.encryptionKey));
        else if (encryption == ENCRYPT_TEXT) buf.append(encryptTextHunk(lines[i], info.encryptionKey));
      }
      tmpString = buf.toString();
    }
    return tmpString;
  }

  /**
   * Writes a node's content to a UHS9xInfo bin stream.
   * String content is encoded into bytes.
   */
  private void getBinData(UHSNode currentNode, UHS9xInfo info, int encryption) throws CharacterCodingException, UnsupportedEncodingException {
    byte[] tmpBytes = null;
    if (currentNode.getContentType() == UHSNode.STRING) {
      String tmpString = getEncryptedText(currentNode, info, encryption);
      tmpBytes = encodeAsciiBytes(tmpString);
    } else {
      tmpBytes = (byte[])currentNode.getContent();
    }
    info.binStream.write(tmpBytes, 0, tmpBytes.length);
  }


  /**
   * Returns text from a UHSNode, escaped.
   * <br />Unexpected non-ascii characters are replaced with <b>^?^</b>.
   * <br />
   * <br />Escapes have existed from version 88a onwards in most nodes' content and titles.
   * <br />The # character is the main escape char and is written <b>##</b>.
   *
   * <ul><li><b>#</b> a '#' character.</li>
   * <li><b>#a+</b>[AaEeIiOoUu][:'`^]<b>#a-</b> accent enclosed letter; :=diaeresis,'=acute,`=grave,^=circumflex.</li>
   * <li><b>#a+</b>[Nn]~<b>#a-</b> accent enclosed letter with a tilde.</li>
   * <li><b>#a+</b>ae<b>#a-</b> an ash character.</li>
   * <li><b>#a+</b>TM<b>#a-</b> a trademark character.</li>
   * <li><b>#w.</b> raw newlines are spaces.</li>
   * <li><b>#w+</b> raw newlines are spaces (default).</li>
   * <li><b>#w-</b> raw newlines are newlines.</li></ul>
   *
   * The following are left for display code to handle (e.g., UHSTextArea).
   * <ul><li><b>#p+</b> proportional font (default).</li>
   * <li><b>#p-</b> non-proportional font.</li></ul>
   *
   * This is displayed, but not a clickable hyperlink.
   * <ul><li><b>#h+</b> through <b>#h-</b> is a hyperlink (http or email).</li></ul>
   * <br />Illustrative UHS: <i>Portal: Achievements</i> (hyperlink)
   *
   * @param currentNode the node to get content from
   * @param plain false to add markup, true to replace with ascii equivalent characters
   * @return an escaped string, or null if the content wasn't text
   */
  public String escapeText(UHSNode currentNode, boolean plain) {
    if (currentNode.getContentType() != UHSNode.STRING) return null;

    CharsetEncoder asciiEncoder = Charset.forName("US-ASCII").newEncoder();

    String accentPrefix = "#a+";
    String accentSuffix = "#a-";

    char[] diaeresisMarkup = new char[] {':'};
    char[] diaeresisAccent = new char[] {'Ä','Ë','Ï','Ö','Ü','ä','ë','ï','ö','ü'};
    char[] diaeresisNormal = new char[] {'A','E','I','O','U','a','e','i','o','u'};
    char[] acuteMarkup = new char[] {'\''};
    char[] acuteAccent = new char[] {'Á','É','Í','Ó','Ú','á','é','í','ó','ú'};
    char[] acuteNormal = new char[] {'A','E','I','O','U','a','e','i','o','u'};
    char[] graveMarkup = new char[] {'`'};
    char[] graveAccent = new char[] {'À','È','Ì','Ò','Ù','à','è','ì','ò','ù'};
    char[] graveNormal = new char[] {'A','E','I','O','U','a','e','i','o','u'};
    char[] circumflexMarkup = new char[] {'^'};
    char[] circumflexAccent = new char[] {'Â','Ê','Î','Ô','Û','â','ê','î','ô','û'};
    char[] circumflexNormal = new char[] {'A','E','I','O','U','a','e','i','o','u'};
    char[] tildeMarkup = new char[] {'~'};
    char[] tildeAccent = new char[] {'Ñ','ñ'};
    char[] tildeNormal = new char[] {'N','n'};

    char[][][] accents = new char[][][] {
      {diaeresisMarkup, diaeresisAccent, diaeresisNormal},
      {acuteMarkup, acuteAccent, acuteNormal},
      {graveMarkup, graveAccent, graveNormal},
      {circumflexMarkup, circumflexAccent, circumflexNormal},
      {graveMarkup, graveAccent, graveNormal}
    };

    StringBuffer buf = new StringBuffer();
    char[] tmp = ((String)currentNode.getContent()).toCharArray();
    for (int c=0; c < tmp.length; c++) {
      boolean escaped = false;

      for (int i=0; !escaped && i < accents.length; i++) {
        char markup = accents[i][0][0]; char[] accent = accents[i][1]; char[] normal = accents[i][2];

        for (int a=0; !escaped && a < accent.length; a++) {
          if (tmp[c] == accent[a]) {
            if (plain) {
              buf.append(normal[a]);
            } else {
              buf.append(accentPrefix).append(normal[a]).append(markup).append(accentSuffix);
            }
            escaped = true;
          }
        }
      }

      if (!escaped && tmp[c] == 'æ') {
        String normal = "ae";
        if (plain) {
          buf.append(normal);
        } else {
          buf.append(accentPrefix).append(normal).append(accentSuffix);
        }
        escaped = true;
      }
      if (!escaped && tmp[c] == '™') {
        String normal = "TM";
        if (plain) {
          buf.append(normal);
        } else {
          buf.append(accentPrefix).append(normal).append(accentSuffix);
        }
        escaped = true;
      }
      if (!escaped) {
        if (asciiEncoder.canEncode(tmp[c])) {
          buf.append(tmp[c]);
        } else {
          UHSErrorHandler errorHandler = UHSErrorHandlerManager.getErrorHandler();
          if (errorHandler != null) errorHandler.log(UHSErrorHandler.ERROR, this, "No escape is known for this non-ascii character: "+ tmp[c], 0, null);
          buf.append("^?^");
        }
      }
      // TODO: Linebreaks
    }

    return buf.toString();
  }


  private byte[] encodeAsciiBytes(String s) throws CharacterCodingException, UnsupportedEncodingException {
    CharsetEncoder asciiEncoder = Charset.forName("ISO-8859-1").newEncoder();
      asciiEncoder.onMalformedInput(CodingErrorAction.REPLACE);
      asciiEncoder.onUnmappableCharacter(CodingErrorAction.REPORT);
      asciiEncoder.replaceWith("!".getBytes("ISO-8859-1"));
    CharBuffer sBuf = CharBuffer.wrap(s);
    ByteBuffer bBuf = asciiEncoder.encode(sBuf);
    return bBuf.array();
  }


  // An object to pass args around while recursively writing a 9x file.
  private class UHS9xInfo {
    // Pass 1
    public int line = -1;
    public ByteArrayOutputStream binStream = new ByteArrayOutputStream();
    public Map<Integer, Integer> idToLineMap = new HashMap<Integer, Integer>();

    public int[] encryptionKey = null;
    public boolean linesCollected = false;
    public boolean binCollected = false;

    // Pass 2
    public int offsetNumberWidth = 6;  // Might grow to 7 if nodeStream's too large
    public int lengthNumberWidth = 6;
    public boolean numberWidthChanged = false;
    public ByteArrayOutputStream nodeStream = new ByteArrayOutputStream();
    public long binOffset = 0;  // From start of file to 0x1A

    // Pass 3
    // reset nodeStream and encode for real

    public int getLine(int id) {
      Integer line = idToLineMap.get(new Integer(id));
      if (line != null) return line.intValue();
      else return -1;
    }

    public void putLine(int id, int line) {
      idToLineMap.put(new Integer(id), new Integer(line));
    }


    public void checkBinOffset(String offsetString) {
      if (offsetString.length() > offsetNumberWidth) {
        offsetNumberWidth = offsetString.length();
        numberWidthChanged = true;
      }
    }

    public void checkBinLength(String lengthString) {
      if (lengthString.length() > lengthNumberWidth) {
        lengthNumberWidth = lengthString.length();
        numberWidthChanged = true;
      }
    }
  }

}
