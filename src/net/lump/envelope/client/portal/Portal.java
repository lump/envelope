package us.lump.envelope.client.portal;

import us.lump.envelope.Command;
import us.lump.envelope.client.ui.components.forms.Preferences;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.client.ui.prefs.ServerSettings;
import us.lump.envelope.exception.EnvelopeException;
import us.lump.envelope.exception.SessionException;
import us.lump.envelope.server.rmi.Controller;
import us.lump.lib.util.Encryption;

import javax.swing.*;
import javax.crypto.SecretKey;
import javax.crypto.KeyGenerator;
import java.awt.*;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.UnmarshalException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.PublicKey;
import java.text.MessageFormat;

/**
 * All portals should subclass this class, as this provides a single point of
 * exit/entry to the server along with exception handling.
 *
 * @author Troy Bowman
 * @version $Revision: 1.18 $
 */

abstract class Portal {
  private Controller controller;
  Component frame;

  public Serializable invoke(Command command) throws EnvelopeException {
    return invoke(null, command);
  }

  public Serializable invoke(Component frame, Command command)
      throws EnvelopeException {
    // sign the command before invoking
    try {
      LoginSettings ls = LoginSettings.getInstance();
      command.sign(ls.getUsername(), ls.getKeyPair().getPrivate());
    } catch (Exception e) {
      handleException(e);
    }
    return rawInvoke(frame, command);
  }


  public Serializable rawInvoke(Command command)
      throws EnvelopeException {
    return rawInvoke(null, command);
  }

  // this is the real deal.
  public Serializable rawInvoke(Component jframe, Command command) {

    // set instance variables for use if exceptions happen
    this.frame = jframe;

    try {
      return SocketController.getSocket().invoke(command);
//      return getController().invoke(command);
    } catch (Exception e) {
      handleException(e);
      return null;
    }
  }

  private Controller getController()
      throws IOException, NotBoundException {
    if (controller == null)
      controller = (Controller)Naming.lookup(
          ServerSettings.getInstance().rmiController());
    return controller;
  }

  private void handleException(Exception incomingException) {

    //try to find a nested envelope exception first...
    Throwable cause = incomingException;
    boolean found = false;
    while (cause != null && !found) {
      if (cause instanceof EnvelopeException) {
        found = true;
        SessionException e = (SessionException)cause;
        switch (e.getType()) {
          case Invalid_Credentials:
            checkSettings(e);
            break;
          case Invalid_Session:
            fatalError(e);
            break;
          case Invalid_User:
            checkSettings(e);
            break;
          default:
            error(incomingException);
        }
        break;
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

  private void error(Exception e) {
    error(e, null);
  }

  private void error(Exception e, String message) {
    if (message == null)
      message = e.getClass().getSimpleName() + ": " + e.getLocalizedMessage();
    e.printStackTrace();
    JOptionPane.showMessageDialog(
        frame,
        message,
        Strings.get("error"),
        JOptionPane.ERROR_MESSAGE);
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

  private void checkSettings(Exception e) {
    Preferences appPrefs = Preferences.getInstance();

    if (!appPrefs.areServerSettingsOk()) {
      appPrefs.selectTab(Strings.get("server"));
      appPrefs.setVisible(true);
    } else if (!appPrefs.areLoginSettingsOk()) {
      appPrefs.selectTab(Strings.get("login"));
      appPrefs.setVisible(true);
    } else {
      error(e);
    }
  }
}
