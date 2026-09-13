package net.lump.envelope.client.tls;

import org.apache.log4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

/**
 * What the client trusts when it speaks https.
 *
 * <p>Two things, in this order: the Java runtime's own certificate authorities,
 * so a server with a publicly issued certificate just works; and then the
 * authorities bundled in this package, so a server signed by the private CA
 * works too.  A handshake succeeds if either set vouches for the chain.
 *
 * <p>Bundling the CA in the jar is what lets a downloaded client connect with
 * no per-machine setup -- no -Djavax.net.ssl.trustStore, no importing into
 * cacerts.  Only a CA's public certificate goes in, never a key.
 *
 * <p>Installed once, as the default for every HttpsURLConnection the client
 * opens.  A private CA whose file is missing or unreadable is logged and
 * skipped rather than fatal: the client still works against public
 * certificates, and against plain http.
 */
public final class ClientTrust {

  private static final Logger logger = Logger.getLogger(ClientTrust.class);

  /** the CA certificates carried in this package, by resource name */
  private static final String[] BUNDLED = {"lumpnet-ca.crt"};

  private static boolean installed = false;

  private ClientTrust() {}

  /** Make every https connection the client opens trust the bundled CAs too. */
  public static synchronized void install() {
    if (installed) return;
    installed = true;

    X509TrustManager bundled;
    try {
      bundled = bundledTrust();
    } catch (Exception e) {
      logger.warn("could not read the bundled certificate authorities; only the platform's stand", e);
      return;
    }
    if (bundled == null) return;   // nothing to add; the platform default stands

    // The platform's own trust can itself be unusable -- a JRE whose cacerts is
    // empty, or a -Djavax.net.ssl.trustStore pointing at nothing -- and that must
    // not take the bundled CA down with it.  Then the bundled CA is all there is.
    X509TrustManager platform = null;
    try {
      platform = platformTrust();
    } catch (Exception e) {
      logger.warn("the platform's certificate authorities are unusable; only the bundled CA(s) stand", e);
    }

    try {
      TrustManager trust = platform == null ? bundled : new EitherTrust(platform, bundled);
      SSLContext context = SSLContext.getInstance("TLS");
      context.init(null, new TrustManager[]{trust}, null);
      HttpsURLConnection.setDefaultSSLSocketFactory(context.getSocketFactory());
      logger.info("https connections trust " + (platform == null ? "" : "the platform CAs and ")
          + BUNDLED.length + " bundled CA(s)");
    } catch (Exception e) {
      logger.warn("could not install the bundled certificate authorities; only the platform's stand", e);
    }
  }

  /** The runtime's own trust: cacerts, or whatever -Djavax.net.ssl.trustStore names. */
  private static X509TrustManager platformTrust() throws Exception {
    TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    f.init((KeyStore)null);
    return firstX509(f);
  }

  /** A trust manager over the CAs in this package, or null if none could be read. */
  private static X509TrustManager bundledTrust() throws Exception {
    KeyStore store = KeyStore.getInstance(KeyStore.getDefaultType());
    store.load(null, null);
    CertificateFactory certs = CertificateFactory.getInstance("X.509");

    int loaded = 0;
    for (String name : BUNDLED) {
      InputStream in = ClientTrust.class.getResourceAsStream(name);
      if (in == null) {
        logger.warn("bundled CA " + name + " is not in the jar");
        continue;
      }
      try {
        // a .crt may hold a chain; take every certificate in it
        for (java.security.cert.Certificate c : certs.generateCertificates(in)) {
          X509Certificate x = (X509Certificate)c;
          store.setCertificateEntry(name + "#" + loaded, x);
          logger.info("trusting bundled CA: " + x.getSubjectX500Principal().getName());
          loaded++;
        }
      } catch (CertificateException e) {
        logger.warn("bundled CA " + name + " could not be parsed", e);
      } finally {
        try { in.close(); } catch (IOException ignore) {}
      }
    }
    if (loaded == 0) return null;

    TrustManagerFactory f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    f.init(store);
    return firstX509(f);
  }

  private static X509TrustManager firstX509(TrustManagerFactory f) {
    for (TrustManager t : f.getTrustManagers())
      if (t instanceof X509TrustManager) return (X509TrustManager)t;
    throw new IllegalStateException("no X509TrustManager from " + f.getAlgorithm());
  }

  /** Accepts a chain if either of two trust managers does. */
  private static final class EitherTrust implements X509TrustManager {
    private final X509TrustManager first, second;

    EitherTrust(X509TrustManager first, X509TrustManager second) {
      this.first = first;
      this.second = second;
    }

    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
      Exception platformSaidNo;
      try {
        first.checkServerTrusted(chain, authType);
        return;
      } catch (CertificateException e) {
        platformSaidNo = e;
      } catch (RuntimeException e) {
        // A platform trust manager over an EMPTY store does not refuse a chain
        // politely: it throws "the trustAnchors parameter must be non-empty",
        // wrapped in a RuntimeException.  Catching only CertificateException here
        // let that escape the handshake before the bundled CA was ever asked --
        // which is precisely the machine the bundled CA exists for.
        platformSaidNo = e;
      }

      try {
        second.checkServerTrusted(chain, authType);
      } catch (CertificateException bundledSaidNo) {
        // report the platform's reason, which is the usual one, with the
        // bundled refusal attached so neither is lost
        CertificateException out = platformSaidNo instanceof CertificateException
            ? (CertificateException)platformSaidNo
            : new CertificateException("the platform's certificate authorities are unusable", platformSaidNo);
        out.addSuppressed(bundledSaidNo);
        throw out;
      }
    }

    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {
      // the client never authenticates a client; defer to the platform
      first.checkClientTrusted(chain, authType);
    }

    public X509Certificate[] getAcceptedIssuers() {
      List<X509Certificate> all = new ArrayList<X509Certificate>();
      for (X509Certificate c : first.getAcceptedIssuers()) all.add(c);
      for (X509Certificate c : second.getAcceptedIssuers()) all.add(c);
      return all.toArray(new X509Certificate[0]);
    }
  }
}
