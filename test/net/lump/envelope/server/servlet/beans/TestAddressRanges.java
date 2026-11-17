package net.lump.envelope.server.servlet.beans;

import junit.framework.TestCase;

/** The /configure allow list's range matching.  Needs no server. */
public class TestAddressRanges extends TestCase {

  /** The swarm's list: host bits ignored, as a router reads it. */
  public void testTheSwarmRange() {
    AddressRanges lan = new AddressRanges("172.17.2.0/20");
    assertTrue(lan.contains("172.17.2.40"));
    assertTrue("the /20 starts at 172.17.0.0", lan.contains("172.17.0.1"));
    assertTrue("and ends at 172.17.15.255", lan.contains("172.17.15.255"));
    assertFalse(lan.contains("172.17.16.0"));
    assertFalse(lan.contains("172.16.255.255"));
    assertFalse(lan.contains("10.0.0.5"));
    assertFalse(lan.contains("203.0.113.9"));
  }

  /** The default: every private and loopback range, IPv4 and IPv6. */
  public void testTheDefault() {
    AddressRanges priv = new AddressRanges("127.0.0.0/8, 10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16, ::1/128, fc00::/7, fe80::/10");
    for (String in : new String[]{"127.0.0.1", "10.1.2.3", "172.20.0.1", "192.168.1.10", "::1", "0:0:0:0:0:0:0:1",
                                  "fd12:3456::1", "fe80::1", "::ffff:10.0.0.1"})
      assertTrue(in, priv.contains(in));
    for (String out : new String[]{"8.8.8.8", "172.32.0.1", "11.0.0.1", "2001:db8::1", "::ffff:8.8.8.8"})
      assertFalse(out, priv.contains(out));
  }

  /** A typo narrows the list rather than opening it, and nothing is ever looked up. */
  public void testMalformedAndNames() {
    AddressRanges r = new AddressRanges("10.0.0.0/8, 192.168.1.0/99, banana, 300.1.1.1/8");
    assertTrue(r.contains("10.9.9.9"));
    assertFalse(r.contains("192.168.1.5"));
    assertFalse("a host name is not an address", new AddressRanges("127.0.0.0/8").contains("localhost"));
    assertFalse(new AddressRanges("127.0.0.0/8").contains(null));
    assertTrue("nothing usable admits nobody", new AddressRanges("nonsense").isEmpty());
    assertFalse(new AddressRanges("").contains("127.0.0.1"));
  }
}
