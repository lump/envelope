package us.lump.envelope.server.http;

import org.apache.log4j.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.AccessControlException;
import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.GZIPInputStream;

import us.lump.envelope.Server;

/**
 * ClassServer is an abstract class that provides the basic functionality of a
 * mini-webserver, specialized to load class files only. A ClassServer must be
 * extended and the concrete subclass should define the <b>getBytes</b> method
 * which is responsible for retrieving the bytecodes for a class.<p>
 * <p/>
 * The ClassServer creates a thread that listens on a socket and accepts  HTTP
 * GET requests. The HTTP response contains the bytecodes for the class that
 * requested in the GET header. <p>
 * <p/>
 * For loading remote classes, an RMI application can use a concrete subclass of
 * this server in place of an HTTP server. <p>
 *
 * @author Troy Bowman
 * @version $Id: ClassServer.java,v 1.5 2008/07/11 06:15:09 troy Exp $
 */
public class ClassServer implements Runnable {
  private static final Logger logger = Logger.getLogger(ClassServer.class);

  private ServerSocket server = null;

  // client is denied from downloading classes from the following paths
  private static final String[] denyList = new String[]{
      "us/lump/envelope/server/dao",
      "us/lump/envelope/server/http",
      "us/lump/envelope/server/log",
      "us/lump/envelope/server/rmi/Controlled.class",
      "us/lump/envelope/Server.class"
  };

  /**
   * Constructs a ClassServer that listens on <b>port</b> and obtains a class's
   * bytecodes using the method <b>getBytes</b>.
   *
   * @param port the port number
   *
   * @throws IOException if the ClassServer could not listen on <b>port</b>.
   */
  public ClassServer(int port) throws IOException {
    server = new ServerSocket(port);
    newListener();
  }




