package us.lump.envelope.client.ui.prefs;

import us.lump.envelope.client.ui.defs.Strings;
import static us.lump.envelope.client.ui.prefs.ServerSettings.Field.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.text.MessageFormat;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerSettings {
  private static ServerSettings singleton;
  Preferences prefs = Preferences.userNodeForPackage(this.getClass());

  private static ValidCache socketServerValidated = new ValidCache();

//  public static final String CONTROLLER = "Controller";
  public static final String DEFAULT_CLASS_PORT = "7041";
  public static final String PING = "/ping";

  enum Field {
    host,
    port,
    context,
    encrypt,
    compress
  }

  private ServerSettings() { }

  public static ServerSettings getInstance() {
    if (singleton == null) singleton = new ServerSettings();
    return singleton;
  }

  public String getHostName() {
    return prefs.get(host.name(), "localhost");
  }

  public void setHostName(final String hostName) {
    // socket server test
    Matcher hostParser = Pattern.compile("^(.*?)(?:\\:(\\d+))?$")
        .matcher(hostName);
    if (hostParser.matches() && hostParser.group(1) != null) {
      prefs.put(host.name(), hostParser.group(1));
      //setPort handles a null group 2 gracefully
      setPort(hostParser.group(2));
    }
  }

  public String getContext() {
    return prefs.get(context.name(), "/envelope");
  }

  public void setContext(final String context) {
    prefs.put(Field.context.name(), context);
  }

  private String infoQuery(String query) throws IOException {
    URL url = new URL(this.getCodeBase().toString() + "info" + query);
    URLConnection c = url.openConnection();
    c.setDoInput(true);

    BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()));
    
    String output = "";
    String line;
    while ((line = r.readLine()) != null) {
      output += line;
    }
    r.close();
    return output;
  }

  public String getPort() {
    return prefs.get(port.name(), DEFAULT_CLASS_PORT);
  }

  public void setPort(String port) {
    if (port == null) port = DEFAULT_CLASS_PORT;
    prefs.put(Field.port.name(), port);
  }

  public boolean getEncrypt() {
    return prefs.getBoolean(encrypt.name(), false);
  }
  public void setEncrypt(boolean flag) {
    prefs.putBoolean(encrypt.name(), flag);
  }

  public boolean getCompress() {
    return prefs.getBoolean(compress.name(), false);
  }

  public void setCompress(boolean flag) {
    prefs.putBoolean(compress.name(), flag);
  }
  
  public URL getCodeBase() throws MalformedURLException {
    return new URL("http://" + getHostName() + ":" + getPort() + getContext() + "/");
  }

  public String testSocketServer() {
    String message = Strings.get("ok");
    if (socketServerValidated.isValid()) return message;

    try {
      InetAddress ia = InetAddress.getByName(getHostName());

      if (!ia.isReachable(2000))
        message = MessageFormat.format(
            Strings.get("error.server.not.reachable"), this.getHostName());
      else {
        if (infoQuery(PING).matches("^pong")) {
          socketServerValidated.setValid(true);
        } else
          message = MessageFormat.format(
              Strings.get("error.verify.server"), this.getHostName());
      }
    }
    catch (FileNotFoundException fnfe) {
      message = MessageFormat.format(
          Strings.get("error.verify.server"), this.getHostName());
    }
    catch (UnknownHostException uhe) {
      message = MessageFormat.format(
          Strings.get("error.unknown.host"), this.getHostName());
    }
    catch (ConnectException ce) {
      message = MessageFormat.format(
          Strings.get("error.could.not.connect.on.port"),
          this.getHostName(), this.getPort(), ce.getMessage());
    } catch (Exception e) {
      message = e.getClass().getSimpleName() + ": " + e.getMessage();
    }
    return message;
  }

  public void resetCache() {
    socketServerValidated.setValid(false);
  }

  // small object to maintain a cache of server validation
  private static class ValidCache {
    // 10 seconds
    private static final int CACHE = 3000;
    private boolean valid = false;
    private long stamp = 0;

    public ValidCache setValid(boolean valid) {
      this.valid = valid;
      this.stamp = System.currentTimeMillis();
      return this;
    }

    public boolean isValid() {
      return (valid && this.stamp > (System.currentTimeMillis() - CACHE));
    }
  }
}
