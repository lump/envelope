package us.lump.envelope.client;

import junit.framework.TestCase;
import us.lump.envelope.Command;
import us.lump.envelope.Command.Name;
import us.lump.envelope.Command.Param;
import us.lump.envelope.TestSuite;
import us.lump.envelope.client.portal.SecurityPortal;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.entity.Identifiable;
import us.lump.envelope.server.rmi.Controller;
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

    LoginSettings ls = LoginSettings.getInstance();
    ls.setUsername("guest");
    ls.setPassword("guest");

    SecurityPortal sp = new SecurityPortal();
    Boolean authed = sp.auth(ls.challengeResponse(sp.getChallenge()));
    assertTrue("User does not auth", authed);

    try {
      // an entity object retrieval test
      Command cmd = new Command(Name.listTransactions)
              .set(Param.year, 2007).sign(user, kp.getPrivate());

//ugly old way
//      Authorized cmd = new Authorized(DataDispatch.Name.list_transactions)
//          .putParameter(SecurityDispatch.Param.user_name, user)
//          .putParameter(DataDispatch.Param.year, "2007")
//          .setCredentials(new Credentials(user))
//          .sign(kp.getPrivate());

//      assertTrue("verification falied", Encryption.verify(kp.getPublic(),String.valueOf(cmd.valueHashCode()),
//          cmd.getCredentials().getSignature()));


      List<Identifiable> list = (List<Identifiable>) controller.invoke(cmd);
      System.out.println(list.size());
      System.out.println(controller.invoke(new Command(Command.Name.authedPing).sign(user, kp.getPrivate())));
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