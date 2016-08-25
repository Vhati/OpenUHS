package net.vhati.openuhs.core.markup;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;


/**
 * A StringDecorator for "Link" and various group nodes.
 * Their content is a single-line heading.
 *
 * <br />Line breaks are replaced with " ".
 * <br />Standard symbols and decorations are handled.
 */
public class Version9xTitleDecorator extends Version9xStringDecorator {


  public DecoratedFragment[] getDecoratedString(String rawContent) {
    ArrayList resultList = new ArrayList();
    char[] linebreak = StringDecorator.linebreak;
    char[] tmp = rawContent.toCharArray();
    StringBuffer buf = new StringBuffer(tmp.length);
    int consumedOffset = -1;
    String[] breakStr = new String[] {" "};  // Initial value varies by Decorator
    Decoration[] decos = getDecorations();
    int[] decoStates = new int[decos.length];

    for (int c=0; c < tmp.length; c++) {
      consumedOffset = parseSymbolMarkup(tmp, buf, c);
      if (consumedOffset != -1) {c += consumedOffset; continue;}

      if (c+linebreak.length < tmp.length) {
        char[] chunkA = new char[linebreak.length];
        System.arraycopy(tmp, c, chunkA, 0, linebreak.length);
        if (Arrays.equals(chunkA, linebreak)) {
          buf.append(" ");
          c += linebreak.length-1; continue;
        }
      }

      consumedOffset = parseLineBreakMarkup(tmp, buf, c, breakStr);
      if (consumedOffset != -1) {c += consumedOffset; continue;}

      consumedOffset = parseDecorationMarkup(tmp, buf, c, decos, decoStates, resultList);
      if (consumedOffset != -1) {c += consumedOffset; continue;}

      buf.append(tmp[c]);
    }

    // Handle lingering content
    if (buf.length() > 0) {
      String fragment = buf.toString();
      ArrayList attribList = new ArrayList(1);
      for (int d=0; d < decos.length; d++) {
        if (decoStates[d] > 0) attribList.add(decos[d].name);
      }
      String[] decoNames = (String[])attribList.toArray(new String[attribList.size()]);
      LinkedHashMap[] argMaps = new LinkedHashMap[attribList.size()];
      resultList.add(new DecoratedFragment(fragment, decoNames, argMaps));
    }

    return (DecoratedFragment[])resultList.toArray(new DecoratedFragment[resultList.size()]);
  }
}
