package us.lump.envelope.client;

import junit.framework.TestCase;
import us.lump.envelope.Command;
import us.lump.envelope.TestSuite;
import us.lump.envelope.client.portal.SecurityPortal;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.server.rmi.Controller;

import java.security.KeyPair;

public class TestSecurity extends TestCase {
  @SuppressWarnings("unchecked")

  /**
   * tests a login. 
   */
  public void testLogin() throws Exception {
    String user = "guest";
    Controller controller = TestSuite.getController();

    LoginSettings ls = LoginSettings.getInstance();
    ls.setUsername("guest");
    ls.setPassword("guest");
    KeyPair kp = ls.getKeyPair();

    SecurityPortal sp = new SecurityPortal();
    Boolean authed = sp.auth(ls.challengeResponse(sp.getChallenge()));
    assertTrue("User does not auth", authed);

    // an entity object retrieval test
//    Command cmd = new Command(Command.Name.listTransactions)
//            .set(Command.Param.year, 2007).sign(user, kp.getPrivate());

//    System.out.println("Encryption.decodePrivateKey(\""
//            + Encryption.encodeKey(kp.getPrivate()) + "\");");

//    System.out.println("Encryption.decodePublicKey(\""
//            + Encryption.encodeKey(kp.getPublic()) + "\");");

//    System.out.println("((Command)Encryption.thaw("
//            + "us.lump.lib.util.Base64.base64ToByteArray(\""
//            + us.lump.lib.util.Base64
//            .byteArrayToBase64(Encryption.freeze(cmd))
//            + "\"))).verify(kp.getPublic());");

//    List<Identifiable> list = (List<Identifiable>) controller.invoke(cmd);
//    System.out.println(list.size());

    try {
      System.out.println(controller
              .invoke(new Command(Command.Name.authedPing)
              .sign(user, kp.getPrivate())));
    } catch(Exception e) {
      fail("Exception thrown");
    }
  }

  protected void setUp() throws Exception {
    super.setUp();
  }

  protected void tearDown() throws Exception {
    super.tearDown();
  }
}