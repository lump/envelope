package us.lump.envelope.server.servlet;

import us.lump.envelope.server.Controller;
import us.lump.envelope.server.dao.DAO;
import us.lump.envelope.server.dao.Security;
import us.lump.envelope.server.servlet.beans.ServerPrefs;
import us.lump.envelope.shared.command.Command;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.*;

/**
 * The default servlet.
 *
 * @author troy
 * @version $Id: InvocationServlet.java,v 1.3 2009/07/13 17:21:44 troy Exp $
 */
public class InvocationServlet extends HttpServlet {

  private static final int READLIMIT = 104857600;

  public InvocationServlet() {
    super();
  }

  public void init() {
    try {
      DAO.initialize(ServerPrefs.getInstance().getProps(DAO.class));
    } catch (MalformedURLException ignore) {
    } catch (IOException ignore) {}
  }

  @SuppressWarnings({"unchecked", "ConstantConditions"}) @Override
  protected void doPost(HttpServletRequest rq, HttpServletResponse rp) throws ServletException, IOException {

    Matcher m =
        Pattern.compile("^(multipart/form-data);\\s+boundary=(.*?)$", Pattern.CASE_INSENSITIVE).matcher(rq.getContentType());
    try {

      if (m.matches() && m.group(2) != null) {
        String fix = "--";
        String boundary = m.group(2);

        Pattern nlp = Pattern.compile("(?:\\r?\\n)");
        Pattern dnlp = Pattern.compile(nlp.pattern() + "{2}");
        Pattern boundarySearch = Pattern.compile(String.format("%s%s((?:%s)?%s?)", fix, boundary, fix, nlp.pattern()));
        Pattern boundaryStart = Pattern.compile(String.format("%s%s", fix, boundary));
        Pattern boundaryEnd = Pattern.compile(String.format(nlp.pattern() + "?%s%s%s%s?", fix, boundary, fix, nlp.pattern()));

        BufferedInputStream bis = new BufferedInputStream(rq.getInputStream());
        bis.mark(READLIMIT);

        SecretKey sessionKey = null;
        Security security = null;
        Command command = null;
        String encoding;
        String encryption;

        while (true) {
          String name = null;

          String foundBoundary = readUpTo(bis, true, boundarySearch).toString();
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
          Matcher dMatcher =
              Pattern.compile("^.*form-data\\s*;\\s*name\\s*=\\s*\"?(.*?)\"\\s*.*$", Pattern.CASE_INSENSITIVE).matcher(disposition);
          if (disposition != null && dMatcher.matches() && dMatcher.group(1) != null) {
            name = dMatcher.group(1);
          }
          else {
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
              read = bis.read(buffer, 0, contentLength - bytesRead);
              if (read < 0) break;
              System.arraycopy(buffer, 0, content, bytesRead, read);
              bytesRead = bytesRead + read;
            }
            bis.mark(READLIMIT);
          }
          else {
            content = readUpTo(bis, false, boundaryStart).toString().getBytes();
          }

          // handle an incoming key
          if (name.equalsIgnoreCase("key")) {
            String transferEncoding = headers.get("content-transfer-encoding");
            byte[] input;
            if (transferEncoding != null) {
              if (transferEncoding.equals("base64")) input = Base64.base64ToByteArray(new String(content).replaceAll("\\s", ""));
              else {
                rp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "only base64 ecnoding is allowed for " + name);
                return;
              }
            }
            else {
              input = content;
            }
            security = security == null ? new Security() : security;
            sessionKey = (SecretKey)security.unwrapSessionKey(input);
          }
          else if (name.equalsIgnoreCase("command")) {

            // handle an encrypted body
            encryption = headers.get("content-encryption");
            if (encryption != null && encryption.length() > 0) {
              final Cipher c = Cipher.getInstance(encryption);
              c.init(Cipher.DECRYPT_MODE, sessionKey);
              content = c.doFinal(content);
            }

            encoding = headers.get("content-encoding");
            if (encoding != null && encoding.length() > 0) {
              InputStream is = new ByteArrayInputStream(content);
              if (encoding.equals("gzip")) is = new GZIPInputStream(is);
              else if (encoding.equals("deflate")) is = new InflaterInputStream(is);
              else {
                rp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "only gzip or deflate encoding is allowed");
                return;
              }

              byte[] out = new byte[0];
              byte[] buffer = new byte[4096];
              int read;
              while ((read = is.read(buffer)) > 0) {
                byte[] newOut = new byte[out.length + read];
                System.arraycopy(out, 0, newOut, 0, out.length);
                System.arraycopy(buffer, 0, newOut, out.length, read);
                out = newOut;
              }
              content = out;
            }

            String serType = "application/java-serialized-object";
            String contentType = headers.get("content-type");
            if (contentType != null && contentType.equals(serType)) {
              Object o = new ObjectInputStream(new ByteArrayInputStream(content)).readObject();
              if (o instanceof Command) {
                command = (Command)o;
              }
              else {
                rp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    name + " was not a " + Command.class.getCanonicalName());
              }
            }
            else {
              rp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "field " + name + " can only accept " + serType);
              return;
            }
          }
          else rp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "argument " + name + " not recognized");
        }

        //todo: make this more intelligent
        if (rq.getHeader("accept") == null || !rq.getHeader("accept").contains("application/java-serialized-object")) {
          rp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "accept of " + rq.getHeader("accept") + " is not supported");
        }

        // prepare the output stream depending on the request params
        OutputStream os = rp.getOutputStream();
        rp.addHeader("Content-Type", "application/java-serialized-object");
        rp.addHeader("Command-Sequence-Id", String.valueOf(command.getSeqId()));
        rp.addHeader("Connection", "keep-alive");
        rp.addHeader("Keep-Alive", "timeout=86400, max=16");

        String acceptEncryption = rq.getHeader("accept-encryption");
        if (acceptEncryption != null && acceptEncryption.length() > 0 && sessionKey != null) {
          acceptEncryption = acceptEncryption.replaceAll("^\\s*(.*?)\\s*$", "$1");
          rp.addHeader("Content-Encryption", acceptEncryption);
          Cipher cout = Cipher.getInstance(acceptEncryption);
          cout.init(Cipher.ENCRYPT_MODE, sessionKey);
          os = new CipherOutputStream(os, cout);
        }

        // parse accept encodings
        String acceptEncoding = rq.getHeader("accept-encoding");
        if (acceptEncoding != null) {
          String[] acceptEncodings;
          if (!acceptEncoding.contains(",")) acceptEncodings = new String[]{acceptEncoding};
          else acceptEncodings = acceptEncoding.replaceAll("^\\s*(.*?)\\s*$", "$1").split("\\s*,\\s*");
          boolean found = false;
          for (String enc : acceptEncodings) {
            if (enc.equalsIgnoreCase("gzip")) {
              rp.addHeader("Content-Encoding", "gzip");
              os = new GZIPOutputStream(os);
              found = true;
              break;
            }
            if (enc.equalsIgnoreCase("deflate")) {
              rp.addHeader("Content-Encoding", "deflate");
              os = new DeflaterOutputStream(os, new Deflater(Deflater.BEST_COMPRESSION));
              found = true;
            }
          }
          if (!found) rp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "only gzip or deflate encoding is allowed");
        }

        Controller c = new Controller(rp, os);
        try {
           c.invoke(command);
        } catch (RemoteException r) {
          rp.addHeader("Single-Object", Boolean.TRUE.toString());
          rp.addIntHeader("Object-Count", 1);
          new ObjectOutputStream(os).writeObject(r);
          os.flush();
        }

        os.flush();
        os.close();

//        rp.addIntHeader("Content-Length", baos.size());
//        OutputStream servletOs = rp.getOutputStream();

        // finally write it to the servlet's outputstream
//        servletOs.write(baos.toByteArray());
//        servletOs.flush();

        // this simulates slow network
//        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
//        byte[] buffer = new byte[128];
//        int read;
//        while ((read = bais.read(buffer)) > 0) {
//          servletOs.write(buffer, 0, read);
//          try { Thread.sleep(2); } catch (InterruptedException ignore) { }
//          servletOs.flush();
//        }
      }
      else {
        rp.setStatus(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE);
      }
    } catch (ClassNotFoundException e) {
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
  public CharSequence readUpTo(BufferedInputStream bis, boolean inclusive, Pattern search) throws IOException {
    bis.mark(READLIMIT);

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

      s.append(new String(buffer, 0, read));

      matcher = search.matcher(s);
      found = matcher.find();
      if (found) break;
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
