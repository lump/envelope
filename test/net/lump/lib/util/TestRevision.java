package net.lump.lib.util;

import junit.framework.TestCase;
import org.junit.Test;

/**
 * Test Revision.
 *
 * <p>What is asserted here is what the build can actually promise. The version
 * comes from two files the build bundles onto the classpath, so it is there
 * whenever the artifact was built by Maven and absent when the classes are run
 * some other way -- both are legitimate, and {@code describe()} has to stay
 * truthful either way. The provenance fields are recorded only when the build is
 * told, so they are asserted as "either absent or sane", never as present.
 *
 * @author Troy Bowman
 */
public class TestRevision extends TestCase {

  /** Either a build recorded the version or it did not; nothing in between. */
  @Test
  public void testVersionIsWholeOrAbsent() {
    if (Revision.version() == null) {
      assertNull("part of a version with no version", Revision.major());
      assertNull("part of a version with no version", Revision.minor());
      assertNull("part of a version with no version", Revision.patch());
    }
    else {
      assertNotNull("a version with no major", Revision.major());
      assertNotNull("a version with no minor", Revision.minor());
      assertNotNull("a version with no patch", Revision.patch());
      assertEquals("version is not its three parts joined",
                   Revision.major() + "." + Revision.minor() + "." + Revision.patch(),
                   Revision.version());
    }
  }

  /**
   * Run under Maven, which is the only way this test runs, the version is on the
   * classpath -- so this also catches the antrun execution that reads the three
   * version files, or the resource filtering that writes them, silently breaking.
   *
   * <p>It is the same string build.sh tags the image with, so a mismatch here
   * means an artifact could ship in an image claiming a different version.
   */
  @Test
  public void testMavenBuildsCarryTheVersion() {
    assertNotNull("the version never reached the classpath; check the antrun"
                  + " read-version-files execution and the filtered resource in pom.xml",
                  Revision.version());
    assertTrue("a version should look like 0.10.0, not " + Revision.version(),
               Revision.version().matches("^\\d+\\.\\d+\\.\\d+$"));
  }

  /** The build timestamp is Maven's own, so it is always filtered in. */
  @Test
  public void testTheBuildTimestampIsRecorded() {
    assertNotNull("revision.properties was not filtered; ${maven.build.timestamp}"
                  + " never became a value", Revision.built());
    assertTrue("a build timestamp should be ISO-8601 UTC, not " + Revision.built(),
               Revision.built().matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z$"));
  }

  /**
   * Provenance is optional, but an unsubstituted placeholder must never be
   * mistaken for data -- reporting "${envelope.commit}" as a commit would be
   * worse than reporting nothing.
   */
  @Test
  public void testUnrecordedProvenanceReadsAsAbsent() {
    for (String value : new String[]{Revision.commit(), Revision.branch(),
                                     Revision.committer(), Revision.committed()}) {
      if (value != null) {
        assertFalse("an unsubstituted placeholder is being reported as data: " + value,
                    value.startsWith("${"));
        assertTrue("a recorded value should not be blank", value.trim().length() > 0);
      }
    }
  }

  /** describe() has to say something true whatever the build recorded. */
  @Test
  public void testDescribeIsAlwaysUsable() {
    String described = Revision.describe();
    assertNotNull("describe() returned null", described);
    assertTrue("describe() returned nothing", described.trim().length() > 0);
    assertFalse("describe() leaked a placeholder: " + described, described.contains("${"));

    if (Revision.version() == null) assertEquals("development build", described);
    else assertTrue("describe() does not name the version: " + described,
                    described.startsWith(Revision.version()));
  }
}
