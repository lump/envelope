package net.lump.lib.util;

/**
 * @author troy
 * @version $Id: ByteFormat.java,v 1.1 2010/09/20 23:18:23 troy Exp $
 */
@SuppressWarnings({"UnusedDeclaration"})
public class ByteFormat {
  private static ByteFormat singleton = null;
  private static final Object classMutex = new Object();

  private enum Binary {
    B(0D),
    KiB(1024D),
    MiB(1048576D),
    GiB(1073741824D),
    TiB(1099511627776D),
    PiB(1125899906842624D),
    EiB(1152921504606846976D),
    ZiB(1180591620717411303424D),
    YiB(1208925819614629174706176D),
    ;

    private Double threshold;
    private Binary(Double threshold) {
      this.threshold = threshold;
    }
    public double getThreshold() { return threshold; }
  }

  private enum Decimal {
    B(0D),
    KB(1000D),
    MB(1000000D),
    GB(1000000000D),
    TB(1000000000000D),
    PB(1000000000000000D),
    EB(1000000000000000000D),
    ZB(1000000000000000000000D),
    YB(1000000000000000000000000D);

    private double threshold;
    private Decimal(double threshold) {
      this.threshold = threshold;
    }
    public double getThreshold() { return threshold; }
  }

  private ByteFormat() {
  }

  public static ByteFormat getInstance() {
    if (singleton == null) {
      synchronized (classMutex) {
        if (singleton == null) singleton = new ByteFormat();
      }
    }
    return singleton;
  }

  public String formatBytes(double bytes, boolean binary) {
    Enum level = Binary.B;
    double threshold = 0;

    if (binary) {
      for (Binary b : Binary.values()) {
        if (b.getThreshold() > bytes) break;
        level = b;
        threshold = b.getThreshold();
      }
    }
    else {
      for (Decimal d : Decimal.values()) {
        if (d.getThreshold() > bytes) break;
        level = d;
        threshold = d.getThreshold();
      }
    }

    if (level == Binary.B || level == Decimal.B) return String.format("%1.0fB", bytes);
    else return String.format("%1.2f" + level.name(), bytes / threshold);
  }
}
