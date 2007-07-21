package us.lump.lib.util.encdec;

import us.lump.lib.util.encdec.exception.CharacterEncoderStreamExhausted;

import java.io.*;
import java.nio.ByteBuffer;

/**
 * Taken from sun.misc.
 *
 * @author Troy Bowman
 * @author Chuck McManis
 * @version $Id: CharacterDecoder.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 * @see us.lump.lib.util.encdec.exception.CharacterEncoderFormatException
 * @see us.lump.lib.util.encdec.CharacterEncoder
 * @see us.lump.lib.util.encdec.Base64Decoder
 */

public abstract class CharacterDecoder {
  /**
   * Decode the text from the InputStream and write the decoded
   * octets to the OutputStream. This method runs until the stream
   * is exhausted.
   *
   * @throws us.lump.lib.util.encdec.exception.CharacterEncoderFormatException
   *          An error has occured while decoding
   * @throws CharacterEncoderStreamExhausted
   *          The input stream is unexpectedly out of data
   */
  public void decodeBuffer(InputStream aStream, OutputStream bStream) throws IOException {
    int i;
    int totalBytes = 0;

    PushbackInputStream ps = new PushbackInputStream(aStream);
    decodeBufferPrefix(ps, bStream);
    while (true) {
      int length;

      try {
        length = decodeLinePrefix(ps, bStream);
        for (i = 0; (i + bytesPerAtom()) < length; i += bytesPerAtom()) {
          decodeAtom(ps, bStream, bytesPerAtom());
          totalBytes += bytesPerAtom();
        }
        if ((i + bytesPerAtom()) == length) {
          decodeAtom(ps, bStream, bytesPerAtom());
          totalBytes += bytesPerAtom();
        } else {
          decodeAtom(ps, bStream, length - i);
          totalBytes += (length - i);
        }
        decodeLineSuffix(ps, bStream);
      }
      catch (CharacterEncoderStreamExhausted e) {
        break;
      }
    }
    decodeBufferSuffix(ps, bStream);
  }

  /**
   * Alternate decode interface that takes a String containing the encoded
   * buffer and returns a byte array containing the data.
   *
   * @throws us.lump.lib.util.encdec.exception.CharacterEncoderFormatException
   *          An error has occured while decoding
   */
  public byte decodeBuffer(String inputString)[]throws IOException {
    byte inputBuffer[] = inputString.getBytes();
    ByteArrayInputStream inStream;
    ByteArrayOutputStream outStream;

    inStream = new ByteArrayInputStream(inputBuffer);
    outStream = new ByteArrayOutputStream();
    decodeBuffer(inStream, outStream);
    return (outStream.toByteArray());
  }

  /**
   * Decode the contents of the inputstream into a buffer.
   */
  public byte decodeBuffer(InputStream in)[]throws IOException {
    ByteArrayOutputStream outStream = new ByteArrayOutputStream();
    decodeBuffer(in, outStream);
    return (outStream.toByteArray());
  }

  /**
   * Decode the contents of the String into a ByteBuffer.
   */
  public ByteBuffer decodeBufferToByteBuffer(String inputString)
      throws IOException {
    return ByteBuffer.wrap(decodeBuffer(inputString));
  }

  /**
   * Decode the contents of the inputStream into a ByteBuffer.
   */
  public ByteBuffer decodeBufferToByteBuffer(InputStream in)
      throws IOException {
    return ByteBuffer.wrap(decodeBuffer(in));
  }

  /**
   * Return the number of bytes per atom of decoding
   */
  abstract protected int bytesPerAtom();

  /**
   * Return the maximum number of bytes that can be encoded per line
   */
  abstract protected int bytesPerLine();

  /**
   * This method does an actual decode. It takes the decoded bytes and
   * writes them to the OutputStream. The integer <i>l</i> tells the
   * method how many bytes are required. This is always <= bytesPerAtom().
   */
  protected void decodeAtom(PushbackInputStream aStream, OutputStream bStream, int l)
      throws IOException {
    throw new CharacterEncoderStreamExhausted();
  }

  /**
   * decode the beginning of the buffer, by default this is a NOP.
   */
  protected void decodeBufferPrefix(PushbackInputStream aStream, OutputStream bStream)
      throws IOException {
  }

  /**
   * decode the buffer suffix, again by default it is a NOP.
   */
  protected void decodeBufferSuffix(PushbackInputStream aStream, OutputStream bStream)
      throws IOException {
  }

  /**
   * This method should return, if it knows, the number of bytes
   * that will be decoded. Many formats such as uuencoding provide
   * this information. By default we return the maximum bytes that
   * could have been encoded on the line.
   */
  protected int decodeLinePrefix(PushbackInputStream aStream, OutputStream bStream)
      throws IOException {
    return (bytesPerLine());
  }

  /**
   * This method post processes the line, if there are error detection
   * or correction codes in a line, they are generally processed by
   * this method. The simplest version of this method looks for the
   * (newline) character.
   */
  protected void decodeLineSuffix(PushbackInputStream aStream, OutputStream bStream)
      throws IOException {
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
      buffer[i + offset] = (byte) q;
    }
    return len;
  }
}
