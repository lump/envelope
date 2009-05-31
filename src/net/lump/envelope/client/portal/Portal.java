package us.lump.envelope.client.portal;

import us.lump.envelope.client.HttpClient;
import us.lump.envelope.client.ui.components.forms.Preferences;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.command.Command;
import us.lump.envelope.exception.AbortException;
import us.lump.envelope.exception.EnvelopeException;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.rmi.NotBoundException;
import java.rmi.UnmarshalException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * All portals should subclass this class, as this provides a single point of exit/entry to the server along with exception
 * handling.
 *
 * @author Troy Bowman
 * @version $Id: Portal.java,v 1.27 2009/05/31 21:45:30 troy Exp $
 */

abstract class Portal {
  Component frame;

  public Serializable invoke(Command command) throws AbortException {
    return invoke(null, command);
  }

  public Serializable rawInvoke(Command command) throws AbortException {
    return rawInvoke(null, command);
  }

  public Serializable invoke(Component frame, Command command) throws AbortException {
    try {
      LoginSettings ls = LoginSettings.getInstance();
      // sign the command before invoking
      command.sign(ls.getUsername(), ls.getKeyPair().getPrivate());
    } catch (Exception e) {
      handleException(e);
    }
    return rawInvoke(frame, command);
  }

  public Serializable rawInvoke(Component frame, Command command) throws AbortException {
    this.frame = frame;
    Serializable retval = null;
    try {
      retval = new HttpClient().invoke(command);
    } catch (Exception e) {
      handleException(e);
    }
    return retval;
  }

  public List<Serializable> invoke(List<Command> commands) throws AbortException {
    try {
      LoginSettings ls = LoginSettings.getInstance();
      for (Command c : commands) c.sign(ls.getUsername(), ls.getKeyPair().getPrivate());
    } catch (Exception e) {
      handleException(e);
    }
    return rawInvoke(null, commands);
  }

  public List<Serializable> rawInvoke(Component jframe, List<Command> commands) throws AbortException {
    ArrayList<Serializable> retval = new ArrayList<Serializable>();
    this.frame = jframe;
    try {
//      SocketClient.getSocket().invoke(commands);
      for (Command c : commands) retval.add(new HttpClient().invoke(c));
    } catch (Exception e) {
      handleException(e);
    }
    return retval;
  }

  private void handleException(Exception incomingException) throws AbortException {

    //try to find a nested envelope exception first...
    Throwable cause = incomingException;
    boolean found = false;
    while (cause != null && !found) {
      if (cause instanceof EnvelopeException) {
        found = true;
        EnvelopeException e = (EnvelopeException)cause;
        if (e.getType() == EnvelopeException.Type.Session) {
          checkSettings(e);
        } else {
          error(incomingException);
        }
      }
      if (cause instanceof UnmarshalException
          && cause.getCause() instanceof InvalidClassException
          && cause.getCause().getMessage()
          .matches("^.*local class incompatible.*$")) {
        found = true;
        String message =
            MessageFormat.format(
                Strings.get("error.invalidclassexception"),
                ((InvalidClassException)cause.getCause()).classname);
        fatalError(incomingException, message);
        break;
      }
      cause = cause.getCause();
    }

    // move on to handling generic errors
    if (!found)
      try {
        throw (incomingException);
      } catch (java.net.ConnectException e) {
        checkSettings(e);
      } catch (java.net.MalformedURLException e) {
        checkSettings(e);
      } catch (java.rmi.ConnectException e) {
        checkSettings(e);
      } catch (NotBoundException e) {
        checkSettings(e);
      } catch (UnsupportedEncodingException e) {
        fatalError(e);
      } catch (IOException e) {
        checkSettings(e);
      } catch (NoSuchAlgorithmException e) {
        fatalError(e);
      } catch (SignatureException e) {
        fatalError(e);
      } catch (InvalidKeyException e) {
        fatalError(e);
      } catch (ClassNotFoundException e) {
        fatalError(e);
      } catch (Exception e) {
        error(e);
      }
  }

  private void error(Exception e) throws AbortException {
    error(e, null);
  }

  private void error(Exception e, String message) throws AbortException {
    if (message == null)
      message = e.getClass().getSimpleName() + ": " + e.getLocalizedMessage();
    e.printStackTrace();
    JOptionPane.showMessageDialog(
        frame,
        message,
        Strings.get("error"),
        JOptionPane.ERROR_MESSAGE);
    throw new AbortException(e);
  }

  private void fatalError(Exception e) {
    fatalError(e, null);
  }

  private void fatalError(Exception e, String message) {
    if (message == null)
      message = e.getClass().getSimpleName() + ": " + e.getLocalizedMessage();
    e.printStackTrace();
    JOptionPane.showMessageDialog(
        frame,
        message,
        Strings.get("error.fatal"),
        JOptionPane.ERROR_MESSAGE);
    System.exit(1);
  }

  private void checkSettings(Exception e) throws AbortException {
    Preferences appPrefs = Preferences.getInstance();

    if (!(e instanceof EnvelopeException) && e instanceof IOException) error(e);
    else if (e instanceof EnvelopeException) {
      appPrefs.setSessionState(Preferences.State.bad, ((EnvelopeException)e).getName().toString().replaceAll("_", " "));
      appPrefs.selectTab(Strings.get("login"));
      appPrefs.setVisible(true);
      throw new AbortException(e);
    } else if (!appPrefs.areServerSettingsOk()) {
      appPrefs.selectTab(Strings.get("server"));
      appPrefs.setVisible(true);
      throw new AbortException(e);
    } else if (!appPrefs.areLoginSettingsOk()) {
      appPrefs.selectTab(Strings.get("login"));
      appPrefs.setVisible(true);
      throw new AbortException(e);
    } else error(e);
  }
}
