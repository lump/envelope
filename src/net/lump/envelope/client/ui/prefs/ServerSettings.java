package net.lump.envelope.client.ui.prefs;

import net.lump.envelope.client.ui.defs.Strings;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.text.MessageFormat;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.lump.envelope.client.ui.prefs.ServerSettings.Field.*;

public class ServerSettings {
  private static ServerSettings singleton;
  Preferences prefs = Preferences.userNodeForPackage(this.getClass());

  private static ValidCache socketServerValidated = new ValidCache();

//  public static final String CONTROLLER = "Controller";
  public static final String DEFAULT_CLASS_PORT = "7041";
  public static final String PING = "/ping";

  enum Field {
    scheme,
    host,
    port,
    context,
    encrypt,
    compress
  }

  /** a full URL, from which scheme, host, port and context are all taken */
  private static final Pattern URL_FORM =
      Pattern.compile("^(https?)://([^/:\\s]+)(?::(\\d+))?(/[^\\s]*)?$", Pattern.CASE_INSENSITIVE);
  /** the old form: a host, optionally with a port */
  private static final Pattern HOST_FORM = Pattern.compile("^([^/:\\s]+)(?::(\\d+))?$");

  private ServerSettings() { }

  public static ServerSettings getInstance() {
    if (singleton == null) singleton = new ServerSettings();
    return singleton;
  }

  public String getHostName() {
    return prefs.get(host.name(), System.getProperty("codebase") == null ? "localhost" : System.getProperty("codebase") );
  }

  /** "http" or "https" -- which protocol the server is reached over. */
  public String getScheme() {
    return prefs.get(scheme.name(), "http");
  }

  public void setScheme(String value) {
    prefs.put(scheme.name(), "https".equalsIgnoreCase(value) ? "https" : "http");
  }

  /**
   * Take the server from what the user typed, which may be either of:
   *
   * <pre>
   *   https://envelope.example.com/envelope     a URL: scheme, host, port, context
   *   tiamat.lump:7041                           the old form: host and port, over http
   * </pre>
   *
   * <p>A URL sets everything it names.  Its port defaults to the scheme's own
   * (443 or 80) rather than to 7041, since a URL is how a server behind a front
   * end is given, and a front end listens on the usual ports.  A path sets the
   * context; no path leaves the context as it was.  The old form means http --
   * the protocol is whatever the endpoint was given as, and a bare host was
   * given without one.
   */
  public void setHostName(final String typed) {
    String text = typed == null ? "" : typed.trim();

    Matcher url = URL_FORM.matcher(text);
    if (url.matches()) {
      String sch = url.group(1).toLowerCase();
      setScheme(sch);
      prefs.put(host.name(), url.group(2));
      setPort(url.group(3) != null ? url.group(3) : ("https".equals(sch) ? "443" : "80"));
      String path = url.group(4);
      if (path != null && !path.equals("/")) setContext(path.replaceAll("/+$", ""));
      return;
    }

    Matcher hostOnly = HOST_FORM.matcher(text);
    if (hostOnly.matches()) {
      setScheme("http");
      prefs.put(host.name(), hostOnly.group(1));
      setPort(hostOnly.group(2));   // null means the default, 7041
    }
  }

  /**
   * The server as one URL, the way it is shown back to the user: scheme, host,
   * and the port unless it is the scheme's own.
   */
  public String getServerUrl() {
    String sch = getScheme();
    String prt = getPort();
    boolean usual = ("https".equals(sch) && "443".equals(prt)) || ("http".equals(sch) && "80".equals(prt));
    return sch + "://" + getHostName() + (usual ? "" : ":" + prt);
  }

  public String getContext() {
    return prefs.get(context.name(), "/envelope");
  }

  public void setContext(final String context) {
    prefs.put(Field.context.name(), context);
  }

  private String infoQuery(String query) throws IOException {
    URL url = new URL(this.getCodeBase().toString() + "info" + query);
    URLConnection c = url.openConnection();
    c.addRequestProperty("Accept", "text/plain");

    c.setDoInput(true);

    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));

    String output = "";
    String line;
    while ((line = r.readLine()) != null) {
      output += line;
    }
    r.close();
    return output;
  }

  public String getPort() {
    return prefs.get(port.name(), DEFAULT_CLASS_PORT);
  }

  public void setPort(String port) {
    if (port == null) port = DEFAULT_CLASS_PORT;
    prefs.put(Field.port.name(), port);
  }

  public boolean getEncrypt() {
    return prefs.getBoolean(encrypt.name(), false);
  }
  public void setEncrypt(boolean flag) {
    prefs.putBoolean(encrypt.name(), flag);
  }

  public boolean getCompress() {
    return prefs.getBoolean(compress.name(), false);
  }

  public void setCompress(boolean flag) {
    prefs.putBoolean(compress.name(), flag);
  }

  public URL getCodeBase() throws MalformedURLException {
    return new URL(getScheme() + "://" + getHostName() + ":" + getPort() + getContext() + "/");
  }

  public String testSocketServer() {
    String message = Strings.get("ok");
    if (socketServerValidated.isValid()) return message;

    try {
      // Not an ICMP ping first: a server behind a front end, or anywhere that
      // drops ICMP, is perfectly reachable and would have failed that.  The HTTP
      // ping is the test that means something.
      if (infoQuery(PING).matches("^pong")) {
        socketServerValidated.setValid(true);
      } else
        message = MessageFormat.format(
            Strings.get("error.verify.server"), this.getServerUrl());
    }
    catch (FileNotFoundException fnfe) {
      message = MessageFormat.format(
          Strings.get("error.verify.server"), this.getHostName());
    }
    catch (UnknownHostException uhe) {
      message = MessageFormat.format(
          Strings.get("error.unknown.host"), this.getHostName());
    }
    catch (ConnectException ce) {
      message = MessageFormat.format(
          Strings.get("error.could.not.connect.on.port"),
          this.getHostName(), this.getPort(), ce.getMessage());
    } catch (Exception e) {
      message = e.getClass().getSimpleName() + ": " + e.getMessage();
    }
    return message;
  }

  public void resetCache() {
    socketServerValidated.setValid(false);
  }

  // small object to maintain a cache of server validation
  private static class ValidCache {
    // 10 seconds
    private static final int CACHE = 3000;
    private boolean valid = false;
    private long stamp = 0;

    public ValidCache setValid(boolean valid) {
      this.valid = valid;
      this.stamp = System.currentTimeMillis();
      return this;
    }

    public boolean isValid() {
      return (valid && this.stamp > (System.currentTimeMillis() - CACHE));
    }
  }
}
