package us.lump.envelope.server.http;

import org.apache.log4j.Logger;
import us.lump.envelope.Command;
import us.lump.envelope.Server;
import us.lump.envelope.exception.DataException;
import us.lump.envelope.server.rmi.Controlled;

import java.io.*;
import java.net.Socket;
import java.security.AccessControlException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Created by IntelliJ IDEA. User: troy Date: Jul 13, 2008 Time: 3:19:44 PM To
 * change this template use File | Settings | File Templates.
 */
public class HttpRequestHandler implements RequestHandler {

  // client is denied from downloading classes from the following paths
  private static final String[] denyList = new String[]{
      "us/lump/envelope/server/dao",
      "us/lump/envelope/server/http",
      "us/lump/envelope/server/log",
      "us/lump/envelope/server/rmi/Controlled.class",
      "us/lump/envelope/Server.class"
  };

  private static final Logger logger = Logger.getLogger(ClassServer.class);

  /**
   * The "listen" thread that accepts a connection to the server, parses the
   * header to obtain the class file abbr and sends back the bytecodes for the
   * class (or error if the class is not found or the response was malformed).
   */
  public void handleRequest(Socket socket) {
    String encoding;
    String[] encodings = {"raw"};
    HashMap<String, String> headers = new HashMap<String, String>();

    try {
      socket.setSoTimeout(900000);
      socket.setKeepAlive(true);

      String path = null;

      try {
        // get path to class file from header
        BufferedInputStream is =
            new BufferedInputStream(socket.getInputStream());
        int max = 65536;
        is.mark(max);

        String header = "";

        int read = 0;
        do {
          byte[] buffer = new byte[1024];
          read += is.read(buffer, 0, 1024);
          header += new String(buffer).substring(0, read);
        } while (header.indexOf("\r\n\r\n") == -1 || read > max - 1024);

        String line = header.substring(0, header.indexOf("\r\n") + 2);

        String command = "GET";

        // parse command
        Matcher m = Pattern.compile(
            "^((GET|HEAD|COMMAND)\\s*/?(.*?)(?:\\s+HTTP/\\d+(?:\\.\\d+)*)?\\r?\\n?)$")
            .matcher(line);
        if (m.matches()) {
          if (m.group(1) != null) line = m.group(1);
          if (m.group(2) != null) command = m.group(2);
          if (m.group(3) != null) path = m.group(3);
        }

        if (command.equals("COMMAND") && path.equals("invoke")) {
          String magic =
              new String(new byte[]{(byte)0xac, (byte)0xed, 0x00, 0x05});

          // go until we find the object header
          int objectInputStart = -1;
          do {
            byte[] buffer = new byte[1024];
            read += is.read(buffer, 0, 1024);
            header += new String(buffer).substring(0, read);
            objectInputStart = header.indexOf(magic);
          } while (objectInputStart == -1 || read == 0);

          ObjectOutputStream oos =
              new ObjectOutputStream(socket.getOutputStream());

          if (objectInputStart == -1) {
            //noinspection ThrowableInstanceNeverThrown
            oos.writeObject(
                new DataException(DataException.Type.Invalid_Command));
          }

          is.reset();
          is.skip(objectInputStart);

          try {
            ObjectInputStream ois = new ObjectInputStream(is);
            Object o = ois.readObject();

            Command[] commands = null;

            if (o instanceof Command[]) commands = (Command[])o;
            if (o instanceof Command) commands = new Command[]{(Command)o};

            if (commands != null) {
              Controlled c = new Controlled(null);
              Object retval = c.invoke(commands);
//              if (retval instanceof List) {
//                retval = new BackgroundList((List)retval);
//              }
              oos.writeObject(retval);
            } else {
              //noinspection ThrowableInstanceNeverThrown
              oos.writeObject(
                  new DataException(DataException.Type.Invalid_Command));
            }
          }
          catch (Exception e) {
            oos.writeObject(e);
          }
          return;
        }

        // okay, we can do a dataOutputStream now
        DataOutputStream out =
            new DataOutputStream(socket.getOutputStream());

        if (path == null || path.equals("")) {
          byte[] bjnlp =
              slurpInputSteam(this.getClass().getResourceAsStream("jnlp.xml"));
          String jnlp = new String(bjnlp);
          jnlp = jnlp.replaceAll("\\{host\\}",
                                 socket.getLocalAddress().getCanonicalHostName());
          jnlp = jnlp.replaceAll("\\{port\\}",
                                 String.valueOf(socket.getLocalPort()));
          jnlp = jnlp.replaceAll("\\{title\\}", "Envelope Java Web Start");
          jnlp = jnlp.replaceAll("\\{vendor\\}", "Lump Software");
          jnlp = jnlp.replaceAll("\\{description\\}", "An Envelope Budget");
          jnlp = jnlp.replaceAll("\\{icon\\}", "lib/franklin.png");
          jnlp = jnlp.replaceAll("\\{main-class\\}", "Envelope");
          jnlp =
              jnlp.replaceAll("\\{jars\\}",
                              "<jar href=\"lib/client.jar.pack.gz\">\n");

          out.writeBytes("HTTP/1.0 200 OK\r\n");
          out.writeBytes("Content-Type: application/x-java-jnlp-file\r\n");
          out.writeBytes("Content-Disposition: name=envelope.jnlp\r\n");
          out.writeBytes("Content-Length: " + jnlp.length() + "\r\n\r\n");
          out.writeBytes(jnlp);
          logger.info("returned " + jnlp + "for " + path + " from "
                      + socket.getInetAddress().getCanonicalHostName());
          return;
        }

        is.reset();
        is.skip(line.length());
        BufferedReader in = new BufferedReader(
            new InputStreamReader(is));

        // skip other headers;
        do {
          line = in.readLine();
          Matcher headerMatcher =
              Pattern.compile("^([A-Za-z0-9\\-]+):\\s+(.*?)$").matcher(line);

          if (headerMatcher.matches() && headerMatcher.group(1) != null
              && headerMatcher.group(2) != null) {
            headers.put(headerMatcher.group(1).toLowerCase(),
                        headerMatcher.group(2));
          }
        } while (line.matches("^\\S+.*\\r?\\n?$"));

        if (headers.containsKey("accept-encoding"))
          encodings = headers.get("accept-encoding").split("\\s*,\\s*");

        for (String deniedPath : denyList) {
          if (path.startsWith(deniedPath)) {
            throw new AccessControlException("Access Denied");
          }
        }


        Matcher infoMatcher =
            Pattern.compile("^(info/.*|log4j.properties|)$").matcher(path);
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
          if (!command.equals("HEAD")) out.writeBytes(returnValue);
          logger.info("returned " + returnValue + " for " + path + " from "
                      + socket.getInetAddress().getCanonicalHostName());
        } else {

          InputStream cis;
          String contentType;

          cis = ClassLoader.getSystemResourceAsStream(path);
          if (cis == null) {
            cis = Thread.currentThread()
                .getContextClassLoader().getResourceAsStream(path);
          }
          if (cis == null) {
            this.getClass().getClassLoader().getResource(path);
          }

          if (cis == null) {
            logger.error("Does not exist: " + path);
            throw new ClassNotFoundException("File does not exist: " + path);
          }

          encoding = "raw";
          byte[] bytecode = slurpInputSteam(cis);

          int magic = (bytecode[0] & 0xff) << 24 | (bytecode[1] & 0xff) << 16
                      | (bytecode[2] & 0xff) << 8 | (bytecode[3] & 0xff);
          if (magic == 0xcafebabe) // 0xcafebabe = java classes
            contentType = "application/java";
          else if ((magic) == 0xcafed00d) {// 0xcafed00d = pk200
            contentType = "application/x-java-pack200";
          } else if ((magic >> 16) == ('P' << 8
                                       | 'K')) // PKzip files have a ascii magic of PK :)
            if (Pattern.compile("^.*\\.jar$", Pattern.CASE_INSENSITIVE)
                .matcher(path)
                .matches())
              contentType = "application/java-archive";
            else contentType = "application/zip";

          else if (((magic >> 24) | ((magic >> 8) & 0xff00))
                   == GZIPInputStream.GZIP_MAGIC) {
            if (Pattern.compile("^.*\\.jar.pack.gz$", Pattern.CASE_INSENSITIVE)
                .matcher(path).matches()) {
              contentType = "application/java-archive";
              encoding = "pack200-gzip";
            } else contentType = "application/x-gzip";
          } else if (magic == 0x89504e47) //png = 0x89 + "PNG"
            contentType = "image/png";
          else contentType = "application/binary";

          byte[] compressedBytecode = bytecode;
          for (String format : encodings) {
            if (format.equals("raw") || encoding.equals("pack200-gzip")) {
              break;
            } else if (format.equals("gzip")
                       && ((magic >> 16) != GZIPInputStream.GZIP_MAGIC)) {
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
              "to {0}, {1}B {2} {3}",
              socket.getInetAddress().getCanonicalHostName(),
              compressedBytecode.length,
              command,
              path));

          out.writeBytes("HTTP/1.1 200 OK\r\n");
          out.writeBytes(
              "Content-Length: " + compressedBytecode.length + "\r\n");
          if (!encoding.equals("raw"))
            out.writeBytes("Content-Encoding: " + encoding + "\r\n");
          out.writeBytes("Content-Type: " + contentType + "\r\n\r\n");
          if (!command.equals("HEAD")) out.write(compressedBytecode);
          out.flush();
          out.flush();
          out.close();
          socket.close();
        }
      }
      catch (AccessControlException a) {
        logger.warn(MessageFormat.format(
            "to {0} status 403 denied for {1}",
            socket.getInetAddress().getCanonicalHostName(), path));
        writeError(new DataOutputStream(socket.getOutputStream()),
                   "HTTP/1.0 403 Forbidden", headers.get("user-agent"),
                   a.getMessage());
      }
      catch (ClassNotFoundException e) {
        logger.warn(MessageFormat.format(
            "to {0} status 404 not found for {1}",
            socket.getInetAddress().getCanonicalHostName(), path));

        writeError(new DataOutputStream(socket.getOutputStream()),
                   "HTTP/1.0 404 " + e.getMessage(),
                   headers.get("user-agent"),
                   e.getMessage());
      }
      catch (Exception e) {
        logger.warn(MessageFormat.format(
            "to {0} status 400 bad request for {1}",
            socket.getInetAddress().getCanonicalHostName(), path));

        writeError(new DataOutputStream(socket.getOutputStream()),
                   "HTTP/1.0 400 " + e.getMessage(),
                   headers.get("user-agent"),
                   e.getMessage());
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

  private void writeError(DataOutputStream out,
                          String header, String userAgent, String message)
      throws IOException {
    out.writeBytes(header + "\r\n");
    if (userAgent != null && userAgent.matches("^Java/\\d+.*$")) {
      out.writeBytes("\r\n\r\n");
    } else {
      out.writeBytes("Content-Length: " + message.length() + "\r\n");
      out.writeBytes("Content-Type: text/html\r\n\r\n");
      out.writeBytes(
          "<html><head><title>"
          + header
          + "</title></head><body>"
          + header
          + "<br>"
          + message
          + "</body></html>\r\n\r\n");
    }
    out.flush();
  }

  private String getClientLog4j() {
    return "log4j.rootLogger=INFO, console\n"
           + "log4j.logger.us.lump=INFO\n"
           + "log4j.logger.envelope=INFO\n"
           + "log4j.logger.org.hibernate=INFO\n"
           + "log4j.appender.console=org.apache.log4j.ConsoleAppender\n"
           + "log4j.appender.console.layout=org.apache.log4j.PatternLayout\n"
           + "log4j.appender.console.layout.ConversionPattern=%d [%t] %p %c{2} %m%n\n"
           + "log4j.appender.console.Target=System.err\n";
  }

  private byte[] slurpInputSteam(InputStream is) throws IOException {
    DataInputStream dis = new DataInputStream(is);
    int bytesRead;
    byte[] content = new byte[0];
    byte[] buffer = new byte[1024];
    while ((bytesRead = dis.read(buffer, 0, buffer.length)) != -1) {
      byte[] newContent = new byte[content.length + bytesRead];
      System.arraycopy(content, 0, newContent, 0, content.length);
      System.arraycopy(buffer, 0, newContent, content.length, bytesRead);
      content = newContent;
    }
    return content;
  }

}
