package us.lump.lib.util.encdec.exception;

import java.io.IOException;

/**
 * From sun.misc.
 *
 * @author Troy Bowman
 * @version $Id: CharacterEncoderFormatException.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */

public class CharacterEncoderFormatException extends IOException {
  public CharacterEncoderFormatException(String s) {
    super(s);
  }
}
