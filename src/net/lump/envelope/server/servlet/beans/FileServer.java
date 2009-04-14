package us.lump.envelope.server.servlet.beans;

import org.apache.log4j.Logger;
import us.lump.envelope.server.servlet.jnlp.Jnlp;
import us.lump.lib.util.Interval;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.lang.management.ManagementFactory;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.prefs.BackingStoreException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Serves static files.
 *
 * @author troy
 * @version $Id: FileServer.java,v 1.3 2009/04/14 05:37:27 troy Exp $
 */
public class FileServer {

  private static final HashSet<String> blackList = new HashSet<String>();
  private static final Logger logger = Logger.getLogger(FileServer.class);
  private HttpServletRequest rq;
  private HttpServletResponse rp;
  private String contentType = null;

  static {
    blackList.add("us/lump/envelope/server/dao");
    blackList.add("us/lump/envelope/server/http");
    blackList.add("us/lump/envelope/server/log");
    blackList.add("us/lump/envelope/server/rmi/Controlled.class");
  }

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

    if (rq.getServletPath().equals("/envelope.jnlp")) {
      feedJnlp();
      return;
    }

    if (rq.getServletPath().matches("^(/|/index.*|/default.*)$")) {

//      String newUrl = MessageFormat.format(
//          "http://{0}{1}{2}/envelope.jnlp",
//          rq.getServerName(),
//          (rq.getLocalPort() != 80) ? ":" + String.valueOf(rq.getLocalPort()) : "",
//          rq.getContextPath());
//
      rp.setContentType("text/html");
//      rp.setDateHeader("Expires", 0);
//      rp.setHeader("Location", newUrl);
//      rp.addHeader("Refresh", "0;url=" + newUrl);
//      rp.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
//      return;
      PrintWriter out = rp.getWriter();
      out.append("<html><head><title>Envelope</title><style><!-- body { font-family: sans-serif; } --></style>")
          .append("</head><body><ul><li><a href=\"").append(rq.getContextPath())
          .append("/envelope.jnlp\">Envelope JNLP</a></li><li><a href=\"").append(rq.getContextPath())
          .append("/configure\">Server Configuration</a></li></ul></body></html>");

      out.flush();
      return;
    }

    if (rq.getServletPath().matches("^/env$")) {
      env();
      return;
    }

    if (rq.getServletPath().matches("^/info.*$")) {
      feedInfo();
      return;
    }

