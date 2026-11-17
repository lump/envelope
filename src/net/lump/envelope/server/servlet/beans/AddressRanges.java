package net.lump.envelope.server.servlet.beans;

import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * A list of CIDR ranges, IPv4 and IPv6, and whether an address falls in one.
 *
 * <p>For the /configure gate.  A range's host bits are ignored, as a router would
 * read it: 172.17.2.0/20 is 172.17.0.0 to 172.17.15.255.  A malformed entry is
 * logged and skipped, so a typo narrows the list rather than opening it; a list
 * with nothing usable in it admits nobody.  Addresses are compared as given --
 * the caller hands in a literal from getRemoteAddr, so nothing is looked up.
 *
 * @author troy
 */
final class AddressRanges {

  private static final Logger logger = Logger.getLogger(AddressRanges.class);

  private final List<byte[][]> ranges = new ArrayList<byte[][]>();   // {network, mask}

  AddressRanges(String list) {
    if (list == null) return;
    for (String entry : list.split("[,\\s]+")) {
      if (entry.isEmpty()) continue;
      try {
        String[] parts = entry.split("/", 2);
        byte[] network = literal(parts[0]);
        int bits = parts.length == 2 ? Integer.parseInt(parts[1]) : network.length * 8;
        if (bits < 0 || bits > network.length * 8) throw new IllegalArgumentException("prefix length " + bits);
        byte[] mask = new byte[network.length];
        for (int i = 0; i < bits; i++) mask[i / 8] |= (byte)(0x80 >>> (i % 8));
        for (int i = 0; i < network.length; i++) network[i] &= mask[i];
        ranges.add(new byte[][]{network, mask});
      } catch (Exception e) {
        logger.error("ignoring \"" + entry + "\" in an address list: " + e.getMessage());
      }
    }
  }

  boolean isEmpty() {
    return ranges.isEmpty();
  }

  /** Whether the address is in any range; false for anything that is not an address literal. */
  boolean contains(String address) {
    byte[] a;
    try {
      a = literal(address);
    } catch (Exception e) {
      return false;
    }
    for (byte[][] r : ranges) {
      if (r[0].length != a.length) continue;
      boolean in = true;
      for (int i = 0; i < a.length && in; i++) in = (a[i] & r[1][i]) == r[0][i];
      if (in) return true;
    }
    return false;
  }

  /** The bytes of an IP address literal, refusing a host name rather than resolving it. */
  private static byte[] literal(String s) throws Exception {
    String t = s.trim();
    if (t.startsWith("[") && t.endsWith("]")) t = t.substring(1, t.length() - 1);
    // InetAddress resolves anything it does not parse as a literal, so only a
    // well-formed one may reach it: four octets of 0-255, or IPv6 (which has a
    // colon, and which InetAddress never sends to DNS)
    boolean v4 = t.matches("((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)");
    boolean v6 = t.contains(":") && t.matches("[0-9A-Fa-f:.]+(%[0-9A-Za-z]+)?");
    if (!v4 && !v6) throw new IllegalArgumentException("not an address literal");
    // InetAddress turns an IPv4-mapped IPv6 address into the IPv4 one, so
    // ::ffff:10.0.0.1 matches 10.0.0.0/8
    return InetAddress.getByName(t).getAddress();
  }
}
