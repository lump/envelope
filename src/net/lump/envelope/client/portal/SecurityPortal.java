package us.lump.envelope.client.portal;

import us.lump.envelope.Command;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.server.security.Challenge;
import us.lump.envelope.exception.EnvelopeException;

import javax.swing.*;
import javax.crypto.SecretKey;
import java.security.PublicKey;

/**
 * Security methods.
 *
 * @author Troy Bowman
 * @version $Id: SecurityPortal.java,v 1.9 2008/09/12 00:21:06 troy Exp $
 */

public class SecurityPortal extends Portal {


  public Challenge getChallenge() throws EnvelopeException {
    LoginSettings ls = LoginSettings.getInstance();
    Challenge challenge = (Challenge)rawInvoke(new Command(
        Command.Name.getChallenge,
        ls.getUsername(),
        ls.getKeyPair().getPublic()));
    ls.setServerKey(challenge.getServerKey());
    return challenge;
  }

  public Boolean auth(byte[] challengeResponse) throws EnvelopeException {
    LoginSettings ls = LoginSettings.getInstance();
    return (Boolean)rawInvoke(new Command(
        Command.Name.authChallengeResponse,
        ls.getUsername(),
        challengeResponse
    ));
  }

  public Boolean rawPing() throws EnvelopeException {
    return (Boolean)rawInvoke(new Command(Command.Name.ping));
  }

  public Boolean authedPing() throws EnvelopeException {
    return (Boolean)invoke(new Command(Command.Name.authedPing));
  }

  public Boolean authedPing(JFrame jframe) throws EnvelopeException {
    return (Boolean)invoke(jframe, new Command(Command.Name.authedPing));
  }

  public PublicKey getServerPublicKey() throws EnvelopeException {
    return (PublicKey)(new SecurityPortal()).
        rawInvoke(new Command(Command.Name.getServerPublicKey));
  }
}
