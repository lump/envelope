package us.lump.envelope.server.security;

import us.lump.lib.util.Encryption;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.io.Serializable;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

/**
 * A authorization challenge.
 *
 * @author Troy Bowman
 * @version $Id: Challenge.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */
public class Challenge implements Serializable {
  private PublicKey serverKey;
  private String challenge;

  /**
   * Instantiates a new challenge with the provided inforomation.
   * @param serverKey the server public key to provide to the client
   * @param clientKey the public client key for encryption of the challenge
   * @param challenge the challeng string itself
   * @throws NoSuchAlgorithmException
   * @throws BadPaddingException
   * @throws IllegalBlockSizeException
   * @throws InvalidKeyException
   * @throws NoSuchPaddingException
   */
  public Challenge(PublicKey serverKey, PublicKey clientKey, String challenge)
      throws NoSuchAlgorithmException, BadPaddingException, IllegalBlockSizeException,
      InvalidKeyException, NoSuchPaddingException {
    this.serverKey = serverKey;
    this.setChallenge(clientKey, challenge);
  }

  /**
   * Returns the challenge string.
   * @return String
   */
  public String getChallenge() {
    return challenge;
  }

  /**
   * Returns the public server key.
   * @return PublicKey
   */
  public PublicKey getServerKey() {
    return serverKey;
  }

  /**
   * Sets the public server key.
   * @param serverKey the key
   */
  public void setServerKey(PublicKey serverKey) {
    this.serverKey = serverKey;
  }

  /**
   * Takes the client public key and encrypts the challenge for the client.
   *
   * @param key the public key of the client
   * @param challenge the challenge itself
   * @throws BadPaddingException
   * @throws NoSuchAlgorithmException
   * @throws IllegalBlockSizeException
   * @throws InvalidKeyException
   * @throws NoSuchPaddingException
   */
  public void setChallenge(PublicKey key, String challenge) throws
      BadPaddingException, NoSuchAlgorithmException, IllegalBlockSizeException,
      InvalidKeyException, NoSuchPaddingException {
    this.challenge = Encryption.encodeAsym(key, challenge);
  }
}