    if (rq.getServletPath().matches("^/(lib|us)/.*$")) {
      feedLib();
    }
  }

  private void feedJnlp() throws IOException {
    try {
      rp.setHeader("Content-Disposition", "name=envelope.jnlp\r\n");
      rp.setDateHeader("Last-Modified", System.currentTimeMillis());
      contentType = "application/x-java-jnlp-file";
      blortStream(
          new ByteArrayInputStream(new Jnlp(rq.getServerName(), rq.getServerPort(), rq.getContextPath()).toString().getBytes()));
    } catch (IOException e) {
      rp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }
  }

  private void feedLib() throws IOException {

    if (blackList.contains(rq.getServletPath()) || rq.getServletPath().contains("..")) {
      rp.sendError(HttpServletResponse.SC_FORBIDDEN);
      return;
    }

    InputStream is = rq.getSession().getServletContext().getResourceAsStream("WEB-INF" + rq.getServletPath());
//    URL resource = null;
//    if (is != null) resource = rq.getSession().getServletContext().getResource("WEB-INF" + rq.getServletPath());


//    if (is == null) {
//      String classPath = rq.getServletPath().replaceAll("^[/\\\\]", "");
//      if ((is = this.getClass().getClassLoader().getResourceAsStream(classPath)) != null)
//        resource = this.getClass().getClassLoader().getResource(classPath);
//    }

    if (is == null) {
      rp.sendError(HttpServletResponse.SC_NOT_FOUND);
      return;
    }

    blortStream(is);
  }

  private void blortStream(InputStream is) throws IOException {

    String encoding = "raw";
    BufferedInputStream bis = new BufferedInputStream(is);
    bis.mark(32);
    byte[] firstBytes = new byte[24];
    int firstBytesRead = bis.read(firstBytes, 0, 24);
    bis.reset();

    OutputStream os = rp.getOutputStream();

    if (firstBytesRead == 24) {
      int magic =
          (firstBytes[0] & 0xff) << 24 | (firstBytes[1] & 0xff) << 16 | (firstBytes[2] & 0xff) << 8 | (firstBytes[3] & 0xff);

      if (contentType == null) {
        if (magic == 0xcafebabe) // 0xcafebabe = java classes
          contentType = "application/java";
        else if ((magic) == 0xcafed00d) // 0xcafed00d = pk200
          contentType = "application/x-java-pack200";
        else if ((magic >> 16) == ('P' << 8 | 'K')) // PKzip files have a ascii magic of PK :)
          if (Pattern.compile("^.*\\.jar$", Pattern.CASE_INSENSITIVE).matcher(rq.getServletPath()).matches())
            contentType = "application/java-archive";
          else contentType = "application/zip";
        else if (((magic >> 24) | ((magic >> 8) & 0xff00)) == GZIPInputStream.GZIP_MAGIC) {
          if (Pattern.compile("^.*\\.jar.pack.gz$", Pattern.CASE_INSENSITIVE).matcher(rq.getServletPath()).matches()) {
            contentType = "application/java-archive";
            encoding = "pack200-gzip";
          }
          else contentType = "application/x-gzip";
        }
        else if (magic == 0x89504e47) //png = 0x89 + "PNG"
          contentType = "image/png";
        else if ((new String(firstBytes)).matches("^\\s*<\\?xml.*")) contentType = "text/xml";
      }


      String[] encodings =
          rq.getHeader("accept-encoding") != null ? rq.getHeader("accept-encoding").split("\\s*,\\s*") : new String[]{"raw"};

      for (String format : encodings) {
        if (format.equals("raw") || encoding.equals("pack200-gzip")) {
          break;
        }
        else if (format.equals("gzip") && ((magic >> 16) != GZIPInputStream.GZIP_MAGIC)) {
          encoding = "gzip";
          os = new GZIPOutputStream(os);
          break;
        }
        else if (format.equals("deflate")) {
          encoding = "deflate";
          os = new DeflaterOutputStream(os);
          break;
        }
      }
    }

    rp.setStatus(HttpServletResponse.SC_OK);
    if (!encoding.equals("raw")) rp.setHeader("Content-Encoding", encoding);
    rp.setHeader("Content-Type", contentType);

    if (rq.getMethod().equals("HEAD")) os.close();
    else {
      byte[] buffer = new byte[524288];
      int read;
      while ((read = bis.read(buffer, 0, 524288)) > 0) {
        os.write(buffer, 0, read);
      }
      os.flush();
      os.close();
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
    Matcher infoMatcher = Pattern.compile("^/(info/.*|log4j.properties|)$").matcher(rq.getServletPath());
    if (infoMatcher.matches() && infoMatcher.group(1) != null) {
      String query = infoMatcher.group(1);
      String returnValue = "";

      if (query.matches("^info/ping$")) returnValue = "pong";
      else if (query.matches("^info/uptime$")) returnValue = Interval.span(ManagementFactory.getRuntimeMXBean().getUptime());
      else if (query.matches("^info/security.policy$")) returnValue = "grant { permission java.security.AllPermission; };";
      else if (query.matches("^log4j.properties$")) returnValue =
          "log4j.rootLogger=INFO, console\n" + "log4j.logger.us.lump=INFO\n" + "log4j.logger.envelope=INFO\n"
              + "log4j.logger.org.hibernate=INFO\n" + "log4j.appender.console=org.apache.log4j.ConsoleAppender\n"
              + "log4j.appender.console.layout=org.apache.log4j.PatternLayout\n"
              + "log4j.appender.console.layout.ConversionPattern=%d [%t] %p %c{2} %m%n\n"
              + "log4j.appender.console.Target=System.err\n";

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
