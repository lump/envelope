package net.lump.envelope.server.servlet.beans;

import net.lump.envelope.server.dao.DAO;
import org.apache.log4j.Logger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Configure stuff.
 *
 * @author troy
 */
public class ServerPrefs {

  private static final Logger logger = Logger.getLogger(ServerPrefs.class);
  private static ServerPrefs instance = null;
  private static final Class[] classes = new Class[]{ServerPrefs.class, DAO.class};
  private static final HashMap<Class, Preferences> configs = new HashMap<Class, Preferences>();
  private boolean configured = true;

  private static final HashSet<String> required = new HashSet<String>();

  static {
    required.add("ServerPrefs.configure.password");
    required.add("ServerPrefs.configure.username");
    required.add("DAO.hibernate.connection.url");
    required.add("DAO.hibernate.connection.username");
    required.add("DAO.hibernate.connection.password");
  }

  private ServerPrefs() throws IOException {
    // yank the properties file from conventionized properties file

    for (Class klass : classes) {
      Properties config = new Properties();
      config.load(klass.getResourceAsStream(klass.getSimpleName() + ".properties"));

      // yank the preferences for class
      Preferences prefs = Preferences.userNodeForPackage(klass);

      // sync and sanitize the prefs and properties
      for (Object key : config.keySet()) {
        // if the pref is null, set it from the config.
        if (null == prefs.get((String)key, null)) prefs.put((String)key, config.getProperty((String)key));
      }

      // A system property or environment variable overrides both the stored pref and
      // the properties file, so a container can be pointed at a database without
      // rebuilding the war or carrying a java.util.prefs backing store.  For key
      // "hibernate.connection.url" on DAO that is -DDAO.hibernate.connection.url=...
      // or DAO_HIBERNATE_CONNECTION_URL=...
      // ".ok" is normally only set by submitting the /configure form, which a
      // container cannot do for itself; it is overridable so the stack can come up
      // unattended.
      HashSet<String> overridable = new HashSet<String>();
      for (Object key : config.keySet()) overridable.add((String)key);
      overridable.add(klass.getSimpleName() + ".ok");

      for (String key : overridable) {
        String override = lookupOverride(klass, key);
        if (override != null) prefs.put(key, override);
      }

      configs.put(klass, prefs);

      if (!"ok".equals(prefs.get(klass.getSimpleName() + ".ok", null))) configured = false;
    }

    String serverPassword = configs.get(ServerPrefs.class).get("configure.password", null);
    if (serverPassword == null || serverPassword.matches("^\\s*$")) configured = false;

  }

  /**
   * Find an external override for a config key, system property first, then
   * environment variable.
   *
   * @param klass the config class the key belongs to
   * @param key   the property name, e.g. "hibernate.connection.url"
   *
   * @return the override, or null if neither is set
   */
  private static String lookupOverride(Class klass, String key) {
    String prefix = klass.getSimpleName() + ".";
    // the ".ok" key already carries the class name, so don't double it up
    String qualified = key.startsWith(prefix) ? key : prefix + key;

    String value = System.getProperty(qualified);
    if (value != null) return value;

    return System.getenv(qualified.toUpperCase(Locale.ROOT).replaceAll("[.-]", "_"));
  }

  /**
   * Whether a config key holds a secret that must never be rendered back out.
   *
   * @param key the property name, qualified or not
   *
   * @return true if the value is a secret
   */
  private static boolean isSecret(String key) {
    String k = key.toLowerCase(Locale.ROOT);
    return k.contains("password") || k.contains("secret") || k.contains("token");
  }

