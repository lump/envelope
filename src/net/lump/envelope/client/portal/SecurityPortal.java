package us.lump.envelope.client.portal;

import us.lump.envelope.Command;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.server.security.Challenge;
import us.lump.envelope.exception.EnvelopeException;

import javax.swing.*;
import java.security.PublicKey;

/**
 * Security methods.
 *
 * @author Troy Bowman
 * @version $Revision: 1.7 $
 */

public class SecurityPortal extends Portal {

  private static PublicKey serverKey = null;

  public Challenge getChallenge() throws EnvelopeException {
    LoginSettings ls = LoginSettings.getInstance();
    Challenge challenge = (Challenge)rawInvoke(new Command(
        Command.Name.getChallenge,
        ls.getUsername(),
        ls.getKeyPair().getPublic()));
    serverKey = challenge.getServerKey();
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

  public static PublicKey getServerPublicKey() throws EnvelopeException {
    // if we don't have the serverKey saved already (from a challenge or this)
    if (serverKey == null) serverKey =
        // instantiate ourselves and invoke the command to get it
        (PublicKey)(new SecurityPortal()).
            rawInvoke(new Command(Command.Name.getServerPublicKey));
    return serverKey;
  }
}
