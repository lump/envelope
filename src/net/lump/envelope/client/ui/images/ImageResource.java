package us.lump.envelope.client.ui.images;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;

/** Image resource. */
public class ImageResource {
  public static enum icon {
    envelope,
    envelope_16,
    envelope_32,
    envelope_48,
    envelope_64,
    envelope_96,
    envelope_128,
    envelope_256,
    envelope_512,
    envelope_empty,
    envelope_onebill,
    envelope_full,
    envelope_overflow,
    envelope_red,
    budget,
    budget_closed,
    account,
    account_closed;

    public static final String png = "png";
    public static final String ico = "ico";
    public static final String jpg = "jpg";
    public static final String svg = "svg";
    public static final String icns = "icns";

    public Icon get() { return get(png); }

    public Icon get(String ext) {
      return new ImageIcon(
          ImageResource.class.getResource(this.name() + "." + ext));
    }

    public Image getImage() {
      return getImage(png);
    }

    public Image getImage(String ext) {
      return ((ImageIcon)get(ext)).getImage();
    }
  }

  private ImageResource() {}

  public static java.util.List<Image> getFrameList() {
    ArrayList<Image> list = new ArrayList();
    list.add(icon.envelope_512.getImage());
    list.add(icon.envelope_256.getImage());
    list.add(icon.envelope_128.getImage());
    list.add(icon.envelope_96.getImage());
    list.add(icon.envelope_64.getImage());
    list.add(icon.envelope_48.getImage());
    list.add(icon.envelope_32.getImage());
    list.add(icon.envelope_16.getImage());
    return list;
  }
}
