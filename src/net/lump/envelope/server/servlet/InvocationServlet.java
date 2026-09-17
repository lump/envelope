package net.lump.envelope.server.servlet;

import net.lump.envelope.server.Controller;
import net.lump.envelope.server.dao.DAO;
import net.lump.envelope.server.dao.Security;
import net.lump.envelope.server.dao.Sessions;
import net.lump.envelope.server.servlet.beans.ServerPrefs;
import net.lump.envelope.shared.command.Command;
import net.lump.lib.util.Base64;
import net.lump.lib.util.Encryption;
import org.apache.log4j.Logger;

import javax.crypto.*;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.MalformedURLException;
import java.rmi.RemoteException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
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
 * @version $Id: InvocationServlet.java,v 1.7 2010/09/20 23:18:23 troy Exp $
 */
public class InvocationServlet extends HttpServlet {

  private static final int READLIMIT = 104857600;
  private static final Logger logger = Logger.getLogger(InvocationServlet.class);

  public InvocationServlet() {
    super();
  }

  public void init() {
    try {
      DAO.initialize(ServerPrefs.getInstance().getProps(DAO.class));
    } catch (MalformedURLException m) {
      logger.error(m);
    } catch (IOException io) {
      logger.error(io);
    }
  }

  @SuppressWarnings({"unchecked", "ConstantConditions"}) @Override
  protected void doPost(HttpServletRequest rq, HttpServletResponse rp) throws ServletException, IOException {

    Matcher m =
        Pattern.compile("^(multipart/form-data);\\s+boundary=(.*?)$", Pattern.CASE_INSENSITIVE).matcher(rq.getContentType());

    // Hoisted out of the block below so the finally at the bottom can still reach
    // them: a Security opened for the key part carries an open transaction that
    // only Controller commits, and every path that never reaches Controller has to
    // put it back itself.
    Security security = null;
    boolean dispatched = false;

    try {

      if (m.matches() && m.group(2) != null) {

        // Authentication rides in headers rather than in the payload, so it can
        // be settled before anything reaches readObject.  The command name comes
        // along too, so we know whether a session is required without having to
        // look inside the body first.
        String cmdHeader = rq.getHeader(Command.H_COMMAND);
        String sessionId = rq.getHeader(Command.H_SESSION);
        String stampHeader = rq.getHeader(Command.H_STAMP);
        String signature = rq.getHeader(Command.H_SIGNATURE);

        boolean sessionRequired;
        try {
          sessionRequired = Command.Name.valueOf(cmdHeader).isSessionRequired();
        } catch (Exception e) {
          rp.sendError(HttpServletResponse.SC_BAD_REQUEST, "missing or unknown " + Command.H_COMMAND + " header");
          return;
        }
        String authenticatedUser = null;

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
          Pattern pKV = Pattern.compile("^(.*?):\\s+(.*?)$");
          for (String line : header.split(nlp.pattern())) {
            Matcher mKV = pKV.matcher(line);
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
            // Raw bytes, not readUpTo.  That went through
            // new String(buffer)/String.getBytes(), so a part body without a
            // Content-Length was decoded and re-encoded through the platform
            // charset: the ac ed serialization magic came out as ef bf bd and
            // readObject answered "invalid stream header: EFBFBDEF".  It also
            // returned a CHARACTER offset that was then fed to bis.skip() as a
            // BYTE count, desynchronizing the stream for any multi-byte content.
            // The stock client takes this path on every encrypted request -- it
            // sends a Content-Length for the command part but not for the key
            // part -- and survived only because base64 is ASCII.
            content = readBodyUpTo(bis, (fix + boundary).getBytes(StandardCharsets.US_ASCII));
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

            // Check the signature before deserializing.  It covers a digest of
            // exactly these bytes, so an unauthenticated caller never gets to
            // hand an object graph to readObject.
            if (sessionRequired) {
              Sessions.Session session =
                  Security.validateSession(sessionId, stampHeader, signature, Encryption.digest(content));
              if (session == null) {
                rp.sendError(HttpServletResponse.SC_UNAUTHORIZED, "invalid session");
                return;
              }
              authenticatedUser = session.getUsername();
            }

            String serType = "application/java-serialized-object";
            String contentType = headers.get("content-type");
            if (contentType != null && contentType.equals(serType)) {
              Object o = new ObjectInputStream(new ByteArrayInputStream(content)).readObject();
              if (o instanceof Command) {
                command = (Command)o;
                // The header is what decided whether we demanded a signature, so
                // it has to agree with the payload -- otherwise a caller could
                // claim "ping" and send "save".
                if (!command.getName().name().equals(cmdHeader)) {
                  rp.sendError(HttpServletResponse.SC_BAD_REQUEST, "command header does not match payload");
                  return;
                }
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
          else {
            rp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "argument " + name + " not recognized");
            return;
          }
        }

        //todo: make this more intelligent
        // sendError only suspends the output buffer -- it does not return from the
        // servlet -- so without this the request went on to dispatch and commit while
        // the client was told it had been refused, and the signature was already spent.
        if (rq.getHeader("accept") == null || !rq.getHeader("accept").contains("application/java-serialized-object")) {
          rp.sendError(HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "accept of " + rq.getHeader("accept") + " is not supported");
          return;
        }

        // A multipart with no command part at all used to reach getSeqId() below and
        // die there with a bare NullPointerException.
        if (command == null) {
          rp.sendError(HttpServletResponse.SC_BAD_REQUEST, "no command part in the multipart body");
          return;
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
            // "identity" means "send it uncompressed", and "*" means "anything will
            // do" -- both are satisfied by encoding nothing.  They used to fall
            // through to the 406 below, which only ever reached a client because
            // that sendError had no return after it and the response went out
            // anyway; with the return in place, refusing them would turn a legal
            // request into a hard failure.
            if (enc.equalsIgnoreCase("identity") || enc.equals("*")) {
              found = true;
              break;
            }
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
              break;
            }
          }
          if (!found) {
            rp.sendError(HttpServletResponse.SC_NOT_ACCEPTABLE, "only gzip or deflate encoding is allowed");
            return;
          }
        }

        Controller c = new Controller(rp, os);
        dispatched = true;
        try {
          c.invoke(command, authenticatedUser);
        } catch (Exception e) {
          if (!(e instanceof RemoteException)) {
            Throwable tmp = e.getCause();
            while (tmp.getCause() != null) tmp = tmp.getCause();
            e = new RemoteException("Unexpected exception", tmp);
          }
          rp.addHeader("Single-Object", Boolean.TRUE.toString());
          rp.addIntHeader("Object-Count", 1);
          new ObjectOutputStream(os).writeObject(e);
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
    } finally {
      // Unwrapping the session key constructs a Security, and DAO's instance
      // initializer begins a transaction.  On the success path Controller adopts
      // that same thread-bound transaction and commits it.  Every other path --
      // a junk key part landing in one of the catches above, a missing command
      // part, a rejected Accept header -- used to leave it open and bound to this
      // Tomcat worker, still holding its row locks, and because DAO skips begin()
      // when a transaction is already active the next request on this thread
      // inherited it instead of starting its own.
      if (security != null && !dispatched) {
        try {
          if (security.isActive() && !security.wasRolledBack()) security.getTransaction().rollback();
        } catch (Exception e) {
          logger.error("could not roll back an undispatched request's transaction", e);
        } finally {
          try {
            security.close();
          } catch (Exception e) {
            logger.error("could not close an undispatched request's session", e);
          }
        }
      }
    }
  }

  @SuppressWarnings({"ConstantConditions", "ResultOfMethodCallIgnored"})
  public CharSequence readUpTo(BufferedInputStream bis, boolean inclusive, Pattern search) throws IOException {
    bis.mark(READLIMIT);

    StringBuilder s = new StringBuilder();
    Matcher matcher = null;
    boolean found = false;

    // A match can straddle the join between what we already had and what just
    // arrived, so each rescan has to reach back at least as far as the longest
    // possible match.  These patterns are the multipart boundary plus a couple of
    // dashes and a newline, built as a literal, so the pattern's own length bounds
    // that.
    final int overlap = search.pattern().length() + 2;
    int scanned = 0;

    while (!found) {
      // make a buffer the size of available
      byte[] buffer = new byte[1024];

      // fill the buffer with available
      int read = bis.read(buffer);

      // if we read -1 bytes, we've reached EOF.
      if (read == -1) throw new EOFException("EOF Reached");

      s.append(new String(buffer, 0, read));

      // Scan only the new window.  This used to re-run the regex over the whole
      // accumulated buffer on every 1 KB chunk, which is quadratic, and READLIMIT
      // is 100 MB: an unauthenticated 4 MiB part took 4.6s of CPU against 17ms for
      // the same part sent with a Content-Length, doubling ~4x per doubling of
      // size.  One request was a remote CPU-exhaustion lever.
      matcher = search.matcher(s);
      found = matcher.find(Math.max(0, scanned - overlap));
      scanned = s.length();
    }

    if (matcher == null) return null;
    bis.reset();

    // this skip should always skip, because we've already buffered the amount we want to skip to.
    int end = inclusive ? matcher.end() : matcher.start();
    bis.skip(end);
    bis.mark(READLIMIT);

    return s.subSequence(0, end);
  }

  /**
   * Read a part's body as raw bytes, up to but not including the next boundary.
   *
   * <p>The stream is left positioned at the boundary, exactly as
   * {@link #readUpTo} leaves it. Nothing here goes through a {@code String}: a
   * part body is arbitrary bytes, and decoding it to characters and back mangles
   * every byte the platform charset cannot round-trip.
   *
   * @param bis    the buffered request body, positioned at the start of the body
   * @param needle the boundary bytes to stop before
   *
   * @return the body bytes
   *
   * @throws IOException  on io error, or if the body outgrows {@link #READLIMIT}
   * @throws EOFException if the stream ends before the boundary is seen
   */
  private byte[] readBodyUpTo(BufferedInputStream bis, byte[] needle) throws IOException {
    bis.mark(READLIMIT);

    byte[] hay = new byte[8192];
    int len = 0;
    int scanned = 0;
    int at = -1;

    while (at < 0) {
      if (len == hay.length) {
        // Doubling, so the copying is amortized linear.  The gzip/deflate decode
        // path above still regrows by one chunk at a time, which is the quadratic
        // shape this deliberately avoids.
        if (hay.length >= READLIMIT)
          throw new IOException("part body exceeded " + READLIMIT + " bytes");
        hay = Arrays.copyOf(hay, Math.min(hay.length * 2, READLIMIT));
      }

      int read = bis.read(hay, len, hay.length - len);
      if (read == -1) throw new EOFException("EOF Reached");
      len += read;

      // Only look at the new window, reaching back far enough to catch a boundary
      // split across two reads.
      at = indexOf(hay, len, needle, Math.max(0, scanned - needle.length + 1));
      scanned = len;
    }

    bis.reset();
    // A BYTE count this time: readUpTo handed bis.skip() a character offset.
    bis.skip(at);
    bis.mark(READLIMIT);

    return Arrays.copyOf(hay, at);
  }

  /**
   * First index at or after {@code from} where {@code needle} occurs in the first
   * {@code len} bytes of {@code hay}, or -1.
   */
  private static int indexOf(byte[] hay, int len, byte[] needle, int from) {
    final int last = len - needle.length;
    outer:
    for (int i = Math.max(0, from); i <= last; i++) {
      for (int j = 0; j < needle.length; j++)
        if (hay[i + j] != needle[j]) continue outer;
      return i;
    }
    return -1;
  }

}
