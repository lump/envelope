package net.lump.envelope.client.ui;

import net.lump.envelope.client.ui.components.forms.preferences.Preferences;

import java.awt.Desktop;

/**
 * macOS menu integration.
 *
 * <p>This used to drive com.apple.eawt.Application, which was Apple's own JDK
 * extension and disappeared with Apple's JDK.  Java 9 absorbed the same three hooks
 * into {@link java.awt.Desktop}, so this is now plain SE API -- and, unlike the old
 * version, it is a no-op instead of a crash on platforms that don't support them.
 */
public class AppleStuff {

  AppleStuff() {
    if (!Desktop.isDesktopSupported()) return;
    Desktop desktop = Desktop.getDesktop();

    if (desktop.isSupported(Desktop.Action.APP_ABOUT)) {
      desktop.setAboutHandler(e -> MainFrame.getInstance().aboutBox());
    }
    if (desktop.isSupported(Desktop.Action.APP_PREFERENCES)) {
      desktop.setPreferencesHandler(
          e -> Preferences.getInstance().setVisible(true));
    }
    if (desktop.isSupported(Desktop.Action.APP_QUIT_HANDLER)) {
      desktop.setQuitHandler((e, response) -> MainFrame.getInstance().exit(0));
    }
  }
}
