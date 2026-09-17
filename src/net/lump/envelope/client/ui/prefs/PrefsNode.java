package net.lump.envelope.client.ui.prefs;

import java.util.prefs.Preferences;

/**
 * Where {@link ServerSettings} and {@link LoginSettings} keep themselves.
 *
 * <p>Both used to call {@code Preferences.userNodeForPackage(getClass())} directly,
 * which is the one store the real Swing client reads. The test suite writes its own
 * host, port and username in there to talk to a test server, so running the tests
 * silently repointed a developer's client at {@code <hostname>:8080} as user
 * "bowmantest" and left it that way -- the client, and every probe after it, then
 * failed with a {@code ConnectException} and a settings dialog.
 *
 * <p>Saving the node aside and restoring it in a JVM shutdown hook does not work:
 * hook ordering against the preferences system's own flush hook is unspecified, and
 * losing that race leaves the settings either unrestored or, worse, cleared. So the
 * node is selectable instead. Set {@code -Denvelope.prefs.node=<name>} and the
 * settings live under {@code <userRoot>/<name>}, which is how the suite keeps off
 * the real one; unset, the location is exactly what it always was.
 *
 * @author Troy Bowman
 */
public final class PrefsNode {

  /** System property naming an alternate preferences node, relative to the user root. */
  public static final String PROPERTY = "envelope.prefs.node";

  private PrefsNode() {
  }

  /**
   * The preferences node the client settings belong in.
   *
   * @param type the settings class asking, used for the default package-based node
   *
   * @return the node named by {@link #PROPERTY}, or the package node when unset
   */
  public static Preferences of(Class<?> type) {
    final String override = System.getProperty(PROPERTY);
    if (override == null || override.trim().isEmpty())
      return Preferences.userNodeForPackage(type);
    // userRoot().node() wants a relative path with no leading slash
    return Preferences.userRoot().node(override.trim().replaceAll("^/+", ""));
  }
}
