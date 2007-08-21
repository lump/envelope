package us.lump.envelope.server.security;

import us.lump.lib.util.Encryption;
import static us.lump.lib.util.Encryption.TRANS_ENCODING;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * An authorization challenge.
 *
 * @author Troy Bowman
 * @version $Id: Challenge.java,v 1.4 2007/08/21 03:14:52 troy Test $
 */
public class Challenge implements Serializable {
  private PublicKey serverKey;
  private byte[] challenge;

  /**
   * Instantiates a new challenge with the provided inforomation.
   *
   * @param serverKey the server public key to provide to the client
   * @param clientKey the public client key for encryption of the challenge
   * @param challenge the challeng string itself
   *
   * @throws NoSuchAlgorithmException
   * @throws BadPaddingException
   * @throws IllegalBlockSizeException
   * @throws InvalidKeyException
   * @throws NoSuchPaddingException
   */
  public Challenge(PublicKey serverKey, PublicKey clientKey, String challenge)
      throws NoSuchAlgorithmException, BadPaddingException,
      IllegalBlockSizeException, InvalidKeyException,
      NoSuchPaddingException, UnsupportedEncodingException {
    this.serverKey = serverKey;
    this.setChallenge(clientKey, challenge);
  }

  /**
   * Returns the the challenge, decrypted with the supplied private key.
   *
   * @param key the private key
   *
   * @return String challenge
   */
  public String getChallenge(PrivateKey key) throws NoSuchAlgorithmException,
      BadPaddingException, IllegalBlockSizeException, InvalidKeyException,
      NoSuchPaddingException, IOException {
    return new String(Encryption.decodeAsym(key, challenge), TRANS_ENCODING);
  }

  /**
   * Returns the public server key.
   *
   * @return PublicKey
   */
  public PublicKey getServerKey() {
    return serverKey;
  }

  /**
   * Sets the public server key.
   *
   * @param serverKey the key
   */
  public void setServerKey(PublicKey serverKey) {
    this.serverKey = serverKey;
  }

  /**
   * Takes the client public key and encrypts the challenge for the client.
   *
   * @param key       the public key of the client
   * @param challenge the challenge itself
   *
   * @throws BadPaddingException
   * @throws NoSuchAlgorithmException
   * @throws IllegalBlockSizeException
   * @throws InvalidKeyException
   * @throws NoSuchPaddingException
   */
  public void setChallenge(PublicKey key, String challenge) throws
      BadPaddingException, NoSuchAlgorithmException,
      IllegalBlockSizeException, InvalidKeyException,
      NoSuchPaddingException, UnsupportedEncodingException {
    this.challenge = Encryption.encodeAsym(key, challenge.getBytes());
  }
}
