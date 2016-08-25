package net.vhati.openuhs.core.markup;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.vhati.openuhs.core.markup.DecoratedFragment;
import net.vhati.openuhs.core.markup.Decoration;
import net.vhati.openuhs.core.markup.StringDecorator;
import net.vhati.openuhs.core.markup.Version9xStringDecorator;


/**
 * A StringDecorator for "TextData" nodes.
 *
 * <br />Line breaks are initially replaced with "\n" by default.
 * <br />Lines with only a space ("^break^ ^break^") become "\n \n"
 * regardless of linebreak markup context.
 * <br />Standard symbols and decorations are handled.
 */
public class Version9xTextDecorator extends Version9xStringDecorator {
  private static final char[] emptyline = getEmptyLineChars();
  private static final char[] emptysplice = new char[] {'\n',' ','\n'};


  @Override
  public DecoratedFragment[] getDecoratedString(String rawContent) {
    List<DecoratedFragment> resultList = new ArrayList<DecoratedFragment>();
    char[] tmp = rawContent.toCharArray();
    StringBuffer buf = new StringBuffer(tmp.length);
    int consumedOffset = -1;
    String[] breakStr = new String[] {"\n"};  // Initial value varies by Decorator
    Decoration[] decos = getDecorations();
    int[] decoStates = new int[decos.length];

    for (int c=0; c < tmp.length; c++) {
      // Handle empty lines
      if (c+emptyline.length < tmp.length) {
        char[] chunkA = new char[emptyline.length];
        System.arraycopy(tmp, c, chunkA, 0, emptyline.length);
        if (Arrays.equals(chunkA, emptyline)) {
          buf.append(emptysplice);
          c += emptyline.length-1; continue;
        }
      }

      consumedOffset = parseSymbolMarkup(tmp, buf, c);
      if (consumedOffset != -1) {c += consumedOffset; continue;}

      consumedOffset = parseLineBreakMarkup(tmp, buf, c, breakStr);
      if (consumedOffset != -1) {c += consumedOffset; continue;}

      consumedOffset = parseDecorationMarkup(tmp, buf, c, decos, decoStates, resultList);
      if (consumedOffset != -1) {c += consumedOffset; continue;}

      buf.append(tmp[c]);
    }

    // Handle lingering content
    if (buf.length() > 0) {
      String fragment = buf.toString();
      List<String> attribList = new ArrayList<String>(1);
      for (int d=0; d < decos.length; d++) {
        if (decoStates[d] > 0) attribList.add(decos[d].name);
      }
      String[] decoNames = (String[])attribList.toArray(new String[attribList.size()]);
      Map[] argMaps = new LinkedHashMap[attribList.size()];
      resultList.add(new DecoratedFragment(fragment, decoNames, argMaps));
    }

    return (DecoratedFragment[])resultList.toArray(new DecoratedFragment[resultList.size()]);
  }


  /**
   * Builds the empty line char sequence.
   * That is: "^break^ ^break^".
   *
   * @see StringDecorator#linebreak StringDecorator.linebreak
   */
  private static char[] getEmptyLineChars() {
    char[] linebreak = StringDecorator.linebreak;
    char[] middle = new char[] {' '};
    char[] result = new char[linebreak.length+middle.length+linebreak.length];
    System.arraycopy(linebreak, 0, result, 0, linebreak.length);
    System.arraycopy(middle, 0, result, linebreak.length, middle.length);
    System.arraycopy(linebreak, 0, result, linebreak.length+middle.length, linebreak.length);
    return result;
  }
}
