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
 * @version $Id: ClassServer.java,v 1.4 2008/07/06 04:14:24 troy Exp $
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
      "us/lump/envelope/Server/class"
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
   * Returns an array of bytes containing the bytecodes for the class
   * represented by the argument <b>path</b>. The <b>path</b> is a dot separated
   * class abbr with the ".class" extension removed.
   *
   * @param path the path of the object
   *
   * @return the bytecodes for the class
   *
   * @throws ClassNotFoundException if the class corresponding to <b>path</b>
   *                                could not be loaded.
   * @throws java.io.IOException    if there are IO problems.
   */
  public byte[] getBytes(String path)
      throws IOException, ClassNotFoundException {

    InputStream clin = ClassLoader.getSystemResourceAsStream(path);

    if (clin == null) {
      clin = Thread.currentThread()
          .getContextClassLoader().getResourceAsStream(path);
    }

    if (clin == null) {
      logger.error("Does not exist: " + path);
      throw new ClassNotFoundException("File does not exist: " + path);
    }

    DataInputStream in = new DataInputStream(clin);

    int bytesRead;
    byte[] content = new byte[0];
    byte[] buffer = new byte[1024];
    while ((bytesRead = in.read(buffer, 0, buffer.length)) != -1) {
      byte[] newContent = new byte[content.length + bytesRead];
      System.arraycopy(content, 0, newContent, 0, content.length);
      System.arraycopy(buffer, 0, newContent, content.length, bytesRead);
      content = newContent;
    }

    return content;
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

        Matcher infoMatcher = Pattern.compile("^info/(.*)$").matcher(path);
        if (infoMatcher.matches() && infoMatcher.group(1) != null) {
          String query = infoMatcher.group(1);
          String returnValue = "";
          if (query.matches("^ping$")) returnValue = "pong";
          else if (query.matches("^port/rmi$"))
            returnValue = Server.getConfig(Server.PROPERTY_RMI_PORT);
          else if (query.matches("^uptime$"))
            returnValue = Server.uptime();

          out.writeBytes("HTTP/1.0 200 OK\r\n");
          out.writeBytes("Content-Length: " + returnValue.length() + "\r\n");
          out.writeBytes("Content-Type: text/plain\r\n\r\n");
          out.writeBytes(returnValue);
          logger.info("returned " + returnValue + " for " + path + " from "
                      + socket.getInetAddress().getCanonicalHostName());
        }
        else {
          byte[] bytecode = getBytes(path);
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
          out.writeBytes("Content-Type: application/java\r\n\r\n");
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

  /** Create a new thread to listen. */
  private void newListener() {
    (new Thread(this)).start();
  }
}
