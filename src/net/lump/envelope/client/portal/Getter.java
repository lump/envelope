package us.lump.envelope.client.portal;

import us.lump.envelope.server.rmi.Controller;
import us.lump.envelope.server.rmi.Command;
import us.lump.envelope.server.rmi.Cmd;
import us.lump.envelope.client.ui.prefs.ServerSettings;

import java.rmi.Naming;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.net.MalformedURLException;

/**
 * .
 *
 * @author troy
 * @version $Id: Getter.java,v 1.1 2007/07/21 20:15:04 troy Exp $
 */
public class Getter {
  public void get() {
    try {
      Controller controller = (Controller)Naming.lookup(ServerSettings.getInstance().rmiNode() + "Controller");
      String s = (String)controller.invoke(new Command(Cmd.ping));
      System.out.println(s);
    } catch (NotBoundException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    } catch (MalformedURLException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    } catch (RemoteException e) {
      e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
    }


  }
}
