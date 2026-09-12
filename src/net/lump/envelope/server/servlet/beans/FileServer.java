package net.lump.envelope.server.servlet.beans;

import org.apache.log4j.Logger;
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
 * @version $Id: FileServer.java,v 1.5 2009/10/02 22:06:23 troy Exp $
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
    String host = rq.getServerName()
        + ((rq.getServerPort() == 80 || rq.getServerPort() == 443) ? "" : ":" + rq.getServerPort());
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
       .append("<p>On first run it will ask for the server.  Enter:</p>")
       .append("<pre>Host: ").append(host).append("\nContext: ").append(rq.getContextPath()).append("</pre>")
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

  private void env() throws IOException {

    rp.setStatus(HttpServletResponse.SC_OK);
    rp.setHeader("Content-Type", "text/html");
    PrintWriter out = rp.getWriter();
    out.append("<html><head><title>Testing</title></head><body><table>");
    out.append("<tr><td>getPathInfo</td><td>").append(rq.getPathInfo()).append("</td></tr>");
    out.append("<tr><td>getPathTranslated</td><td>").append(rq.getPathTranslated()).append("</td></tr>");
    out.append("<tr><td>getServletPath</td><td>").append(rq.getServletPath()).append("</td></tr>");
    out.append("<tr><td>getContextPath</td><td>").append(rq.getContextPath()).append("</td></tr>");
    out.append("<tr><td>getMethod</td><td>").append(rq.getMethod()).append("</td></tr>");
    out.append("<tr><td>getAuthType</td><td>").append(rq.getAuthType()).append("</td></tr>");
    out.append("<tr><td>getContentType</td><td>").append(rq.getContentType()).append("</td></tr>");
    out.append("<tr><td>getQueryString</td><td>").append(rq.getQueryString()).append("</td></tr>");
    out.append("<tr><td>toString</td><td>").append(rq.toString()).append("</td></tr>");
    out.append("<tr><td>getServerName</td><td>").append(rq.getServerName()).append("</td></tr>");
    out.append("<tr><td>getRequestURI</td><td>").append(rq.getRequestURI()).append("</td></tr>");
    out.append("<tr><td>getRequestURL</td><td>").append(rq.getRequestURL()).append("</td></tr>");
    out.append("<tr><td>getProtocol</td><td>").append(rq.getProtocol()).append("</td></tr>");
    out.append("<tr><td>getRemoteAddr</td><td>").append(rq.getRemoteAddr()).append("</td></tr>");
    out.append("<tr><td>getRemotePort</td><td>").append(String.valueOf(rq.getRemotePort())).append("</td></tr>");
    out.append("<tr><td>getLocalName</td><td>").append(rq.getLocalName()).append("</td></tr>");
    out.append("<tr><td>getLocalAddr</td><td>").append(rq.getLocalAddr()).append("</td></tr>");
    out.append("<tr><td>getLocalPort</td><td>").append(String.valueOf(rq.getLocalPort())).append("</td></tr>");
//    out.append("<tr><td>get</td><td>").append(rq).append("</td></tr>");

    Enumeration e = rq.getHeaderNames();
    while (e.hasMoreElements()) {
      String s = (String)e.nextElement();
      out.append("<tr><td>header \"").append(s).append("\"</td><td>").append(rq.getHeader(s)).append("</td></tr>");
    }

    e = rq.getParameterNames();
    while (e.hasMoreElements()) {
      String s = (String)e.nextElement();
      out.append("<tr><td>parameter \"").append(s).append("\"</td><td>").append(rq.getHeader(s)).append("</td></tr>");
    }

    e = rq.getAttributeNames();
    while (e.hasMoreElements()) {
      String s = (String)e.nextElement();
      out.append("<tr><td>attribute \"").append(s).append("\"</td><td>").append(rq.getHeader(s)).append("</td></tr>");
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