  /**
   * Escape a value for an HTML attribute.  Config values are operator-supplied
   * rather than hostile, but a value carrying a quote would otherwise break out of
   * the attribute it is rendered into.
   *
   * @param s the value, or null
   *
   * @return the escaped value
   */
  private static String esc(String s) {
    if (s == null) return "";
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  public boolean isConfigured() {
    return configured;
  }

  public static ServerPrefs getInstance() throws IOException {
    if (instance == null) instance = new ServerPrefs();
    return instance;
  }

  public Preferences getPrefs(Class klass) {
    return configs.get(klass);
  }

  public Properties getProps(Class klass) throws IOException {
    Preferences prefs = getPrefs(DAO.class);

    Properties props = new Properties();
    try {
      for (String key : prefs.keys()) props.put(key, prefs.get(key, null));
    } catch (BackingStoreException ignore) { }
    return props;
  }

  /**
   * Configure the server, save preferences for persistence between deploys and jvm restarts.
   *
   * @param rq request
   * @param rp response
   *
   * @return boolean whether we're configured;
   *
   * @throws IOException           on io error for preferences
   * @throws BackingStoreException on backing store exception for preferences
   */
  public boolean configure(HttpServletRequest rq, HttpServletResponse rp) throws IOException, BackingStoreException {
    String username = configs.get(this.getClass()).get("configure.username", null);
    String password = configs.get(this.getClass()).get("configure.password", null);

    // This gate used to be skipped entirely when either credential was empty, which
    // is what ServerPrefs.properties ships -- so an installation that never set
    // SERVERPREFS_CONFIGURE_USERNAME/_PASSWORD served this form, and the live
    // database password in it, to anyone who could reach the webapp.  Fail closed
    // instead: no credentials configured means nobody may read or write here.
    if (username == null || username.length() == 0 || password == null || password.length() == 0) {
      logger.error("refusing /configure: configure.username and configure.password are not both set"
                   + " -- set SERVERPREFS_CONFIGURE_USERNAME and SERVERPREFS_CONFIGURE_PASSWORD");
      rp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
      rp.setContentType("text/html");
      rp.getWriter().append("<html><head><title>Not configurable</title></head><body><h1>Not configurable</h1>"
                            + "<p>This server has no configuration credentials set, so the configuration form"
                            + " is disabled.  Set <code>SERVERPREFS_CONFIGURE_USERNAME</code> and"
                            + " <code>SERVERPREFS_CONFIGURE_PASSWORD</code> and restart.</p></body></html>");
      rp.flushBuffer();
      return false;
    }

    boolean authorized = false;
    String authString = rq.getHeader("authorization");
    if (authString != null) {
      try {
        // split with a limit, and check the length: a credential with no colon at
        // all (Basic base64("admin"), or -u admin:) used to index creds[1] and throw
        // ArrayIndexOutOfBounds, which surfaced as an unauthenticated 500.
        String[] creds = (new String(net.lump.lib.util.Base64.base64ToByteArray(
            authString.replaceAll("[Bb]asic\\s*", "")), StandardCharsets.UTF_8)).split(":", 2);
        if (creds.length == 2
            && creds[0].equalsIgnoreCase(username)
            && MessageDigest.isEqual(creds[1].getBytes(StandardCharsets.UTF_8),
                                     password.getBytes(StandardCharsets.UTF_8)))
          authorized = true;
      } catch (RuntimeException e) {
        // a malformed authorization header is a failed login, not a server error
        logger.warn("could not parse an authorization header for /configure: " + e);
      }
    }

    if (!authorized) {
      rp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      rp.setHeader("www-authenticate", "Basic realm=\"ServerPrefs\" domain=\"" + rq.getRequestURL() + "\"");
      rp.setContentType("text/html");
      rp.getWriter().append("<html><head><title>Unauthorized</title></head><body><h1>Unauthorized</h1></body></html>");
      rp.flushBuffer();
      return false;
    }

    ArrayList<String> params = new ArrayList<String>();

    Enumeration e = rq.getParameterNames();
    while (e.hasMoreElements()) params.add((String)e.nextElement());

    if (params.size() > 0) {
      Collections.sort(params);
      for (Class c : classes) {
        int count = 0;
        for (String key : params) {
          if (key.substring(0, key.indexOf(".")).equals(c.getSimpleName())) {
            String keyName = key.substring(key.indexOf(".") + 1);
            String submitted = rq.getParameter(key);
            // The form no longer renders stored passwords, so an empty password
            // field means "leave this one as it is" rather than "blank it".  Any
            // other key takes the submitted value, empty or not.
            if (isSecret(keyName) && (submitted == null || submitted.isEmpty())) continue;
            configs.get(c).put(keyName, submitted);
            count++;
          }
        }
        if (count > 0) configs.get(c).put(c.getSimpleName() + ".ok", "ok");
      }

      configured = true;
      for (Class c : classes) {
        if (!"ok".equals(configs.get(c).get(c.getSimpleName() + ".ok", ""))) configured = false;
      }

      String serverPassword = configs.get(ServerPrefs.class).get("configure.password", null);
      if (serverPassword == null || serverPassword.matches("^\\s*$")) configured = false;
      if (configured && !rq.getRequestURL().toString().equals(rq.getHeader("referer"))) return configured;
    }


    rp.setStatus(HttpServletResponse.SC_OK);
    rp.setHeader("Content-Type", "text/html");
    PrintWriter out = rp.getWriter();
    out.append("<html><head>"
        + "<title>Configuration</title>"
        + "<style>"
        + "body { font-family: sans-serif; }"
        + "input, select, option, textarea { font-family: courier; border: none; }"
        + "input.required { background: #ffc0c0; }"
        + "table { border: 1px solid black; }"
        + "td { border: 1px solid #c0c0c0; }"
        + "td.input { border: 1px solid black; }"
        + "</style>"
        + "</head><body><form method=\"POST\">");

    for (Class c : configs.keySet()) {
      out.append("<h2>").append(c.getSimpleName()).append("</h2><table border=\"1\">");
      Preferences p = configs.get(c);

      String[] customizableNames = p.keys();
      Arrays.sort(customizableNames);

      for (String key : customizableNames) {
        // hide .ok
        if (key.equals(c.getSimpleName() + ".ok")) continue;


        out.append("<tr><td>").append(key).append("</td>").append("<td class=\"input\">");
        String value = p.get(key, "");
        if (value.matches("^(true|false)$")) {
          out.append("<select name=\"").append(c.getSimpleName()).append(".").append(key).append("\">");
          if (value.equals("true")) out.append("<option selected=\"true\">true</option><option>false</option>");
          else out.append("<option>true</option><option selected=\"true\">false</option>");
          out.append("</select>");
        } else {

          boolean secret = isSecret(key);

          out.append("<input size=\"60\" name=\"")
              .append(esc(c.getSimpleName()))
              .append(".")
              .append(esc(key))
              // A secret's stored value is never rendered.  type="password" only
              // masks it in a browser -- the value attribute was still in the page
              // source, so curl or view-source read the live database password in
              // cleartext.  Submit a new value to change it; leave it empty to keep
              // the one already stored.
              .append("\" value=\"")
              .append(secret ? "" : esc(value))
              .append("\"");

          if (required.contains(c.getSimpleName() + "." + key) && (value.matches("^\\s*$")))
            out.append(" class=\"required\" ");

          if (secret) {
            out.append(" type=\"password\" autocomplete=\"new-password\" placeholder=\"")
                .append(value.isEmpty() ? "not set" : "unchanged -- type to replace")
                .append("\" ");
          }

          out.append("/>");
        }


      }
      out.append("</table>");
    }

    out.append("<br/><input type=\"submit\" value=\"Submit\"/></body></html>");
    out.flush();

    return false;
  }
}
