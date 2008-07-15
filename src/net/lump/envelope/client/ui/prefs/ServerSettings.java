package us.lump.envelope.client.ui.prefs;

import us.lump.envelope.client.ui.defs.Strings;
import static us.lump.envelope.client.ui.prefs.ServerSettings.fields.*;

import java.io.*;
import java.net.*;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClassLoader;
import java.text.MessageFormat;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ServerSettings {
  private static ServerSettings singleton;
  Preferences prefs = Preferences.userNodeForPackage(this.getClass());

  private static ValidCache classServerValidated = new ValidCache();
  private static ValidCache rmiServerValidated = new ValidCache();

  public static final String CONTROLLER = "Controller";
  public static final String DEFAULT_CLASS_PORT = "7041";
  public static final String CODEBASE = "java.rmi.server.codebase";
  public static final String RMINODE = "java.rmi.server.rminode";
  public static final String PING = "/ping";
  public static final String RMI_PORT = "/port/rmi";

  enum fields {
    host_name,
    rmi_port,
    class_port
  }

  private ServerSettings() { }

  public static ServerSettings getInstance() {
    if (singleton == null) singleton = new ServerSettings();
    return singleton;
  }

  public String getHostName() {
    return prefs.get(host_name.name(), "localhost");
  }

  public void setHostName(final String hostName) {
    // class server test
    Matcher hostParser = Pattern.compile("^(.*?)(?:\\:(\\d+))?$")
        .matcher(hostName);
    if (hostParser.matches() && hostParser.group(1) != null) {
      prefs.put(host_name.name(), hostParser.group(1));
      //setClassPort handles a null group 2 gracefully
      setClassPort(hostParser.group(2));
    }
  }

  private String infoQuery(String query) throws IOException {
    URL url = new URL(this.getCodeBase().toString() + "info" + query);
    Socket socket = new Socket();
    socket.connect(new InetSocketAddress(url.getHost(), url.getPort()), 1000);
    BufferedReader r = new BufferedReader(
        new InputStreamReader(socket.getInputStream()));
    PrintWriter p = new PrintWriter(socket.getOutputStream(), true);
    p.write("GET info" + query + "\r\n\r\n");
    p.flush();

    boolean inHeader = true;
    String output = "";
    String line;
    while ((line = r.readLine()) != null) {
      if (line.matches("^\\s*$")) {
        inHeader = false;
        continue;
      }
      if (!inHeader) output += line;
    }
    socket.close();
    return output;
  }

  public String getRmiPort() throws IOException {
    String rmiPort = prefs.get(rmi_port.name(), null);
    if (rmiPort == null) {
      rmiPort = infoQuery(RMI_PORT);
      prefs.put(rmi_port.name(), rmiPort);
    }
    return rmiPort;
  }

  public void setRmiPort() throws IOException {
    prefs.put(rmi_port.name(), infoQuery(RMI_PORT));
  }

  public String getClassPort() {
    return prefs.get(class_port.name(), DEFAULT_CLASS_PORT);
  }

  public void setClassPort(String classPort) {
    if (classPort == null) classPort = DEFAULT_CLASS_PORT;
    prefs.put(class_port.name(), classPort);
  }

  public URL getCodeBase() throws MalformedURLException {
    URL url = new URL("http://" + getHostName() + ":" + getClassPort() + "/");
    if (System.getProperties().get(CODEBASE) == null
        || !System.getProperties().get(CODEBASE).equals(url.toString()))
      System.getProperties().put(CODEBASE, url.toString());
    return url;
  }

  public String rmiNode() throws IOException {
    String url = "rmi://" + getHostName() + ":" + getRmiPort() + "/";
    if (System.getProperties().get(RMINODE) == null
        || !System.getProperties().get(RMINODE).equals(url))
      System.getProperties().put(RMINODE, url);
    return url;
  }

  public String rmiController() throws IOException {
    return rmiNode() + CONTROLLER;
  }

  public String testClassServer() {
    String message = Strings.get("ok");
    if (classServerValidated.isValid()) return message;

    try {
      InetAddress ia = InetAddress.getByName(getHostName());

      if (!ia.isReachable(2000))
        message = MessageFormat.format(
            Strings.get("error.server_not_reachable"), this.getHostName());
      else {
        if (infoQuery(PING).matches("^pong")) {
          setRmiPort();
          classServerValidated.setValid(true);
        } else
          message = MessageFormat.format(
              Strings.get("error.verify_class_server"), this.getHostName());
      }
    }
    catch (FileNotFoundException fnfe) {
      message = MessageFormat.format(
          Strings.get("error.verify_class_server"), this.getHostName());
    }
    catch (UnknownHostException uhe) {
      message = MessageFormat.format(
          Strings.get("error.unknown_host"), this.getHostName());
    }
    catch (ConnectException ce) {
      message = MessageFormat.format(
          Strings.get("error.could_not_connect_on_port"),
          this.getHostName(), this.getClassPort(), ce.getMessage());
    } catch (Exception e) {
      message = e.getClass().getSimpleName() + ": " + e.getMessage();
    }
    return message;
  }

  public String testRmiServer() {
    String message = Strings.get("ok");
    if (rmiServerValidated.isValid()) return message;
    String url = "";
    try {
      url = rmiController();
      Thread.currentThread().setContextClassLoader(
          RMIClassLoader.getClassLoader(getCodeBase().toString()));
      Naming.lookup(url);
    } catch (MalformedURLException e) {
      message = MessageFormat.format(Strings.get("error.bad_url"), url);
    } catch (NotBoundException e) {
      message = MessageFormat.format(Strings.get("error.bad_rmi_name"),
                                     CONTROLLER);
    } catch (RemoteException e) {
      message = MessageFormat.format(Strings.get("error.remote_exception"),
                                     e.getMessage());
    } catch (Exception e) {
      message = e.getClass().getSimpleName() + ": " + e.getMessage();
    }

    if (message.equals(Strings.get("ok"))) rmiServerValidated.setValid(true);
    return message;
  }

  public void resetCache() {
    classServerValidated.setValid(false);
    rmiServerValidated.setValid(false);
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
