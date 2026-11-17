package net.lump.envelope.client.portal;

import net.lump.envelope.client.ui.prefs.LoginSettings;
import net.lump.envelope.shared.command.Command;
import net.lump.envelope.shared.command.security.Scram;
import net.lump.envelope.shared.command.security.ScramChallenge;
import net.lump.envelope.shared.command.security.ScramResult;
import net.lump.envelope.shared.exception.EnvelopeException;
import net.lump.envelope.shared.exception.AbortException;
import net.lump.lib.util.Encryption;

import javax.swing.*;
import java.security.GeneralSecurityException;
import java.security.PublicKey;

/**
 * Security methods.
 *
 * @author Troy Bowman
 */

public class SecurityPortal extends Portal {


  /**
   * Log in: SCRAM-SHA-256 in two round trips (see Scram).  The password typed
   * into LoginSettings is used if there is one, else the keys remembered for this
   * user on this server.  The server has to prove it holds this account's
   * verifier before anything it returns is believed -- an impostor that answered
   * the first step cannot answer the second.  On success the session id it
   * returns, encrypted to this process's key, is kept in LoginSettings.
   *
   * @return true when logged in
   * @throws EnvelopeException Invalid_Credentials with no password to prove with
   */
  public Boolean login() throws AbortException, EnvelopeException {
    LoginSettings ls = LoginSettings.getInstance();
    String user = ls.getUsername();
    String clientNonce = Scram.newNonce();
    ScramChallenge challenge = (ScramChallenge)rawInvoke(new Command(Command.Name.scramStart, null, user, clientNonce));
    if (challenge == null) return false;
    ls.setServerKey(challenge.serverKey());

    byte[][] keys = ls.keysFor(challenge);   // {clientKey, serverKey}
    PublicKey mine = ls.getKeyPair().getPublic();
    byte[] authMessage = Scram.authMessage(user, clientNonce, challenge.nonce(), challenge.salt(), challenge.iterations(), mine);
    byte[] proof;
    try {
      proof = Scram.clientProof(keys[0], authMessage);
    } catch (GeneralSecurityException e) {
      throw new AbortException(e);
    }
    ScramResult result = (ScramResult)rawInvoke(new Command(Command.Name.scramFinish, null, challenge.nonce(), proof, mine));
    if (result == null) return false;

    try {
      if (!Scram.serverIsGenuine(keys[1], authMessage, result.serverSignature()))
        throw new AbortException("the server could not prove it holds this account -- it may not be the server it claims to be");
      ls.setSessionId(new String(
          Encryption.decodeAsym(ls.getKeyPair().getPrivate(), result.wrappedSession()), Encryption.TRANS_ENCODING));
    } catch (AbortException e) {
      throw e;
    } catch (Exception e) {
      throw new AbortException(e);
    }
    ls.remember(challenge, keys);
    return true;
  }

  public Boolean rawPing() throws AbortException {
    return (Boolean)rawInvoke(new Command(Command.Name.ping, null));
  }

  public Boolean authedPing() throws AbortException {
    return (Boolean)invoke(new Command(Command.Name.authedPing, null));
  }

  public Boolean authedPing(JFrame jframe) throws AbortException {
    return (Boolean)invoke(jframe, new Command(Command.Name.authedPing, null));
  }

  public PublicKey getServerPublicKey() throws AbortException {
    return (PublicKey)(new SecurityPortal()).invoke(new Command(Command.Name.getServerPublicKey, null));
  }
}
