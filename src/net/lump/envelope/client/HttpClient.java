package us.lump.envelope.client;

import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.client.ui.prefs.ServerSettings;
import us.lump.envelope.shared.command.Command;
import us.lump.envelope.shared.command.OutputEvent;
import us.lump.envelope.shared.exception.AbortException;
import us.lump.lib.util.Base64;
import us.lump.lib.util.CipherOutputStream;
import us.lump.lib.util.Encryption;

import javax.crypto.*;
import java.io.*;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.net.URL;
import java.rmi.RemoteException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * A http client invoker.
 *
 * @author troy
 * @version $Id: HttpClient.java,v 1.13 2009/07/13 17:21:44 troy Exp $
 */
public class HttpClient {

  private static final int READLIMIT = 104857600;
  private static final ServerSettings serverSettings = ServerSettings.getInstance();

  public Serializable invoke(final Command command)
      throws IOException, NoSuchAlgorithmException, IllegalBlockSizeException, InvalidKeyException, NoSuchPaddingException,
      AbortException, ClassNotFoundException {

    boolean encryptable =
        serverSettings.getEncrypt() && (Command.Name.unEncryptables().and(command.getName().bit()).compareTo(BigInteger.ZERO) == 0);

    SecretKey sessionKey = null;
    ArrayList<Serializable> output = null;
    boolean single = false;

    // rfc 2388 multipart/form-data post
    final String prefix = "--";
    final String n = "\r\n";
    final String boundary = String.format("---------%s-%s-%s",
        command.getName().name(),
        String.valueOf(Math.random()).replaceAll("^0\\.", ""),
        String.valueOf(System.currentTimeMillis()));

    URL url = new URL(
        "http://" + serverSettings.getHostName() + ":" + serverSettings.getPort() + serverSettings.getContext() + "/invoke");

    if (System.getProperty("http.keepAlive") == null
        || !System.getProperty("http.keepAlive").equals("true")) System.setProperty("http.keepAlive", "true");
    HttpURLConnection connection = (HttpURLConnection)url.openConnection();
    connection.setDoOutput(true);
    connection.setDoInput(true);
    connection.setDefaultUseCaches(false);
    connection.setRequestMethod("POST");
    connection.addRequestProperty("Accept", "application/java-serialized-object");
    connection.addRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
    if (serverSettings.getCompress()) connection.addRequestProperty("Accept-Encoding", "gzip");
    if (serverSettings.getEncrypt()) connection.addRequestProperty("Accept-Encryption", Encryption.symAlg);
    connection.setReadTimeout(900000);
    connection.setConnectTimeout(30000);

    DataOutputStream out = new DataOutputStream(connection.getOutputStream());

    try {

      // if we're encrypting, generate and send a symkey
      if (encryptable) {
        sessionKey = Encryption.generateSymKey();

        // encrypt the key with server's public key for transfer and base64 it.
        String encodedKey =
            Base64.byteArrayToBase64(Encryption.wrapSecretKey(sessionKey, LoginSettings.getInstance().getServerKey()));

        // write the sym key
        out.writeBytes(prefix + boundary + n);
        out.writeBytes("Content-Transfer-Encoding: base64" + n);
        out.writeBytes("Content-Type: application/octet-stream" + n);
        out.writeBytes("Content-Disposition: form-data; name=\"key\"" + n + n);
        out.writeBytes(encodedKey + n);
      }

      // the boundary
      out.writeBytes(prefix + boundary + n);

      // preliminary bytearray of the object
      byte[] cmdBytes;

      {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(command);
        oos.close();
        cmdBytes = baos.toByteArray();
      }

      //rfc 2616 sec7 entity length
      out.writeBytes("Enity-Length: " + cmdBytes.length + n);

      // if we're compressing, set the compress header
      if (serverSettings.getCompress()) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

//        out.writeBytes("Content-Encoding: deflate" + n);
//        OutputStream gz = new DeflaterOutputStream(baos, new Deflater(Deflater.BEST_COMPRESSION));
        out.writeBytes("Content-Encoding: gzip" + n);
        OutputStream gz = new GZIPOutputStream(baos);
        gz.write(cmdBytes);
        gz.flush();
        gz.close();
        cmdBytes = baos.toByteArray();
      }

      // if this command is encryptable, set the e
      if (encryptable) {
        out.writeBytes("Content-Encryption: " + Encryption.symAlg + n);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CipherOutputStream cos = Encryption.encodeSym(sessionKey, baos);
        cos.write(cmdBytes);
        cos.close();
        cmdBytes = baos.toByteArray();
      }

      out.writeBytes("Content-Length: " + cmdBytes.length + n);
      out.writeBytes("Content-Type: application/java-serialized-object" + n);
      out.writeBytes("Content-Disposition: form-data; name=\"command\"" + n + n);
      out.write(cmdBytes);

      // write the finish boundary
      out.writeBytes(n + prefix + boundary + prefix + n);
      // signal end of parts
      out.flush();

      InputStream is = connection.getInputStream();

      // handle an encrypted body
      String encryptionAlgorithm = connection.getHeaderField("content-encryption");
      if (sessionKey != null && encryptionAlgorithm != null && encryptionAlgorithm.length() > 0) {
        final Cipher c = Cipher.getInstance(encryptionAlgorithm);
        c.init(Cipher.DECRYPT_MODE, sessionKey);
        is = new CipherInputStream(is, c);
      }

      String encoding = connection.getContentEncoding();
      if (encoding != null && encoding.length() > 0) {
        if (encoding.equals("gzip")) {
          is = new GZIPInputStream(is);
        }
        else if (encoding.equals("deflate")) {
          is = new InflaterInputStream(is);
        }
        else {
          throw new IllegalStateException("gzip encoding isn't supported");
        }
      }

      String objectCount = connection.getHeaderField("object-count");
      int count;
      if (objectCount != null && objectCount.matches("^\\d+$")) count = Integer.parseInt(objectCount);
      else throw new IllegalStateException("object-count is invalid");

      long seqId;
      String sequenceNumber = connection.getHeaderField("command-sequence-id");
      if (sequenceNumber != null && sequenceNumber.matches("^\\d+$")) seqId = Long.parseLong(sequenceNumber);
      else throw new IllegalStateException("sequence identifier is bad");

      single =
          connection.getHeaderField("single-object") != null && Boolean.parseBoolean(connection.getHeaderField("single-object"));
      final ObjectInputStream ois = new ObjectInputStream(is);
      assert seqId == command.getSeqId() : "sequence identifier didn't match";
      output = new ArrayList<Serializable>(count);
      for (int x = 0; x < count; x++) {
        Serializable s = (Serializable)ois.readObject();
        if (s instanceof RemoteException) throw (RemoteException)s;
        else {
          output.add(s);
          command.fireOutput(new OutputEvent(command, (long)count, (long)x, s));
          // allow the UI event thread to catch up
          // sleep 1 ms every 5 rows
          if (x % 5 == 0) try { Thread.sleep(3); } catch (InterruptedException ignore) { }

        }
      }
    } finally {
      out.close();
    }

    // return the output.
    if (output.size() == 0 && single) return null;
    else if (output.size() == 1 && single) return output.get(0);
    else return output;
  }

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

    //noinspection ConstantConditions
    if (matcher == null) return null;

    bis.reset();
    // this skip should always skip, because we've already buffered the amount we want to skip to.
    int end = inclusive ? matcher.end() : matcher.start();
    //noinspection ResultOfMethodCallIgnored
    bis.skip(end);
    bis.mark(READLIMIT);

    return s.subSequence(0, end);
  }
}
