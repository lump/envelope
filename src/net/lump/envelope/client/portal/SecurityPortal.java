package net.lump.envelope.client.portal;

import net.lump.envelope.client.ui.prefs.LoginSettings;
import net.lump.envelope.shared.command.Command;
import net.lump.envelope.shared.command.security.Challenge;
import net.lump.envelope.shared.exception.AbortException;
import net.lump.lib.util.Encryption;

import javax.swing.*;
import java.security.PublicKey;

/**
 * Security methods.
 *
 * @author Troy Bowman
 * @version $Id: SecurityPortal.java,v 1.15 2009/10/02 22:06:23 troy Exp $
 */

public class SecurityPortal extends Portal {


  public Challenge getChallenge() throws AbortException {
    LoginSettings ls = LoginSettings.getInstance();

    Challenge challenge = (Challenge)rawInvoke(new Command(
      Command.Name.getChallenge,
      null,
      ls.getUsername(),
      ls.getKeyPair().getPublic()));

    if (challenge != null) ls.setServerKey(challenge.getServerKey());
    return challenge;
  }

  public Boolean auth(byte[] challengeResponse) throws AbortException {
    LoginSettings ls = LoginSettings.getInstance();

    // On success the server returns a session id encrypted to the key we just
    // proved we hold, so only this process can read it.
    byte[] wrapped = (byte[])rawInvoke(new Command(
      Command.Name.authChallengeResponse, null, ls.getUsername(), challengeResponse, ls.getKeyPair().getPublic()));

    if (wrapped == null) return false;

    try {
      ls.setSessionId(new String(
        Encryption.decodeAsym(ls.getKeyPair().getPrivate(), wrapped), Encryption.TRANS_ENCODING));
    } catch (Exception e) {
      throw new AbortException(e);
    }
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
