package us.lump.envelope.client.portal;

import us.lump.envelope.client.ui.prefs.ServerSettings;
import us.lump.envelope.Command.Name;
import us.lump.envelope.Command;
import us.lump.envelope.server.rmi.Controller;
import us.lump.envelope.Command.Param;
import us.lump.envelope.server.security.Challenge;
import us.lump.lib.util.Encryption;

import java.net.MalformedURLException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.security.PublicKey;

/**
 * .
 *
 * @author troy
 * @version $Id: Getter.java,v 1.3 2007/07/26 06:52:06 troy Exp $
 */
public class Getter {

  private Controller controller;

  public Getter() throws MalformedURLException, NotBoundException, RemoteException {
    controller = (Controller)Naming.lookup(ServerSettings.getInstance().rmiNode() + "Controller");
  }

  public Challenge getChallenge(String username, PublicKey key) throws RemoteException {
    return (Challenge)controller
            .invoke(new Command(Name.getChallenge)
                    .set(Param.user_name, username)
                    .set(Param.public_key, Encryption.encodeKey(key)));
  }

  public Boolean auth(String username, String response) throws RemoteException {
    return (Boolean)controller
        .invoke(new Command(Name.authChallengeResponse)
            .set(Param.user_name, username)
            .set(Param.challenge_response, response));

//            Encryption.encodeAsym(
//                challenge.getServerKey(),
//                Crypt.crypt(Encryption.decodeAsym(kp.getPrivate(), challenge.getChallenge()), user)))

  }


  public void get() {
    try {
      Controller controller = (Controller)Naming.lookup(ServerSettings.getInstance().rmiNode() + "Controller");
      String s = (String)controller.invoke(new Command(Name.ping));
      System.out.println("got this from ping command:" + s);
    } catch (NotBoundException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    } catch (MalformedURLException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    } catch (RemoteException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }


  }
}
