package net.lump.envelope.shared.command.security;

import java.io.Serializable;

/**
 * The server's answer to a valid scramFinish.
 *
 * @param serverSignature proof the server holds this user's verifier; the client
 *                        checks it before trusting anything else here
 * @param wrappedSession  the session id, encrypted to the client key the proof covered
 */
public record ScramResult(byte[] serverSignature, byte[] wrappedSession) implements Serializable {
}
