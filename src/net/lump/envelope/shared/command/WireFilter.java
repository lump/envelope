package net.lump.envelope.shared.command;

import org.apache.log4j.Logger;

import java.io.ObjectInputFilter;
import java.util.Set;

/**
 * The classes a command from a client may contain, and nothing else.
 *
 * <p>Applied to the {@code ObjectInputStream} that reads every command, so a class
 * not listed here is refused before an instance of it is constructed -- before
 * its {@code readObject}, {@code readResolve} or any field of it runs.  That is
 * what makes Java deserialization of client data safe to keep: a gadget chain
 * needs classes from the server's classpath that have no business in a command,
 * and none of them are here.
 *
 * <p>Every command's parameters are plain values -- ids, strings, booleans,
 * dates, version stamps, amounts -- so the list is exactly:
 * <ul>
 *   <li>{@code Command} and its {@code Name};</li>
 *   <li>{@code Money}, and the JDK value types: boxed primitives,
 *       {@code BigDecimal}/{@code BigInteger}, the three date types, and the
 *       {@code ArrayList} a command keeps its parameters in;</li>
 *   <li>{@code KeyRep}, the form a {@code PublicKey} takes on the wire, and the
 *       RSA key it resolves to, for the login exchange.</li>
 * </ul>
 * No entity and no Hibernate class.  An earlier cut of 0.12 admitted both,
 * because the saves took entities, and a {@code PersistentBag} whose collection
 * key the client had chosen was enough to read another budget through
 * {@code saveTransaction}.  Don't add an entity parameter back: send its id and
 * the fields that change.  A new parameter type means a new line here; the
 * refusal is logged with the class name.  Limits on depth, references, array
 * length and bytes bound what a caller can make the server allocate.
 *
 * @author troy
 */
public final class WireFilter implements ObjectInputFilter {

  private static final Logger logger = Logger.getLogger(WireFilter.class);

  /** The filter for commands arriving at {@code /invoke}. */
  public static final WireFilter COMMANDS = new WireFilter();

  // a command is Command > ArrayList > value, with a key a level or two deeper
  static final long MAX_DEPTH = 16;
  static final long MAX_REFERENCES = 1_000;
  static final long MAX_ARRAY_LENGTH = 10_000;
  static final long MAX_BYTES = 1024 * 1024;

  private static final Set<String> CLASSES = Set.of(
      "net.lump.envelope.shared.command.Command",
      "net.lump.envelope.shared.command.Command$Name",
      "net.lump.lib.Money",
      "java.lang.Boolean", "java.lang.Byte", "java.lang.Short", "java.lang.Integer",
      "java.lang.Long", "java.lang.Float", "java.lang.Double", "java.lang.Character",
      "java.lang.Number", "java.lang.Enum", "java.lang.String",
      "java.math.BigDecimal", "java.math.BigInteger",
      "java.util.Date", "java.sql.Date", "java.sql.Timestamp",
      "java.util.ArrayList",
      // ArrayList.readObject checks its backing Object[] against the filter
      // before filling it; Object itself is not Serializable, so nothing can
      // be constructed through this line
      "java.lang.Object",
      "java.security.KeyRep", "java.security.KeyRep$Type",
      // what KeyRep.readResolve hands back, which the filter is asked about
      // again: the JDK rebuilds it from its encoded bytes through KeyFactory,
      // and the class itself refuses direct deserialization
      "sun.security.rsa.RSAPublicKeyImpl");

  private WireFilter() {}

  @Override
  public Status checkInput(FilterInfo info) {
    if (info.depth() > MAX_DEPTH || info.references() > MAX_REFERENCES
        || info.arrayLength() > MAX_ARRAY_LENGTH || info.streamBytes() > MAX_BYTES) {
      logger.warn("refused a command past the limits: depth " + info.depth() + ", references "
          + info.references() + ", array length " + info.arrayLength() + ", bytes " + info.streamBytes());
      return Status.REJECTED;
    }

    Class<?> c = info.serialClass();
    if (c == null) return Status.UNDECIDED;   // a limits-only check, passed above
    while (c.isArray()) c = c.getComponentType();
    if (c.isPrimitive()) return Status.ALLOWED;

    String name = c.getName();
    if (CLASSES.contains(name)) return Status.ALLOWED;

    // UNDECIDED would let it through, so say no explicitly
    logger.warn("refused to deserialize " + name + " in a command");
    return Status.REJECTED;
  }
}
