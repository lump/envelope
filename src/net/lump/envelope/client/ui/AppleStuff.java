package net.lump.envelope.client.ui;

import com.apple.eawt.Application;
import com.apple.eawt.ApplicationAdapter;
import com.apple.eawt.ApplicationEvent;
import net.lump.envelope.client.ui.components.forms.Preferences;

/**
 * This is in its own class to prevent having to ship apple's ui.jar.
 */
public class AppleStuff {

  Application application;
  ApplicationAdapter applicationAdapter;


  AppleStuff() {

    application = Application.getApplication();
    application.setEnabledPreferencesMenu(true);
//    application.setDockIconBadge("Hi");

    applicationAdapter = new com.apple.eawt.ApplicationAdapter() {
      public void handleAbout(ApplicationEvent e) {
        MainFrame.getInstance().aboutBox();
        e.setHandled(true);
      }

      public void handleOpenApplication(ApplicationEvent e) {
      }

      public void handleOpenFile(ApplicationEvent e) {
      }

      public void handlePreferences(ApplicationEvent e) {
        Preferences.getInstance().setVisible(true);
      }

      public void handlePrintFile(ApplicationEvent e) {
      }

      public void handleQuit(ApplicationEvent e) {
        MainFrame.getInstance().exit(0);
      }
    };

    application.addApplicationListener(applicationAdapter);
  }
}
