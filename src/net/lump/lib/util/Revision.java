package net.lump.lib.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * Which build of this application is running.
 *
 * <p>This used to be an enum of CVS keywords -- {@code $Id$}, {@code $Author$},
 * {@code $Revision$} and the rest -- expanded by the version control system when a
 * file was checked out. Git has no equivalent and will not grow one: a file's
 * bytes are an input to the commit hash, so a file cannot contain its own
 * revision. (Git's one keyword, the {@code ident} attribute, expands {@code $Id$}
 * to the blob hash -- the hash of the content, carrying no commit, author or date
 * -- and {@code export-subst} substitutes real commit data, but only into
 * {@code git archive} output and only the archive's own commit rather than each
 * file's.) So these keywords had been frozen at their 2009 values for years, and
 * {@code AboutBox} was showing the literal string {@code Exp}.
 *
 * <p>The version is the application's own instead, held in three files at the top
 * of the tree and joined with dots:
 *
 * <ul>
 *   <li>{@code .Major.version}</li>
 *   <li>{@code .Minor.version}</li>
 *   <li>{@code .Patch.version}</li>
 * </ul>
 *
 * <p>Any one of them is bumped depending on what the change was. They are the
 * single source of truth: {@code build.sh} reads the same three to tag the image
 * {@code <branch>-<major>.<minor>.<patch>}, so the artifact and the image it ships
 * in always agree. The build reads them into {@code revision.properties} beside
 * this class, so the answer is the same whether the artifact was built on a host
 * or inside the image -- {@code .dockerignore} lets exactly those three files
 * through for that reason.
 *
 * <p>Provenance -- commit, branch, who committed it and when -- is recorded only
 * when the build is told, through {@code -Denvelope.commit} and friends. There is
 * deliberately no git-reading Maven plugin: the distribution build runs inside the
 * image, where there is no {@code .git} to read, so a plugin could only ever
 * answer for host builds. Everything here degrades to null rather than guessing.
 *
 * @author Troy Bowman
 */
public final class Revision {

  private static final String PROPERTIES = "/net/lump/lib/util/revision.properties";

  private static final Properties properties = readProperties();

  private Revision() {
  }

  /**
   * The application version, as in {@code 0.10.0}. The same string build.sh tags
   * the image with.
   *
   * @return the version, or null if the build did not record one
   */
  public static String version() {
    return property("version");
  }

  /** From {@code .Major.version}. */
  public static String major() {
    return property("major");
  }

  /** From {@code .Minor.version}. */
  public static String minor() {
    return property("minor");
  }

  /** From {@code .Patch.version}. */
  public static String patch() {
    return property("patch");
  }

  /** When this artifact was built, ISO-8601 in UTC, or null. */
  public static String built() {
    return property("built");
  }

  /** The short commit it was built from, or null if the build was not told. */
  public static String commit() {
    return property("commit");
  }

  /** The branch it was built from, or null if the build was not told. */
  public static String branch() {
    return property("branch");
  }

  /** Who committed it, or null if the build was not told. */
  public static String committer() {
    return property("committer");
  }

  /** When it was committed, or null if the build was not told. */
  public static String committed() {
    return property("committed");
  }

  /**
   * One line naming this build, for an About box or a log line.
   *
   * <p>Says as much as the build actually recorded and no more: the version on its
   * own for an ordinary build, with the branch, commit and build time appended
   * when they were supplied.
   *
   * @return something true about this build, never null
   */
  public static String describe() {
    String version = version();
    if (version == null) return "development build";

    StringBuilder s = new StringBuilder(version);
    String branch = branch();
    String commit = commit();
    if (branch != null && commit != null) s.append(" (").append(branch).append(' ').append(commit).append(')');
    else if (commit != null) s.append(" (").append(commit).append(')');
    else if (branch != null) s.append(" (").append(branch).append(')');

    String built = built();
    if (built != null) s.append(" built ").append(built);
    return s.toString();
  }

  /**
   * A value from the filtered build properties.
   *
   * @param key the property name
   *
   * @return its value, or null when it was not recorded -- which covers an empty
   *         value and an unsubstituted {@code ${...}} placeholder alike, so a
   *         build that never filtered the file reads as "not recorded" rather than
   *         reporting the placeholder as though it were data.  The check is for a
   *         placeholder anywhere in the value, not just at the start: version is
   *         built from three properties, so one of them failing to substitute
   *         leaves something like {@code 0.10.${envelope.patch}}, which is worse
   *         than admitting the version is unknown
   */
  private static String property(String key) {
    if (properties == null) return null;
    String value = properties.getProperty(key);
    if (value == null) return null;
    value = value.trim();
    return value.isEmpty() || value.contains("${") ? null : value;
  }

  private static Properties readProperties() {
    try (InputStream in = Revision.class.getResourceAsStream(PROPERTIES)) {
      if (in == null) return null;
      Properties p = new Properties();
      try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
        p.load(r);
      }
      return p;
    } catch (IOException e) {
      return null;
    }
  }
}