  /**
   * The "listen" thread that accepts a connection to the server, parses the
   * header to obtain the class file abbr and sends back the bytecodes for the
   * class (or error if the class is not found or the response was malformed).
   */
  public void run() {
    Socket socket;
    String encoding;
    String[] encodings = {"raw"};

    // accept a connection
    try {
      socket = server.accept();
    }
    catch (IOException e) {
      logger.warn(MessageFormat.format("Class Server died: {0}",
                                       e.getMessage()));
      e.printStackTrace();
      return;
    }

    // create a new thread to accept the next connection
    newListener();

    try {
      DataOutputStream out =
          new DataOutputStream(socket.getOutputStream());
      String path = null;

      try {
        // get path to class file from header
        BufferedReader in = new BufferedReader(
            new InputStreamReader(socket.getInputStream()));
        String line = in.readLine();

        // parse get
        Matcher m = Pattern.compile(
            "^GET\\s*/?(.*?)(?:\\s+HTTP/\\d+(?:\\.\\d+)*)?\\r?\\n?$")
            .matcher(line);
        if (m.matches() && m.group(1) != null) path = m.group(1);
        if (path == null || path.equals(""))
          throw new Exception("Malformed Header");

        // skip other headers;
        do {
          line = in.readLine();
          Matcher headerMatcher =
              Pattern.compile("^Accept-Encoding:\\s+(.*?)$",
                              Pattern.CASE_INSENSITIVE).matcher(line);
          if (headerMatcher.matches() && headerMatcher.group(1) != null) {
            encodings = headerMatcher.group(1).split("\\s*,\\s*");
          }
        } while (line.matches("^\\S+.*\\r?\\n?$"));

        for (String deniedPath : denyList) {
          if (path.startsWith(deniedPath)) {
            throw new AccessControlException("Access Denied");
          }
        }


        Matcher infoMatcher = Pattern.compile("^(info/.*|log4j.properties)$").matcher(path);
        if (infoMatcher.matches() && infoMatcher.group(1) != null) {
          String query = infoMatcher.group(1);
          String returnValue = "";
          if (query.matches("^info/ping$")) returnValue = "pong";
          else if (query.matches("^info/port/rmi$"))
            returnValue = Server.getConfig(Server.PROPERTY_RMI_PORT);
          else if (query.matches("^info/uptime$"))
            returnValue = Server.uptime();
          else if (query.matches("^info/security.policy$"))
            returnValue = "grant { permission java.security.AllPermission; };";
          else if (query.matches("^log4j.properties$"))
            returnValue = getClientLog4j();
          else throw new ClassNotFoundException();

          out.writeBytes("HTTP/1.0 200 OK\r\n");
          out.writeBytes("Content-Length: " + returnValue.length() + "\r\n");
          out.writeBytes("Content-Type: text/plain\r\n\r\n");
          out.writeBytes(returnValue);
          logger.info("returned " + returnValue + " for " + path + " from "
                      + socket.getInetAddress().getCanonicalHostName());
        }
        else {

          InputStream is;
          String contentType;

          is = ClassLoader.getSystemResourceAsStream(path);
          if (is == null) {
            is = Thread.currentThread()
                .getContextClassLoader().getResourceAsStream(path);
          }

          if (is == null) {
            logger.error("Does not exist: " + path);
            throw new ClassNotFoundException("File does not exist: " + path);
          }

          DataInputStream dis = new DataInputStream(is);
          int bytesRead;
          byte[] bytecode = new byte[0];
          byte[] buffer = new byte[1024];
          while ((bytesRead = dis.read(buffer, 0, buffer.length)) != -1) {
            byte[] newContent = new byte[bytecode.length + bytesRead];
            System.arraycopy(bytecode, 0, newContent, 0, bytecode.length);
            System.arraycopy(buffer, 0, newContent, bytecode.length, bytesRead);
            bytecode = newContent;
          }

          int magic = (bytecode[0] & 0xff) << 24 | (bytecode[1] & 0xff) << 16
                      | (bytecode[2] & 0xff) << 8 | (bytecode[3] & 0xff);
          if (magic == 0xcafebabe) // 0xcafebabe = java classes
            contentType = "application/java";
          else if ((magic) == 0xcafed00d) // 0xcafed00d = pk200
            contentType = "application/x-java-pack200";
          else if ((magic >> 16) == ('P' << 8 | 'K')) // PKzip files have a ascii magic of PK :)
            if (Pattern.compile("^.*\\.jar$", Pattern.CASE_INSENSITIVE).matcher(path).matches())
              contentType = "application/java-archive";
            else contentType = "application/zip";
          else if ((magic >> 16) == GZIPInputStream.GZIP_MAGIC)
            contentType = "application/x-gzip";
          else contentType = "application/binary";

          byte[] compressedBytecode = bytecode;
          encoding = "raw";
          for (String format : encodings) {
            if (format.equals("raw")) {
              break;
            } else if (format.equals("gzip")) {
              encoding = "gzip";
              ByteArrayOutputStream baos = new ByteArrayOutputStream();
              GZIPOutputStream gzip = new GZIPOutputStream(baos);
              gzip.write(bytecode);
              gzip.finish();
              compressedBytecode = baos.toByteArray();
              break;
            } else if (format.equals("deflate")) {
              encoding = "deflate";
              ByteArrayOutputStream baos = new ByteArrayOutputStream();
              DeflaterOutputStream zip = new DeflaterOutputStream(baos);
              zip.write(bytecode);
              zip.finish();
              compressedBytecode = baos.toByteArray();
              break;
            }
          }

          if (!encoding.equals("raw"))
            logger.info(MessageFormat.format(
                "{0} compressed using {1} from {2} to {3}",
                path, encoding, bytecode.length, compressedBytecode.length));

          logger.info(MessageFormat.format(
              "to {0}, {1}B for {2}",
              socket.getInetAddress().getCanonicalHostName(),
              compressedBytecode.length, path));

          out.writeBytes("HTTP/1.1 200 OK\r\n");
          out.writeBytes("Content-Length: "
                         + compressedBytecode
              .length
                         + "\r\n");
          if (!encoding.equals("raw"))
            out.writeBytes("Content-Encoding: " + encoding + "\r\n");
          out.writeBytes("Content-Type: " + contentType + "\r\n\r\n");
          out.write(compressedBytecode);
          out.flush();
        }
      }
      catch (AccessControlException a) {
        logger.warn(MessageFormat.format(
            "to {0} status 403 denied for {1}",
            socket.getInetAddress().getCanonicalHostName(), path));
        String denied = a.getMessage();
        out.writeBytes("HTTP/1.0 403 Forbidden\r\n");
        out.writeBytes("Content-Length: " + denied.length() + "\r\n");
        out.writeBytes("Content-Type: text/plain\r\n\r\n");
        out.writeBytes(denied);
      }
      catch (ClassNotFoundException e) {
        logger.warn(MessageFormat.format(
            "to {0} status 404 not found for {1}",
            socket.getInetAddress().getCanonicalHostName(), path));
        out.writeBytes("HTTP/1.1 404 " + e.getMessage() + "\r\n");
        out.writeBytes("Content-Type: text/html\r\n\r\n");
        out.writeBytes(
            "<html><head><title>HTTP 404</title></head><body>HTTP 404<br>"
            + e.getMessage()
            + "</body></html>\r\n\r\n");
        out.flush();
      }
      catch (Exception e) {
        logger.warn(MessageFormat.format(
            "to {0} status 400 bad request for {1}",
            socket.getInetAddress().getCanonicalHostName(), path));
        out.writeBytes("HTTP/1.1 400 " + e.getMessage() + "\r\n");
        out.writeBytes("Content-Type: text/html\r\n\r\n");
        out.writeBytes(
            "<html><head><title>HTTP 400 Bad Request</title></head><body>HTTP 400<br>Bad Request</body></html>\r\n\r\n");
        out.flush();
      }
    }
    catch (IOException ex) {
      // eat exception (could log error to log file, but
      // write out to stdout for now).
      logger.error(MessageFormat.format(
          "error writing response: {0}", ex.getMessage()));
      ex.printStackTrace();
    }
    finally {
      try {
        socket.close();
      }
      catch (IOException e) {
        logger.error(e);
      }
    }
  }

  private String getClientLog4j() {
    return "log4j.rootLogger=INFO, console\n"
           + "log4j.logger.us.lump=DEBUG\n"
           + "log4j.logger.envelope=DEBUG\n"
           + "log4j.logger.org.hibernate=INFO\n";
//           + "log4j.appender.console=org.apache.log4j.ConsoleAppender\n"
//           + "log4j.appender.console.layout=org.apache.log4j.PatternLayout\n"
//           + "log4j.appender.console.layout.ConversionPattern=%d [%t] %p %c{2} %m%n\n"
//           + "log4j.appender.console.Target=System.err\n";
  }

  /** Create a new thread to listen. */
  private void newListener() {
    (new Thread(this)).start();
  }
}
