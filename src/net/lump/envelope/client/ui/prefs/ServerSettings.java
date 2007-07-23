package us.lump.envelope.client.ui.prefs;

import sun.net.www.content.text.PlainTextInputStream;
import us.lump.envelope.client.ui.defs.Strings;
import static us.lump.envelope.client.ui.prefs.ServerSettings.fields.*;

import java.io.DataInputStream;
import java.io.FileNotFoundException;
import java.net.*;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.server.RMIClassLoader;
import java.text.MessageFormat;
import java.util.prefs.Preferences;

public class ServerSettings {
  private static ServerSettings singleton;
  Preferences prefs;

  enum fields {
    host_name,
    rmi_port,
    class_port
  }

  private ServerSettings() {
    prefs = Preferences.userNodeForPackage(this.getClass());
  }

  public static ServerSettings getInstance() {
    if (singleton == null) singleton = new ServerSettings();
    return singleton;
  }

  public String getHostName() {
    return prefs.get(host_name.name(), "localhost");
  }

  public void setHostName(final String hostName) {
    prefs.put(host_name.name(), hostName);
  }

  public String getRmiPort() {
    return prefs.get(rmi_port.name(), "7041");
  }

  public void setRmiPort(final String rmiPort) {
    prefs.put(rmi_port.name(), rmiPort);
  }

  public String getClassPort() {
    return prefs.get(class_port.name(), "7042");
  }

  public void setClassPort(final String classPort) {
    prefs.put(class_port.name(), classPort);
  }

  public URL getCodeBase() throws MalformedURLException {
    URL url = new URL("http://" + getHostName() + ":" + getClassPort() + "/");
    if (System.getProperties().get("java.rmi.server.codebase") == null
        || !System.getProperties().get("java.rmi.server.codebase").equals(url.toString()))
      System.getProperties().put("java.rmi.server.codebase", url.toString());
    return url;
  }

  public String rmiNode() {
    String url = "rmi://" + getHostName() + ":" + getRmiPort() + "/";
    if (System.getProperties().get("java.rmi.server.rminode") == null
        || !System.getProperties().get("java.rmi.server.rminode").equals(url))
      System.getProperties().put("java.rmi.server.rminode", url);
    return url;
  }

  public String testClassServer() {
    ServerSettings ss = getInstance();
    String message = Strings.get("ok");
    try {
      InetAddress ia = InetAddress.getByName(getHostName());

      if (!ia.isReachable(2000))
        message = MessageFormat.format(Strings.get("error.server_not_reachable"), ss.getHostName());
      else {

        URL url = new URL(ss.getCodeBase().toString() + "ping");
        URLConnection pingConnection = url.openConnection();

        pingConnection.connect();
        byte[] content = new byte[pingConnection.getContentLength()];
        DataInputStream out = new DataInputStream((PlainTextInputStream)pingConnection.getContent());
        out.readFully(content);
        String stringOut = new String(content);
        if (!stringOut.matches("^pong\\s*$"))
          message = MessageFormat.format(Strings.get("error.verify_class_server"), ss.getHostName());
      }
    }
    catch (FileNotFoundException fnfe) {
      message = MessageFormat.format(Strings.get("error.verify_class_server"), ss.getHostName());
    }
    catch (UnknownHostException uhe) {
      message = MessageFormat.format(Strings.get("error.unknown_host"), ss.getHostName());
    }
    catch (ConnectException ce) {
      message = MessageFormat.format(Strings.get("error.could_not_connect"),
          ss.getHostName(), ss.getClassPort(), ce.getMessage());
    } catch (Exception e) {
      message = e.getClass().getSimpleName() + ": " + e.getMessage();
    }
    return message;
  }

  public String testRmiServer() {
    String message = Strings.get("ok");
    String controller = "Controller";
    String url = rmiNode() + controller;
    try {
      Thread.currentThread().setContextClassLoader(RMIClassLoader.getClassLoader(getCodeBase().toString()));
      Naming.lookup(url);
    } catch (MalformedURLException e) {
      message = MessageFormat.format(Strings.get("error.bad_url"), url);
    } catch (NotBoundException e) {
      message = MessageFormat.format(Strings.get("error.bad_rmi_name"), controller);
    } catch (RemoteException e) {
      message = MessageFormat.format(Strings.get("error.remote_exception"), e.getMessage());
    } catch (Exception e) {
      message = e.getClass().getSimpleName() + ": " + e.getMessage();
    }
    return message;
  }
}
