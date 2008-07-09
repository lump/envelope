package us.lump.envelope.client.portal;

import org.apache.log4j.Logger;
import us.lump.envelope.Command;
import us.lump.envelope.client.ui.Preferences;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.client.ui.prefs.ServerSettings;
import us.lump.envelope.server.rmi.Controller;
import us.lump.envelope.exception.SessionException;
import us.lump.envelope.exception.EnvelopeException;

import javax.swing.*;
import java.io.Serializable;
import java.rmi.Naming;
import java.rmi.RemoteException;

/**
 * All portals should subclass this class, as this provides a single point of
 * exit/entry to the server.
 *
 * @author Troy Bowman
 * @version $Revision: 1.7 $
 */

abstract class Portal {
  Controller controller;
  Logger logger;
  JFrame frame;

  {
    this.logger = Logger.getLogger(this.getClass());
  }

  Portal() {
    try {
      this.controller = (Controller)Naming.lookup(
          ServerSettings.getInstance().rmiController());
    } catch (Exception e) {
      logger.error(e);
      Preferences p = Preferences.getInstance();
      p.areServerSettingsOk();
      p.selectTab(Strings.get("server"));
      p.setVisible(true);
    }
  }

  public Serializable invoke(Command command) throws EnvelopeException {
    return invoke(null, command);
  }

  public Serializable invoke(JFrame frame, Command command)
    throws EnvelopeException {
    LoginSettings ls = LoginSettings.getInstance();

    // sign the command
    try {
      command.sign(ls.getUsername(), ls.getKeyPair().getPrivate());
    } catch (Exception e) {
      // if we can't sign for any reason, this is fatal.
      logger.error(e);
      JOptionPane.showMessageDialog(
          frame,
          Strings.get(e.getClass().getSimpleName() + ": " + e.getMessage()),
          Strings.get("error.fatal"),
          JOptionPane.ERROR_MESSAGE);
      System.exit(1);
    }

    return rawInvoke(command);
  }


  public Serializable rawInvoke(Command command)
      throws EnvelopeException {
    return rawInvoke(null, command);
  }

  public Serializable rawInvoke(JFrame jframe, Command command)
      throws EnvelopeException {

    try {
      return controller.invoke(command);
    } catch (RemoteException e) {
      logger.warn(e);
      Throwable cause = e;
      boolean found = false;
      while (cause != null && found == false  ) {
        if (cause instanceof SessionException) {
          found = true;
          Preferences p = Preferences.getInstance();
          if (p.areLoginSettingsOk() == null) throw new SessionException(
              ((SessionException)cause).getType(), cause);
        }
        cause = cause.getCause();
      }
      if (!found) {
        //todo: catch other generic errors, rethrow non-generic errors
      }
    }
    return null;
  }
}
