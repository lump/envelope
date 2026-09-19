package net.lump.envelope.server.servlet.beans;

import org.apache.log4j.Logger;
import net.lump.envelope.server.dao.DAO;
import net.lump.lib.util.Interval;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.Enumeration;
import java.util.prefs.BackingStoreException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Everything the server answers that is not a command: the landing page, the
 * client download, the configuration form, and the health probes.
 *
 * <p>This used to be a Java Web Start launcher as well -- a generated JNLP, the
 * server's own jars served out of WEB-INF/lib as pack200, a security policy --
 * all of which went with Web Start's removal in JDK 11.  The client is now one
 * runnable jar, built into the war under WEB-INF/client where it is off the
 * classpath, and handed out from here.
 *
 * @author troy
 */
public class FileServer {

  /** where the war carries the client; under WEB-INF so Tomcat never lists it, but not under lib/ */
  private static final String CLIENT_JAR = "/WEB-INF/client/envelope-client.jar";
  private static final String CLIENT_JAR_NAME = "envelope-client.jar";

  private static final Logger logger = Logger.getLogger(FileServer.class);
  private HttpServletRequest rq;
  private HttpServletResponse rp;

  public FileServer(HttpServletRequest rq, HttpServletResponse rp) throws IOException, ServletException {
    this.rq = rq;
    this.rp = rp;
    serve();
  }

  protected void serve() throws ServletException, IOException {

    if (!ServerPrefs.getInstance().isConfigured() || rq.getServletPath().equals("/configure")) {
      try {
        if (!ServerPrefs.getInstance().configure(rq, rp)) return;
      } catch (BackingStoreException e) {
        rp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      }
    }

    String path = rq.getServletPath();

    if (path.equals("/" + CLIENT_JAR_NAME)) {
      feedClient();
      return;
    }

    if (path.matches("^(/|/index.*|/default.*)$")) {
      landingPage();
      return;
    }

    if (path.matches("^/env$")) {
      env();
      return;
    }

    if (path.matches("^/info.*$")) {
      feedInfo();
      return;
    }

    rp.sendError(HttpServletResponse.SC_NOT_FOUND);
  }

  /**
   * The front page: how to get the client and how to run it.  Written against
   * the request so the instructions name the host and port the reader actually
   * reached us on.
   */
  private void landingPage() throws IOException {
    // Scheme, host and port are as the visitor reached us -- behind the front
    // end's proxy that is what X-Forwarded-Proto / -Host say, which the
    // RemoteIpValve in the image makes Tomcat believe.
    boolean usual = ("https".equals(rq.getScheme()) && rq.getServerPort() == 443)
                 || ("http".equals(rq.getScheme()) && rq.getServerPort() == 80);
    String serverUrl = rq.getScheme() + "://" + rq.getServerName()
        + (usual ? "" : ":" + rq.getServerPort()) + rq.getContextPath();
    String jar = rq.getContextPath() + "/" + CLIENT_JAR_NAME;

    rp.setContentType("text/html;charset=UTF-8");
    PrintWriter out = rp.getWriter();
    out.append("<!doctype html><html><head><meta charset=\"utf-8\"><title>Envelope</title>")
       .append("<style>body{font-family:sans-serif;max-width:40em;margin:3em auto;line-height:1.5}")
       .append("code,pre{background:#f4f4f4;padding:.15em .4em;border-radius:3px}")
       .append("pre{padding:.8em 1em;overflow-x:auto}</style></head><body>")
       .append("<h1>Envelope</h1>")
       .append("<p>An envelope budget.  The client is a desktop application; download it, then run it")
       .append(" against this server.</p>")
       .append("<h2>1. Get the client</h2>")
       .append("<p><a href=\"").append(jar).append("\" download>").append(CLIENT_JAR_NAME).append("</a></p>")
       .append("<h2>2. Have Java 25 or later</h2>")
       .append("<p>The client needs a Java runtime, version 25 or newer.  <a href=\"https://adoptium.net/\">Temurin</a>")
       .append(" is a good free one for Windows, macOS and Linux.</p>")
       .append("<h2>3. Run it</h2>")
       .append("<pre>java -jar ").append(CLIENT_JAR_NAME).append("</pre>")
       .append("<p>On most desktops, double-clicking the file does the same.</p>")
       .append("<h2>4. Point it here</h2>")
       .append("<p>On first run it will ask for the server.  Paste this as the Server URL:</p>")
       .append("<pre>").append(serverUrl).append("</pre>")
       .append("<p>then log in with the user name and password you were given.</p>")
       .append("<hr><p><small><a href=\"").append(rq.getContextPath()).append("/configure\">Server configuration</a>")
       .append(" &middot; <a href=\"").append(rq.getContextPath()).append("/info/ping\">Health</a></small></p>")
       .append("</body></html>");
    out.flush();
  }

