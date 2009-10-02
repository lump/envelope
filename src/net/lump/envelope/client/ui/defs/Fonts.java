package net.lump.envelope.client.ui.defs;

import java.awt.*;

/**
 * .
 *
 * @author troy
 * @version $Id: Fonts.java,v 1.5 2009/10/02 22:06:23 troy Exp $
 */
public enum Fonts {

  serif_36_bold_italic(new Font("serif", Font.ITALIC | Font.BOLD, 36)),
  lucida_grande(new Font("Lucida Grande", Font.BOLD, 14)),
  sans_serif(new Font("SansSerif", Font.BOLD, 14)),
  sans_14_bold(new Font("Lucida Grande", Font.BOLD, 14), new Font("SansSerif", Font.PLAIN, 14)),
  sans_10_bold(new Font("Lucida Grande", Font.BOLD, 10), new Font("SansSerif", Font.PLAIN, 10)),
  fixed(
      new Font("Bitstream Vera Sans Mono", Font.PLAIN, 12),
      new Font("Andale Mono", Font.PLAIN, 12),
      new Font("Monaco", Font.PLAIN, 12),
      new Font("Courier New", Font.PLAIN, 12),
      new Font("Monospaced", Font.PLAIN, 12)
  );

  Font font;

  Fonts(Font... fonts) {
    font = null;

    // get the first font that is defined in the list
    for (Font f : fonts) {
      if (f != null) {
        font = f;
        break;
      }
    }
  }

  public Font getFont() {
    return this.font;
  }
}