package us.lump.envelope.client.portal;

import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.command.Command;
import us.lump.envelope.command.security.Challenge;
import us.lump.envelope.exception.AbortException;

import javax.swing.*;
import java.security.PublicKey;

/**
 * Security methods.
 *
 * @author Troy Bowman
 * @version $Id: SecurityPortal.java,v 1.12 2009/04/10 22:49:27 troy Exp $
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
    return (Boolean)rawInvoke(new Command(Command.Name.authChallengeResponse, null, ls.getUsername(), challengeResponse));
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