  /** Hand out the client jar built into the war. */
  private void feedClient() throws IOException {
    InputStream is = rq.getServletContext().getResourceAsStream(CLIENT_JAR);
    if (is == null) {
      logger.error("the war does not contain " + CLIENT_JAR + " -- was it built with mvn package?");
      rp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }
    try {
      rp.setContentType("application/java-archive");
      rp.setHeader("Content-Disposition", "attachment; filename=\"" + CLIENT_JAR_NAME + "\"");
      if (rq.getMethod().equals("HEAD")) return;

      OutputStream os = rp.getOutputStream();
      byte[] buffer = new byte[64 * 1024];
      long total = 0;
      for (int n; (n = is.read(buffer)) > 0; ) {
        os.write(buffer, 0, n);
        total += n;
      }
      os.flush();
      logger.info("served " + CLIENT_JAR_NAME + " (" + total + " bytes) to " + rq.getRemoteHost());
    } finally {
      is.close();
    }
  }

  /**
   * Escape a value for HTML text content.  Everything {@link #env} prints is
   * attacker-controlled -- the query string, the path, every header and parameter
   * name -- and it prints into text/html, so without this a crafted URL reflects
   * script back to whoever followed it.
   *
   * @param s the untrusted value, or null
   *
   * @return the value with the five XML significant characters escaped
   */
  private static String esc(Object s) {
    if (s == null) return "";
    return s.toString()
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;");
  }

  /** One table row: label and value, both escaped. */
  private static void row(PrintWriter out, String label, Object value) {
    out.append("<tr><td>").append(esc(label)).append("</td><td>").append(esc(value)).append("</td></tr>");
  }

  private void env() throws IOException {

    rp.setStatus(HttpServletResponse.SC_OK);
    rp.setHeader("Content-Type", "text/html");
    PrintWriter out = rp.getWriter();
    out.append("<html><head><title>Testing</title></head><body><table>");
    row(out, "getPathInfo", rq.getPathInfo());
    row(out, "getPathTranslated", rq.getPathTranslated());
    row(out, "getServletPath", rq.getServletPath());
    row(out, "getContextPath", rq.getContextPath());
    row(out, "getMethod", rq.getMethod());
    row(out, "getAuthType", rq.getAuthType());
    row(out, "getContentType", rq.getContentType());
    row(out, "getQueryString", rq.getQueryString());
    row(out, "toString", rq.toString());
    row(out, "getServerName", rq.getServerName());
    row(out, "getRequestURI", rq.getRequestURI());
    row(out, "getRequestURL", rq.getRequestURL());
    row(out, "getProtocol", rq.getProtocol());
    row(out, "getRemoteAddr", rq.getRemoteAddr());
    row(out, "getRemotePort", rq.getRemotePort());
    row(out, "getLocalName", rq.getLocalName());
    row(out, "getLocalAddr", rq.getLocalAddr());
    row(out, "getLocalPort", rq.getLocalPort());

    Enumeration e = rq.getHeaderNames();
    while (e.hasMoreElements()) {
      String s = (String)e.nextElement();
      row(out, "header \"" + s + "\"", rq.getHeader(s));
    }

    // these two read their own collections now; they used to call getHeader(s)
    // with a parameter or attribute name, which always answered null
    e = rq.getParameterNames();
    while (e.hasMoreElements()) {
      String s = (String)e.nextElement();
      row(out, "parameter \"" + s + "\"", rq.getParameter(s));
    }

    e = rq.getAttributeNames();
    while (e.hasMoreElements()) {
      String s = (String)e.nextElement();
      row(out, "attribute \"" + s + "\"", rq.getAttribute(s));
    }

    out.append("</table></body></html>");

    out.flush();
  }

