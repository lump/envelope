package us.lump.envelope.client.portal;

import us.lump.envelope.Command;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.server.security.Challenge;

/**
 * Security methods.
 *
 * @author Troy Bowman
 * @version $Revision: 1.4 $
 */

public class SecurityPortal extends Portal {

  public Challenge getChallenge() {
    LoginSettings ls = LoginSettings.getInstance();
    return (Challenge)rawInvoke(new Command(
        Command.Name.getChallenge,
        ls.getUsername(),
        ls.getKeyPair().getPublic()));
  }

  public Boolean auth(byte[] challengeResponse) {
    LoginSettings ls = LoginSettings.getInstance();
    return (Boolean)rawInvoke(new Command(
        Command.Name.authChallengeResponse,
        ls.getUsername(),
        challengeResponse
    ));
  }

  public Boolean rawPing() {
    return (Boolean)rawInvoke(new Command(Command.Name.ping));
  }

  public Boolean authedPing() {
    return (Boolean)invoke(new Command(Command.Name.authedPing));
  }
}
