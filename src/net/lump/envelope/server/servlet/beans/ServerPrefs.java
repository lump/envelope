package us.lump.envelope.server.servlet.beans;

import us.lump.envelope.server.dao.DAO;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Configure stuff.
 *
 * @author troy
 * @version $Id: ServerPrefs.java,v 1.3 2009/06/28 16:21:35 troy Exp $
 */
public class ServerPrefs {

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

      configs.put(klass, prefs);

      if (!"ok".equals(prefs.get(klass.getSimpleName() + ".ok", null))) configured = false;
    }

    String serverPassword = configs.get(ServerPrefs.class).get("configure.password", null);
    if (serverPassword == null || serverPassword.matches("^\\s*$")) configured = false;

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

    if (username != null && username.length() > 0 && password != null && password.length() > 0) {
      boolean authorized = false;
      String authString = rq.getHeader("authorization");
      if (authString != null) {
        String[] creds = (new String(us.lump.lib.util.Base64.base64ToByteArray(
            authString.replaceAll("[Bb]asic\\s*", "")))).split(":");
        if (creds[0].equalsIgnoreCase(username) && creds[1].equals(password))
          authorized = true;
      }

      if (!authorized) {
        rp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        rp.setHeader("www-authenticate", "Basic realm=\"ServerPrefs\" domain=\"" + rq.getRequestURL() + "\"");
        rp.setContentType("text/html");
        rp.getWriter().append("<html><head><title>Unauthorized</title></head><body><h1>Unauthorized</h1></body></html>");
        rp.flushBuffer();
        return false;
      }
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
            configs.get(c).put(keyName, rq.getParameter(key));
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

          out.append("<input size=\"60\" name=\"")
              .append(c.getSimpleName())
              .append(".")
              .append(key)
              .append("\" value=\"")
              .append(p.get(key, ""))
              .append("\"");

          if (required.contains(c.getSimpleName() + "." + key) && (value.matches("^\\s*$")))
            out.append(" class=\"required\" ");

          if (key.contains("password")) out.append(" type=\"password\" ");

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
