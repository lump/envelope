package us.lump.envelope.client;

import junit.framework.TestCase;
import us.lump.envelope.TestSuite;
import us.lump.envelope.entity.Identifiable;
import us.lump.envelope.server.rmi.Cmd;
import us.lump.envelope.server.rmi.Command;
import us.lump.envelope.server.rmi.Controller;
import us.lump.envelope.server.rmi.Param;
import us.lump.envelope.server.security.Challenge;
import us.lump.envelope.server.security.Crypt;
import us.lump.lib.util.Encryption;

import java.security.KeyPair;
import java.util.List;

public class TestSecurity extends TestCase {
  @SuppressWarnings("unchecked")

  /**
   * tests a login. 
   */
  public void testLogin() throws Exception {
    KeyPair kp = Encryption.generateKeyPair();

    String user = "guest";
    Controller controller = TestSuite.getController();

    Challenge challenge = (Challenge)controller
        .invoke(new Command(Cmd.getChallenge)
            .set(Param.user_name, user)
            .set(Param.public_key, Encryption.encodePublicKey(kp)));

//    String salt = Encryption.decodeAsym(kp.getPrivate(), challenge.getChallenge());

    Boolean authed = (Boolean)controller
        .invoke(new Command(Cmd.authChallengeResponse)
            .set(Param.user_name, user)
            .set(Param.challenge_response,
            Encryption.encodeAsym(
                challenge.getServerKey(),
                Crypt.crypt(Encryption.decodeAsym(kp.getPrivate(), challenge.getChallenge()), user)))
        );

    assertTrue("User does not auth", authed);

    try {
      // an entity object retrieval test
      Command cmd = new Command(Cmd.listTransactions)
          .set(Param.year, 2007).sign(user, kp.getPrivate());

//ugly old way
//      Authorized cmd = new Authorized(DataDispatch.Name.list_transactions)
//          .putParameter(SecurityDispatch.Param.user_name, user)
//          .putParameter(DataDispatch.Param.year, "2007")
//          .setCredentials(new Credentials(user))
//          .sign(kp.getPrivate());

//      assertTrue("verification falied", Encryption.verify(kp.getPublic(),String.valueOf(cmd.valueHashCode()),
//          cmd.getCredentials().getSignature()));

      List<Identifiable> list = (List<Identifiable>)controller.invoke(cmd);
      System.out.println(list.size());
    } catch (Exception e) {
      //success
      Throwable t = e;
      boolean found = false;
      while (t != null)
        if (t.getCause() instanceof IllegalArgumentException) {
          found = true;
          break;
        } else {
          t = t.getCause();
        }

      assertTrue("Exception not IllegalArgumentException", found);
    }
  }

  protected void setUp() throws Exception {
    super.setUp();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }
}