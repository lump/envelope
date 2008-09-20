package us.lump.envelope.server.http;

import org.apache.log4j.Logger;
import us.lump.envelope.Command;
import us.lump.envelope.Server;
import us.lump.envelope.server.XferFlags;
import static us.lump.envelope.server.XferFlags.Flag.*;
import us.lump.envelope.server.dao.Security;
import us.lump.envelope.server.rmi.Controlled;
import us.lump.lib.util.Base64;
import us.lump.lib.util.CipherOutputStream;
import us.lump.lib.util.Compression;
import us.lump.lib.util.Encryption;

import javax.crypto.SecretKey;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.AccessControlException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

  private static final int MAX_READ = 5242880;

  // client is denied from downloading classes from the following paths
  private static final String[] denyList = new String[]{
      "us/lump/envelope/server/dao",
      "us/lump/envelope/server/http",
      "us/lump/envelope/server/log",
      "us/lump/envelope/server/rmi/Controlled.class",
      "us/lump/envelope/Server.class"
  };

  private static final Logger logger = Logger.getLogger(ClassServer.class);


  public String readTo(BufferedInputStream bis, String search)
      throws IOException {

    bis.mark(MAX_READ);
    byte[] buffer = new byte[1024];

    StringBuffer s = new StringBuffer();
    int read = 0;
    do {
      read += bis.read(buffer, 0, 1024);
      if (read > 0)
        s.append(new String(buffer).substring(0, read > 1024 ? 1024 : read));
    } while (read > 0 && (s.indexOf(search) == -1
                          || s.length() > MAX_READ - 1024));
    if (read == -1) throw new EOFException();
    bis.reset();

    return s.substring(0, s.indexOf(search) + search.length());
  }

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
//      socket.setSoTimeout(15000);
      socket.setSoTimeout(0);
      socket.setKeepAlive(true);

      String path = null;

      try {
        // get path to class file from header
        BufferedInputStream is =
            new BufferedInputStream(socket.getInputStream());
        String header = readTo(is, "\r\n\r\n");

        String line = "";
        String httpcommand = "GET";

        // parse command
        Matcher m = Pattern.compile(
            "^((GET|HEAD|COMMAND)\\s*/?(.*?)(?:\\s+(?:HT|JO)TP/\\d+(?:\\.\\d+)*)?\\r\\n)")
            .matcher(header.subSequence(0, header.indexOf("\r\n") + 2));
        if (m.matches()) {
          if (m.group(1) != null) line = m.group(1);
          if (m.group(2) != null) httpcommand = m.group(2);
          if (m.group(3) != null) path = m.group(3);
        }

        if (httpcommand.equals("COMMAND") && path.equals("invoke")) {
          String magic =
              new String(new byte[]{(byte)0xac, (byte)0xed, 0x00, 0x05});

          boolean loop = true;
          while (loop) {

            try {
              header = readTo(is, "JOTP/1.0\r\n\r\n");// + magic);
              is.skip(header.length());// - magic.length());
              XferFlags outFlags = new XferFlags();
              XferFlags inFlags = new XferFlags((byte)is.read());
              logger.info("request flags [" + inFlags + "] " );
              is.mark(MAX_READ);

              InputStream optionIs = is;

              SecretKey sessionKey = null;
              if (inFlags.has(F_ENCRYPT)) {
                Security security = new Security();
                // read the session key, ecnrypted for me.
                ObjectInputStream ois = new ObjectInputStream(is);
                String b64encryptedKey = (String)(ois).readObject();
                is.mark(MAX_READ);

                sessionKey = (SecretKey)security.unwrapSessionKey(
                    Base64.base64ToByteArray(b64encryptedKey));

                optionIs = new ByteArrayInputStream(
                    Encryption.decodeSym(sessionKey,
                                         (String)(ois).readObject()));
                outFlags.add(F_ENCRYPT);
              }
              if (inFlags.has(F_COMPRESS)) {
                optionIs = new GZIPInputStream(optionIs);
                outFlags.add(F_COMPRESS);
              }

              Controlled c = new Controlled(null);
              ObjectInputStream ois = new ObjectInputStream(optionIs);
              Object retval = null;

              // execute commands and catalog their listiness
              if (inFlags.has(F_OBJECT)) {
                Command command = (Command)ois.readObject();
                is.mark(MAX_READ);
                retval = c.invoke(command);
                logger.info(command);
                if (retval instanceof List) outFlags.add(F_LIST);
                else outFlags.add(F_OBJECT);
              }
              else if (inFlags.has(F_LIST)) {
                Integer size = (Integer)ois.readObject();
                is.mark(MAX_READ);
                ArrayList<Object> list = new ArrayList<Object>(size);
                boolean listOfLists = false;
                for (int x=0; x< size; x++) {
                  Command command = (Command)ois.readObject();
                  is.mark(MAX_READ);
                  Object out = c.invoke(command);
                  if (out instanceof List) listOfLists = true;
                  list.add(out);
                  logger.info("["+(x+1)+" of "+size+"] "+command);
                }
                retval = list;
                if (listOfLists) outFlags.add(F_LISTS);
                else outFlags.add(F_LIST);
              }

              // create a oos from a baos to hold our stream of objects
              // because gzip outputstream will close the socket when we close.
              ByteArrayOutputStream baos = new ByteArrayOutputStream();
              ObjectOutputStream oos = new ObjectOutputStream(baos);
              logger.info("response flags [" + outFlags + "] " );
              // just one object
              if (outFlags.has(F_OBJECT)) {
                oos.writeObject(retval);
                oos.flush();
              }

              // list which is one level deep
              if (outFlags.has(F_LIST)) {
                oos.writeObject(new Integer(((List)retval).size()));
                oos.flush();
                for (Object entry : (List)retval) {
                  oos.writeObject(entry);
                  oos.flush();
                }
              }

              // list which is possibly two levels deep
              if (outFlags.has(F_LISTS)) {
                oos.writeObject(new Integer(((List)retval).size()));
                for (Object entry : (List)retval) {
                  if (entry instanceof List) {
                    oos.writeObject(new Integer(((List)entry).size()));
                    oos.flush();
                    for (Object sub : (List)entry) {
                      oos.writeObject(sub);
                      oos.flush();
                    }
                  }
                  else {
                    // -1397705797 is secret code for a single object
                    // since size should never be negative, we're safe.
                    // (1397705797 is "SOLE" in integer form)
                    oos.writeObject(new Integer(-1397705797));
                    oos.writeObject(entry);
                  }
                }
              }
              oos.flush();
              oos.close();

              // if we're asked to compress the output stream...
              if (outFlags.has(F_COMPRESS)) baos = Compression.compress(baos);

              OutputStream os = socket.getOutputStream();
              os.write(outFlags.getByte());

              // if we're asked to encrypt the output stream...
              if (outFlags.has(F_ENCRYPT)) {
                CipherOutputStream cos = Encryption.encodeSym(sessionKey, os);
                baos.writeTo(cos);

                cos.flush();
                // if we're not on the padding boundary, pad some zeros
//                if (baos.size() % cos.getBlockSize() != 0)
//                  cos.write(new byte[baos.size() % cos.getBlockSize()]);
//                cos.flush();
              }
              else {
                baos.writeTo(os);
              }
              os.flush();
            }
            catch (EOFException e) {
              loop = false;
            }
            catch (SocketException e) {
              loop = false;
            }
//            catch (IOException e) {
//              loop = false;
//            }
            catch (Exception e) {
              socket.getOutputStream().write(0);
              ObjectOutputStream oos =
                  new ObjectOutputStream(socket.getOutputStream());
              oos.writeObject(e);
              oos.flush();
            }
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
          jnlp = jnlp.replaceAll("\\{icon\\}", "lib/envelope.png");
          jnlp = jnlp.replaceAll("\\{main-class\\}", "Envelope");

//          for (String file : new String[]{"slim-client.jar.pack.gz",
//                                          "slim-client.jar",
//                                          "client.jar.pack.gz",
//                                          "client.jar"}) {
//            if (ClassLoader.getSystemResource("lib/"+file) != null) {
//              jnlp =
//                  jnlp.replaceAll("\\{jars\\}",
//                                  "<jar href=\"lib/"+file+"\">\n");
//              break;
//            }
//          }
//
          jnlp =
              jnlp.replaceAll("\\{jars\\}",
                              "<jar href=\"lib/client.jar.pack.gz\">\n"
              );
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
          if (!httpcommand.equals("HEAD")) out.writeBytes(returnValue);
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
              httpcommand,
              path));

          out.writeBytes("HTTP/1.1 200 OK\r\n");
          out.writeBytes(
              "Content-Length: " + compressedBytecode.length + "\r\n");
          if (!encoding.equals("raw"))
            out.writeBytes("Content-Encoding: " + encoding + "\r\n");
          out.writeBytes("Content-Type: " + contentType + "\r\n\r\n");
          if (!httpcommand.equals("HEAD")) out.write(compressedBytecode);
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
      catch (SocketTimeoutException e) {
        logger.info(MessageFormat.format("socket to {0} timed out",
                                         socket.getInetAddress().getCanonicalHostName()));
      }
      catch (SocketException e) {
        logger.info(MessageFormat.format("socket to {0} {1}",
                                         socket.getInetAddress().getCanonicalHostName(),
                                         e.getMessage()));
      }
      catch (Exception e) {
        logger.warn(MessageFormat.format(
            "to {0} status 400 bad request for {1}, {2}",
            socket.getInetAddress().getCanonicalHostName(),
            path,
            e.getMessage()));

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
