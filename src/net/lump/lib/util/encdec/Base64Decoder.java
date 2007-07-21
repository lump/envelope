package us.lump.lib.util.encdec;

/**
 * From sun.misc.
 *
 * @author Troy Bowman
 * @version $Id: Base64Decoder.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */

import us.lump.lib.util.encdec.exception.CharacterEncoderFormatException;
import us.lump.lib.util.encdec.exception.CharacterEncoderStreamExhausted;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;

/**
 * This class implements a BASE64 Character decoder as specified in RFC1521.
 * <p/>
 * This RFC is part of the MIME specification which is published by the
 * Internet Engineering Task Force (IETF). Unlike some other encoding
 * schemes there is nothing in this encoding that tells the decoder
 * where a buffer starts or stops, so to use it you will need to isolate
 * your encoded data into a single chunk and then feed them this decoder.
 * The simplest way to do that is to read all of the encoded data into a
 * string and then use:
 * <pre>
 *      byte    mydata[];
 *      BASE64Decoder base64 = new BASE64Decoder();
 * <p/>
 *      mydata = base64.decodeBuffer(bufferString);
 * </pre>
 * This will decode the String in <i>bufferString</i> and give you an array
 * of bytes in the array <i>myData</i>.
 * <p/>
 * On errors, this class throws a CharacterEncoderFormatException with the following detail
 * strings:
 * <pre>
 *    "BASE64Decoder: Not enough bytes for an atom."
 * </pre>
 *
 * @author Chuck McManis
 * @version $Id: Base64Decoder.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 * @see us.lump.lib.util.encdec.CharacterEncoder
 * @see Base64Decoder
 */

public class Base64Decoder extends CharacterDecoder {
  byte decode_buffer[] = new byte[4];

  /**
   * This character array provides the character to value map
   * based on RFC1521.
   * <p/>
   * + 43
   * / 47
   * [0-9] 48-57
   * = 61
   * [A-Z] 65-90
   * [a-z] 97-122
   */
  private final static char pem_array[] = {
      //0   1    2    3    4    5    6    7
      'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', // 0
      'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', // 1
      'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', // 2
      'Y', 'Z', 'a', 'b', 'c', 'd', 'e', 'f', // 3
      'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n', // 4
      'o', 'p', 'q', 'r', 's', 't', 'u', 'v', // 5
      'w', 'x', 'y', 'z', '0', '1', '2', '3', // 6
      '4', '5', '6', '7', '8', '9', '+', '/'  // 7
  };

  private final static byte pem_convert_array[] = new byte[256];

  static {
    for (int i = 0; i < 255; i++) {
      pem_convert_array[i] = -1;
    }
    for (int i = 0; i < pem_array.length; i++) {
      pem_convert_array[pem_array[i]] = (byte) i;
    }
  }

  /**
   * This class has 4 bytes per atom
   */
  protected int bytesPerAtom() {
    return (4);
  }

  /**
   * Any multiple of 4 will do, 72 might be common
   */
  protected int bytesPerLine() {
    return (72);
  }

  /**
   * Decode one BASE64 atom into 1, 2, or 3 bytes of data.
   */
  protected void decodeAtom(PushbackInputStream inStream, OutputStream outStream, int rem)
      throws java.io.IOException {
    int i;
    byte a = -1, b = -1, c = -1, d = -1;

    if (rem < 2) {
      throw new CharacterEncoderFormatException("BASE64Decoder: Not enough bytes for an atom.");
    }
    do {
      i = inStream.read();
      if (i == -1) {
        throw new CharacterEncoderStreamExhausted();
      }
    } while (i == '\n' || i == '\r');
    decode_buffer[0] = (byte) i;

    i = readFully(inStream, decode_buffer, 1, rem - 1);
    if (i == -1) {
      throw new CharacterEncoderStreamExhausted();
    }

    if (rem > 3 && decode_buffer[3] == '=') {
      rem = 3;
    }
    if (rem > 2 && decode_buffer[2] == '=') {
      rem = 2;
    }
    switch (rem) {
      case 4:
        d = pem_convert_array[decode_buffer[3] & 0xff];
        // NOBREAK
      case 3:
        c = pem_convert_array[decode_buffer[2] & 0xff];
        // NOBREAK
      case 2:
        b = pem_convert_array[decode_buffer[1] & 0xff];
        a = pem_convert_array[decode_buffer[0] & 0xff];
        break;
    }

    switch (rem) {
      case 2:
        outStream.write((byte) (((a << 2) & 0xfc) | ((b >>> 4) & 3)));
        break;
      case 3:
        outStream.write((byte) (((a << 2) & 0xfc) | ((b >>> 4) & 3)));
        outStream.write((byte) (((b << 4) & 0xf0) | ((c >>> 2) & 0xf)));
        break;
      case 4:
        outStream.write((byte) (((a << 2) & 0xfc) | ((b >>> 4) & 3)));
        outStream.write((byte) (((b << 4) & 0xf0) | ((c >>> 2) & 0xf)));
        outStream.write((byte) (((c << 6) & 0xc0) | (d & 0x3f)));
        break;
    }
  }

  /**
   * This method works around the bizarre semantics of BufferedInputStream's
   * read method.
   */
  protected int readFully(InputStream in, byte buffer[], int offset, int len)
      throws java.io.IOException {
    for (int i = 0; i < len; i++) {
      int q = in.read();
      if (q == -1) {
        return ((i == 0) ? -1 : i);
      }

      // byte ranges:
      //
      // + 43
      // / 47
      // [0-9] 48-57
      // = 61
      // [A-Z] 65-90
      // [a-z] 97-122

      // make sure the byte is one of the encoded bytes.  If it isn't, lets skip it.
      if (((q > 64) && (q < 91))
          || ((q > 96) && (q < 123))
          || ((q > 46) && (q < 58))
          || (q == 43) || (q == 61)) {
        buffer[i + offset] = (byte) q;
      } else {
        i--;
      }
    }
    return len;
  }

}
