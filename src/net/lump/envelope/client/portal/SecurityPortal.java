package us.lump.envelope.client.portal;

import us.lump.envelope.server.security.Challenge;
import us.lump.envelope.Command;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.lib.util.Encryption;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.net.MalformedURLException;
import java.security.PublicKey;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.InvalidKeyException;

/**
 * Security methods.
 *
 * @author Troy Bowman
 * @version $Revision: 1.1 $
 */

public class SecurityPortal extends Portal {
  
  public Challenge getChallenge() {
    LoginSettings ls = LoginSettings.getInstance();
    return (Challenge)rawInvoke(new Command(Command.Name.getChallenge)
            .set(Command.Param.user_name, ls.getUsername())
            .set(Command.Param.public_key, Encryption.encodeKey(ls.getKeyPair().getPublic())));
  }

  public Boolean auth(String challengeResponse) {
    LoginSettings ls = LoginSettings.getInstance();
    return (Boolean)rawInvoke(new Command(Command.Name.authChallengeResponse)
            .set(Command.Param.user_name, ls.getUsername())
            .set(Command.Param.challenge_response, challengeResponse));
  }

  public Boolean rawPing() {
    return (Boolean)rawInvoke(new Command(Command.Name.ping));
  }

  public Boolean authedPing() {
    return (Boolean)invoke(new Command(Command.Name.authedPing));
  }
}