  private void feedInfo() throws IOException {
    Matcher infoMatcher = Pattern.compile("^/(info/.*)$").matcher(rq.getServletPath());
    if (infoMatcher.matches() && infoMatcher.group(1) != null) {
      String query = infoMatcher.group(1);
      String returnValue = "";

      if (query.matches("^info/ping$")) returnValue = "pong";
      else if (query.matches("^info/uptime$")) returnValue = Interval.span(ManagementFactory.getRuntimeMXBean().getUptime());
      else if (query.matches("^info/ready$")) {
        // Readiness, as distinct from liveness.  /info/ping answers as soon as
        // Tomcat is up and says nothing about the database; a container was
        // reported healthy for hours while every login failed.  This one opens a
        // session and runs a query, and says what is wrong when it cannot.
        try {
          DAO.initialize(ServerPrefs.getInstance().getProps(DAO.class));
          // A DAO made outside Controller has to finish its own transaction here,
          // or every probe leaks a session -- the very thing that stalled the
          // server once before.  Completing the transaction is all it takes:
          // with current_session_context_class=thread, ThreadLocalSessionContext
          // closes and unbinds the session at completion.  This used to call
          // close() and disconnect() as well, and since the commit had already
          // disposed of the session each of those opened a FRESH one -- three
          // sessions per probe, two closed, the third left bound to this Tomcat
          // worker.  Every ten seconds, for the life of the container.
          net.lump.envelope.server.dao.Generic dao = new net.lump.envelope.server.dao.Generic();
          try {
            returnValue = dao.readyCheck();
            if (dao.isActive() && !dao.wasRolledBack()) dao.commit();
          } finally {
            // The path that does need handling is the failure one, where the query
            // threw and the transaction never completed, so nothing has unbound
            // the session.
            try {
              if (dao.isActive() && !dao.wasRolledBack()) dao.getTransaction().rollback();
            } catch (Exception rollbackFailed) {
              logger.warn("could not roll back a readiness probe", rollbackFailed);
            }
          }
        } catch (Exception e) {
          Throwable root = e;
          while (root.getCause() != null) root = root.getCause();
          byte[] out = ("not ready: " + root.getMessage()).getBytes();
          rp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
          rp.setHeader("Content-Length", String.valueOf(out.length));
          rp.setHeader("Content-Type", "text/plain");
          if (!rq.getMethod().equals("HEAD")) rp.getOutputStream().write(out);
          logger.warn("not ready: " + root.getMessage());
          return;
        }
      }
      else { rp.sendError(HttpServletResponse.SC_NOT_FOUND); return; }

      byte[] out = returnValue.getBytes();
      rp.setStatus(HttpServletResponse.SC_OK);
      rp.setHeader("Content-Length", String.valueOf(out.length));
      rp.setHeader("Content-Type", "text/plain");
      if (!rq.getMethod().equals("HEAD")) rp.getOutputStream().write(out);

      logger.info("returned " + returnValue + " for " + rq.getServletPath() + " from " + rq.getRemoteHost());
    }
    else { rp.sendError(HttpServletResponse.SC_NOT_FOUND); }
  }

}
