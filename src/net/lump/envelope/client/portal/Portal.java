package us.lump.envelope.client.portal;

import org.apache.log4j.Logger;
import us.lump.envelope.Command;
import us.lump.envelope.client.Main;
import us.lump.envelope.client.ui.Preferences;
import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.prefs.LoginSettings;
import us.lump.envelope.client.ui.prefs.ServerSettings;
import us.lump.envelope.server.rmi.Controller;

import javax.swing.*;
import java.rmi.Naming;
import java.rmi.RemoteException;

/**
 * All portals should subclass this class, as this provides a single point of
 * exit/entry to the server.
 *
 * @author Troy Bowman
 * @version $Revision: 1.4 $
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
      Preferences p = Main.getInstance().getPreferences();
      p.areServerSettingsOk();
      p.selectTab(Strings.get("server"));
      p.setVisible(true);
    }
  }

  public Object invoke(Command command) {
    return invoke(null, command);
  }

  public Object invoke(JFrame frame, Command command) {
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


  public Object rawInvoke(Command command) {
    return rawInvoke(null, command);
  }

  public Object rawInvoke(JFrame jframe, Command command) {

    try {
      return controller.invoke(command);
    } catch (RemoteException e) {
      logger.warn(e);
      //todo: write stuff to catch not-logged-in stuff to force an auth
      //todo: catch other generic errors, rethrow non-generic errors
    }
    return null;
  }
}
