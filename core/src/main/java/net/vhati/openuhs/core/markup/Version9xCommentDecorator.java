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
 * A StringDecorator for "CommentData" nodes.
 *
 * <br />Line breaks are initially replaced with " " by default.
 * <br />Standard symbols and decorations are handled.
 */
public class Version9xCommentDecorator extends Version9xStringDecorator {


  @Override
  public DecoratedFragment[] getDecoratedString(String rawContent) {
    List<DecoratedFragment> resultList = new ArrayList<DecoratedFragment>();
    char[] tmp = rawContent.toCharArray();
    StringBuffer buf = new StringBuffer(tmp.length);
    int consumedOffset = -1;
    String[] breakStr = new String[] {" "};  // Initial value varies by Decorator
    Decoration[] decos = getDecorations();
    int[] decoStates = new int[decos.length];

    for (int c=0; c < tmp.length; c++) {
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
      String[] decoNames = attribList.toArray(new String[attribList.size()]);
      Map[] argMaps = new LinkedHashMap[attribList.size()];
      resultList.add(new DecoratedFragment(fragment, decoNames, argMaps));
    }

    return resultList.toArray(new DecoratedFragment[resultList.size()]);
  }
}
