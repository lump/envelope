package net.lump.envelope.client;

import junit.framework.TestCase;
import net.lump.envelope.shared.command.security.Crypt;
import net.lump.envelope.shared.command.security.Scram;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Base64;

/**
 * The SCRAM-SHA-256 derivations, against RFC 7677's own test vector, and the
 * properties the login leans on.  Needs no server.
 */
public class TestScram extends TestCase {

  /** RFC 7677 section 3: user "user", password "pencil". */
  public void testRfc7677Vector() throws Exception {
    byte[] salt = Base64.getDecoder().decode("W22ZaJ0SNY7soEsUEjb6gQ==");
    String authMessage = "n=user,r=rOprNGfwEbeRWgbNEkqO,"
        + "r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0,s=W22ZaJ0SNY7soEsUEjb6gQ==,i=4096,"
        + "c=biws,r=rOprNGfwEbeRWgbNEkqO%hvYDpWUa2RaTCAfuxFIlj)hNlF$k0";
    byte[] am = authMessage.getBytes(StandardCharsets.US_ASCII);

    byte[] salted = Scram.saltedPassword("pencil", salt, 4096);
    byte[] clientKey = Scram.clientKey(salted);
    byte[] proof = Scram.clientProof(clientKey, am);
    assertEquals("dHzbZapWIk4jUhN+Ute9ytag9zjfMHgsqmmiz7AndVQ=", Base64.getEncoder().encodeToString(proof));

    byte[] serverSignature = Scram.serverSignature(Scram.serverKey(salted), am);
    assertEquals("6rriTRBi23WpRR/wtup+mMhUZUn/dB5nLTJRsjl95G4=", Base64.getEncoder().encodeToString(serverSignature));

    assertTrue("the server must accept the RFC's proof", Scram.proofIsValid(Scram.storedKey(clientKey), am, proof));
    assertTrue("the client must accept the RFC's signature", Scram.serverIsGenuine(Scram.serverKey(salted), am, serverSignature));
  }

  /** What the login depends on: a proof is good for its own exchange only, and the stored values make none. */
  public void testProofProperties() throws Exception {
    PublicKey me = keyPair(), someoneElse = keyPair();
    Scram.Verifier v = Scram.newVerifier("correct horse");
    byte[] salted = Scram.saltedPassword(Crypt.crypt(v.md5Salt, "correct horse"), v.salt, v.iterations);
    byte[] clientKey = Scram.clientKey(salted);
    assertTrue(java.util.Arrays.equals(v.storedKey, Scram.storedKey(clientKey)));

    byte[] am = Scram.authMessage("guest", "cnonce", "cnonce+snonce", v.salt, v.iterations, me);
    byte[] proof = Scram.clientProof(clientKey, am);
    assertTrue(Scram.proofIsValid(v.storedKey, am, proof));

    byte[] otherExchange = Scram.authMessage("guest", "cnonce", "cnonce+other", v.salt, v.iterations, me);
    assertFalse("a proof replayed into another exchange", Scram.proofIsValid(v.storedKey, otherExchange, proof));
    byte[] otherKey = Scram.authMessage("guest", "cnonce", "cnonce+snonce", v.salt, v.iterations, someoneElse);
    assertFalse("a proof lifted onto another client key", Scram.proofIsValid(v.storedKey, otherKey, proof));

    byte[] wrongPassword = Scram.clientKey(Scram.saltedPassword(Crypt.crypt(v.md5Salt, "wrong"), v.salt, v.iterations));
    assertFalse("the wrong password", Scram.proofIsValid(v.storedKey, am, Scram.clientProof(wrongPassword, am)));
    assertFalse("a thief using StoredKey as the client key", Scram.proofIsValid(v.storedKey, am, Scram.clientProof(v.storedKey, am)));
    assertFalse("a thief using ServerKey as the client key", Scram.proofIsValid(v.storedKey, am, Scram.clientProof(v.serverKey, am)));

    assertTrue(Scram.serverIsGenuine(v.serverKey, am, Scram.serverSignature(v.serverKey, am)));
    assertFalse("an impostor without the verifier", Scram.serverIsGenuine(v.serverKey, am, Scram.serverSignature(Scram.randomBytes(32), am)));
  }

  private static PublicKey keyPair() throws Exception {
    KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
    g.initialize(1024);
    return g.generateKeyPair().getPublic();
  }
}
