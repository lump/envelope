package us.lump.lib.util;

import javax.crypto.Cipher;
import javax.crypto.NullCipher;
import javax.crypto.SecretKey;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.PBEParameterSpec;
import java.io.OutputStream;
import java.io.IOException;
import java.io.FilterOutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.security.spec.AlgorithmParameterSpec;

/**
 * A filtered output stream that transforms data written to it with a {@link
 * javax.crypto.Cipher} before sending it to the underlying output stream.
 * <p/>
 * From {@link javax.crypto.CipherOutputStream}.
 *
 * @author Troy Bowman
 */
public class CipherOutputStream extends FilterOutputStream {
  /** The underlying cipher. */
  private Cipher cipher;
  private String keyAlg;
  private SecretKey key;
  private boolean valid;

  /**
   * Create a new cipher output stream. The cipher argument must have already
   * been initialized.
   *
   * @param out    The sink for transformed data.
   * @param key    they secret key to cipher with.
   * @param keyAlg the algorithm to use.
   */
  public CipherOutputStream(OutputStream out, SecretKey key, String keyAlg)
      throws
      NoSuchAlgorithmException,
      NoSuchPaddingException,
      InvalidKeyException {
    super(out);
    this.keyAlg = keyAlg;
    this.key = key;
    initCipher();
  }

  private void initCipher()
      throws
      InvalidKeyException,
      NoSuchAlgorithmException,
      NoSuchPaddingException {

    cipher = Cipher.getInstance(keyAlg);
    cipher.init(Cipher.ENCRYPT_MODE, key);
    valid = true;
  }

  public int getBlockSize() {
    return cipher.getBlockSize();
  }
  
  /**
   * Create a cipher output stream with no cipher.
   *
   * @param out The sink for transformed data.
   */
  protected CipherOutputStream(OutputStream out) {
    super(out);
  }

  /**
   * Close this output stream, and the sink output stream.
   * <p/>
   * This method will first invoke the {@link Cipher#doFinal()} method of the
   * underlying {@link Cipher}, and writes the output of that method to the sink
   * output stream.
   *
   * @throws IOException If an I/O error occurs, or if an error is caused by
   *                     finalizing the transformation.
   */
  public void close() throws IOException {
    try {
      out.write(cipher.doFinal());
      out.flush();
      out.close();
      valid = false;
    }
    catch (Exception cause) {
      IOException ioex = new IOException(String.valueOf(cause));
      ioex.initCause(cause);
      throw ioex;
    }
  }

  /**
   * Flush any pending output.  Does a cipher.doFinal().
   *
   * @throws IOException If an I/O error occurs.
   */
  public void flush() throws IOException {
    try {
      out.write(cipher.doFinal());
      out.flush();
      valid = false;
    }
    catch (Exception cause) {
      IOException ioex = new IOException(String.valueOf(cause));
      ioex.initCause(cause);
      throw ioex;
    }
  }

  /**
   * Write a single byte to the output stream.
   *
   * @param b The next byte.
   *
   * @throws IOException If an I/O error occurs, or if the underlying cipher is
   *                     not in the correct state to transform data.
   */
  public void write(int b) throws IOException {
    write(new byte[]{(byte)b}, 0, 1);
  }

  /**
   * Write a byte array to the output stream.
   *
   * @param buf The next bytes.
   *
   * @throws IOException If an I/O error occurs, or if the underlying cipher is
   *                     not in the correct state to transform data.
   */
  public void write(byte[] buf) throws IOException {
    write(buf, 0, buf.length);
  }

  /**
   * Write a portion of a byte array to the output stream.
   *
   * @param buf The next bytes.
   * @param off The offset in the byte array to start.
   * @param len The number of bytes to write.
   *
   * @throws IOException If an I/O error occurs, or if the underlying cipher is
   *                     not in the correct state to transform data.
   */
  public void write(byte[] buf, int off, int len) throws IOException {
    if (!valid) try {
      initCipher();
    } catch (InvalidKeyException e) {
      throw new IOException(e.getMessage());
    } catch (NoSuchAlgorithmException e) {
      throw new IOException(e.getMessage());
    } catch (NoSuchPaddingException e) {
      throw new IOException(e.getMessage());
    }
    out.write(cipher.update(buf, off, len));
  }
}