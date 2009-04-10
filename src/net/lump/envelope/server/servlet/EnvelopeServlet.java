package us.lump.envelope.server.servlet;

import org.apache.log4j.PropertyConfigurator;
import us.lump.envelope.command.Command;
import us.lump.envelope.server.Controller;
import us.lump.envelope.server.dao.DAO;
import us.lump.envelope.server.dao.Security;
import us.lump.envelope.server.log.Log4j;
import us.lump.envelope.server.servlet.beans.ServerPrefs;
import us.lump.lib.util.Base64;

import javax.crypto.*;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * The default servlet.
 *
 * @author troy
 * @version $Id: EnvelopeServlet.java,v 1.2 2009/04/10 22:49:28 troy Exp $
 */
public class EnvelopeServlet extends HttpServlet {

  private static final int READLIMIT = 104857600;

  public EnvelopeServlet() {
    super();
  }

  public void init() {

    try {
      PropertyConfigurator.configure(ServerPrefs.getInstance().getProps(Log4j.class));
      DAO.initialize(ServerPrefs.getInstance().getProps(DAO.class));
    } catch (MalformedURLException ignore) {
    } catch (IOException ignore) {}
  }

  @SuppressWarnings({"unchecked"})
  @Override 
  protected void doPost(HttpServletRequest rq, HttpServletResponse rp)
      throws ServletException, IOException {

    Matcher m = Pattern.compile("^(multipart/form-data);\\s+boundary=(.*?)$", Pattern.CASE_INSENSITIVE)
        .matcher(rq.getContentType());

    try {

      if (m.matches() && m.group(2) != null) {
        String fix = "--";
        String boundary = m.group(2);

        Pattern nlp = Pattern.compile("(?:\\r?\\n)");
        Pattern dnlp = Pattern.compile(nlp.pattern() + "{2}");
        Pattern boundaryStart = Pattern.compile(String.format("%s%s", fix, boundary));
        Pattern boundaryEnd = Pattern.compile(String.format(nlp.pattern() + "%s%s%s", fix, boundary, fix));

        BufferedInputStream bis = new BufferedInputStream(rq.getInputStream());
        bis.mark(READLIMIT);

        SecretKey sessionKey = null;
        Security security = null;

        while (true) {
          String name = null;

          String foundBoundary = readUpTo(bis, true, boundaryStart, boundaryEnd).toString();
          if (foundBoundary.matches("^.*" + boundaryEnd.pattern() + "$")) break;

          // we found a boundary with content, lets read it.
          HashMap<String, String> headers = new HashMap<String, String>();
          String header = readUpTo(bis, true, dnlp).toString();
          for (String line : header.split(nlp.pattern())) {
            Matcher mKV = Pattern.compile("^(.*?):\\s+(.*?)$").matcher(line);
            if (mKV.matches() && mKV.group(1) != null && mKV.group(2) != null)
              headers.put(mKV.group(1).toLowerCase(), mKV.group(2));
          }

          String disposition = headers.get("content-disposition");
          Matcher dMatcher = Pattern.compile("^.*form-data\\s*;\\s*name\\s*=\\s*\"?(.*?)\"\\s*.*$",
              Pattern.CASE_INSENSITIVE).matcher(disposition);
          if (disposition != null && dMatcher.matches() && dMatcher.group(1) != null) {
            name = dMatcher.group(1);
          } else {
            rp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "bad content disposition " + name);
            return;
          }

          byte[] content;
          int contentLength = headers.get("content-length") == null ? -1 : new Integer(headers.get("content-length"));
          if (contentLength > 0) {
            content = new byte[contentLength];
            byte[] buffer = new byte[contentLength];
            int bytesRead = 0;
            int read;
            while (bytesRead < contentLength) {
              read = bis.read(buffer);
              if (read < 0) break;
              System.arraycopy(buffer, 0, content, 0, read);
              bytesRead = bytesRead + read;
            }
            bis.mark(READLIMIT);
          } else {
            content = readUpTo(bis, false, boundaryStart).toString().getBytes();
          }

          // handle an incoming key
          if (name.equalsIgnoreCase("key")) {
            String encoding = headers.get("content-transfer-encoding");
            byte[] input;
            if (encoding != null) {
              if (encoding.equals("base64")) input = Base64.base64ToByteArray(new String(content));
              else {
                rp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "only base64 ecnoding is allowed for " + name);
                return;
              }
            } else {
              input = content;
            }
            security = security == null ? new Security() : security;
            sessionKey = (SecretKey)security.unwrapSessionKey(input);

          } else if (name.equalsIgnoreCase("command")) {

            // handle an encrypted body
            String encryptionAlgorithm = headers.get("content-encryption");
            if (encryptionAlgorithm != null && encryptionAlgorithm.length() > 0) {
              final Cipher c = Cipher.getInstance(encryptionAlgorithm);
              c.init(Cipher.DECRYPT_MODE, sessionKey);
              content = c.doFinal(content);
            }

            String encoding = headers.get("content-encoding");
            if (encoding != null && encoding.length() > 0) {
              if (encoding.equals("gzip")) {
                GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(content));
                byte[] out = new byte[0];
                byte[] buffer = new byte[1024];
                int read;
                while ((read = gzis.read(buffer)) > 0) {
                  byte[] newOut = new byte[out.length + read];
                  System.arraycopy(buffer, 0, newOut, out.length, read);
                  out = newOut;
                }
                content = out;
              } else {
                rp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "only gzip encoding is allowed");
                return;
              }
            }

            String serType = "application/java-serialized-object";
            String contentType = headers.get("content-type");
            if (contentType != null && contentType.equals(serType)) {
              Object o = new ObjectInputStream(new ByteArrayInputStream(content)).readObject();
              if (o instanceof Command) {

                Command command = (Command)o;
                Controller c = new Controller();
                Serializable retval;
                try {
                   retval = c.invoke(command);
                }
                catch (RemoteException r) {
                  retval = r;
                }

                List<Serializable> rl;
                if (retval instanceof List) {
                  rp.addHeader("Single-Object", Boolean.FALSE.toString());
                  rl = (List<Serializable>)retval;
                } else {
                  rp.addHeader("Single-Object", Boolean.TRUE.toString());
                  rl = new Vector<Serializable>(1);
                  rl.add(retval);
                }

                rp.addHeader("Content-Type", "application/java-serialized-object");
                rp.addIntHeader("Object-Count", rl.size());
                rp.addHeader("Command-Sequence-Id", String.valueOf(command.getSeqId()));

                // we have to cache the output to get content-length
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                OutputStream os = baos;

                if (encoding != null && encoding.equals("gzip")) {
                  rp.addHeader("Content-Encoding", "gzip");
                  os = new GZIPOutputStream(os);
                }

                if (encryptionAlgorithm != null && encryptionAlgorithm.length() > 0 && sessionKey != null) {
                  rp.addHeader("Content-Encryption", encryptionAlgorithm);
                  Cipher cout = Cipher.getInstance(encryptionAlgorithm);
                  cout.init(Cipher.ENCRYPT_MODE, sessionKey);
                  os = new CipherOutputStream(os, cout);
                }

                ObjectOutputStream oos = new ObjectOutputStream(os);
                for (Serializable s : rl) {
                  oos.writeObject(s);
                }
                oos.flush();
                oos.close();

                rp.addIntHeader("Content-Length", baos.size());

                OutputStream servletOs = rp.getOutputStream();

                ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
                // finally write it to the servlet's outputstream
                servletOs.write(baos.toByteArray());
                servletOs.flush();

                // this simulates slow network
//                byte[] buffer = new byte[128];
//                int read;
//                while ((read = bais.read(buffer)) > 0) {
//                  servletOs.write(buffer, 0, read);
//                  try {
//                    Thread.sleep(50);
//                  } catch (InterruptedException ignore) {
//                  }
//                  servletOs.flush();
//                }
                break;

              } else {
                rp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    name + " was not a " + Command.class.getCanonicalName());
              }

            } else {
              rp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "field " + name + " can only accept " + serType);
              return;
            }
          }
          else rp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "argument " + name + " not recognized");
        }
      } else {
        rp.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
      }
    }
    catch (ClassNotFoundException e) {
      rp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (InvalidKeyException e) {
      rp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (NoSuchAlgorithmException e) {
      rp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (NoSuchPaddingException e) {
      rp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (IllegalBlockSizeException e) {
      rp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    } catch (BadPaddingException e) {
      rp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
    }
  }

  @SuppressWarnings({"ConstantConditions", "ResultOfMethodCallIgnored"})
  public CharSequence readUpTo(BufferedInputStream bis, boolean inclusive, Pattern... search) throws IOException {
    bis.mark(READLIMIT);

    if (search.length == 0) return null;
    StringBuffer s = new StringBuffer();
    Matcher matcher = null;
    boolean found = false;

    while (!found) {
      // make a buffer the size of available
      byte[] buffer = new byte[1024];

      // fill the buffer with available
      int read = bis.read(buffer);

      // if we read -1 bytes, we've reached EOF.
      if (read == -1) throw new EOFException("EOF Reached");

      s.append(new String(buffer).substring(0, read));

      for (Pattern p : search) {
        matcher = p.matcher(s);
        found = matcher.find();
        if (found) break;
      }
    }

    if (matcher == null) return null;

    bis.reset();
    // this skip should always skip, because we've already buffered the amount we want to skip to.
    int end = inclusive ? matcher.end() : matcher.start();
    bis.skip(end);
    bis.mark(READLIMIT);

    return s.subSequence(0, end);
  }

}