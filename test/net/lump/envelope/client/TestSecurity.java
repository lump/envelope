package us.lump.envelope.client;

import junit.framework.TestCase;
import org.junit.Test;
import us.lump.envelope.Command;
import us.lump.envelope.TestSuite;
import us.lump.envelope.client.portal.SecurityPortal;
import us.lump.envelope.client.portal.TransactionPortal;
import us.lump.envelope.entity.Transaction;
import us.lump.envelope.server.rmi.Controller;

import java.sql.Date;
import java.util.List;

public class TestSecurity extends TestCase {

  /**
   * tests a login.
   *
   * @throws Exception
   */
  @SuppressWarnings("unchecked")
  @Test
  public void testLogin() throws Exception {
    Controller controller = TestSuite.getController();

    SecurityPortal sp = new SecurityPortal();
    Boolean authed = sp.auth(
        TestSuite.loginSettings.challengeResponse(sp.getChallenge()));

    assertTrue("User does not auth", authed);

//    System.out.println("Encryption.decodePrivateKey(\""
//            + Encryption.encodeKey(kp.getPrivate()) + "\");");

//    System.out.println("Encryption.decodePublicKey(\""
//            + Encryption.encodeKey(kp.getPublic()) + "\");");

//    System.out.println("((Command)Encryption.thaw("
//            + "us.lump.lib.util.Base64.base64ToByteArray(\""
//            + us.lump.lib.util.Base64
//            .byteArrayToBase64(Encryption.freeze(cmd))
//            + "\"))).verify(kp.getPublic());");

    // an entity object retrieval test
//    List<Transaction> list = new TransactionPortal().listTransactions(2007);

    List<Transaction> list = new TransactionPortal().listTransactions(
        new Date(System.currentTimeMillis()),
        new Date(System.currentTimeMillis() - (86400000L * 30L)));
    assertTrue("list size is not > 0", list.size() > 0);

    try {
      System.out.println(
          controller.invoke(new Command(Command.Name.authedPing)
              .sign(TestSuite.USER,
                    TestSuite.loginSettings.getKeyPair().getPrivate())));
    } catch (Exception e) {
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