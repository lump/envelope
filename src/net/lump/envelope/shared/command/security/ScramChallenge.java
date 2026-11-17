package net.lump.envelope.shared.command.security;

import java.io.Serializable;
import java.security.PublicKey;

/**
 * The server's answer to scramStart: what the client needs to compute its proof.
 * An unknown user gets one of these too, made up and stable, so the answer says
 * nothing about who exists.
 *
 * @param nonce      the client's nonce with the server's appended; both ends sign it
 * @param md5Salt    the md5-crypt salt the password is pre-hashed under
 * @param salt       the SCRAM salt
 * @param iterations the PBKDF2 iteration count
 * @param serverKey  the server's public key, for the optional request encryption
 */
public record ScramChallenge(String nonce, String md5Salt, byte[] salt, int iterations, PublicKey serverKey)
    implements Serializable {
}
