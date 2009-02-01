package us.lump.envelope.client.portal;

import us.lump.envelope.Command;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.exception.AbortException;
import us.lump.envelope.server.security.Challenge;

import javax.swing.*;
import java.security.PublicKey;

/**
 * Security methods.
 *
 * @author Troy Bowman
 * @version $Id: SecurityPortal.java,v 1.11 2009/02/01 02:33:42 troy Alpha $
 */

public class SecurityPortal extends Portal {


  public Challenge getChallenge() throws AbortException {
    LoginSettings ls = LoginSettings.getInstance();
    Challenge challenge = (Challenge)rawInvoke(new Command(
        Command.Name.getChallenge,
        ls.getUsername(),
        ls.getKeyPair().getPublic()));
    if (challenge != null) ls.setServerKey(challenge.getServerKey());
    return challenge;
  }

  public Boolean auth(byte[] challengeResponse) throws AbortException {
    LoginSettings ls = LoginSettings.getInstance();
    return (Boolean)rawInvoke(new Command(
        Command.Name.authChallengeResponse,
        ls.getUsername(),
        challengeResponse
    ));
  }

  public Boolean rawPing() throws AbortException {
    return (Boolean)rawInvoke(new Command(Command.Name.ping));
  }

  public Boolean authedPing() throws AbortException {
    return (Boolean)invoke(new Command(Command.Name.authedPing));
  }

  public Boolean authedPing(JFrame jframe) throws AbortException {
    return (Boolean)invoke(jframe, new Command(Command.Name.authedPing));
  }

  public PublicKey getServerPublicKey() throws AbortException {
    return (PublicKey)(new SecurityPortal()).
        rawInvoke(new Command(Command.Name.getServerPublicKey));
  }
}
