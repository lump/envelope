package us.lump.envelope;

import us.lump.envelope.client.Main;

/**
 * Client main code.
 *
 * @author Troy Bowman
 * @version $Id: Client.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */

@SuppressWarnings({"UnusedDeclaration"})
public class Client implements Runnable {

  @SuppressWarnings("unchecked")
  public void run() {

    // Retrieve the network location of the RMIServer location
    // This was stored in the system properties by the bootstrap
    // loader program.
//      KeyPair kp = Encryption.generateKeyPair();
//      String rmiName = (String)System.getProperties().get("java.rmi.server.rminode");
//      Controller controller = (Controller)Naming.lookup(rmiName + "Controller");

    javax.swing.SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        String rmiName = (String) System.getProperties().get("java.rmi.server.rminode");
        new Main().run();
      }
    });
  }

/*
      String user = "guest";

      Challenge challenge = (Challenge)controller
          .invoke(new Command(getChallenge)
              .set(Param.user_name, user)
              .set(Param.public_key, Encryption.encodePublicKey(kp)));

      String salt = Encryption.decodeAsym(kp.getPrivate(), challenge.getChallenge());

      Boolean authed = (Boolean)controller
          .invoke(new Command(authChallengeResponse)
              .set(Param.user_name, user)
              .set(Param.challenge_response,
              Encryption.encodeAsym(challenge.getServerKey(), Crypt.crypt(salt, "guest")))
          );

      assert (authed);

      List<Identifiable> list = (List<Identifiable>)controller
          .invoke(new Command(listTransactions)
              .set(Param.year, 2007)
              .sign(user, kp.getPrivate()));

      assert (list.size() > 0);
      System.out.println("blah");
    }
    catch (Exception e) {
      System.out.println("Client encountered an error: " + e);
      e.printStackTrace();
      System.exit(1);
    }
  }

*/
}
