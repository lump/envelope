package us.lump.envelope.client.ui.images;

import javax.swing.*;
import java.awt.*;

/** Empty class for imges anchor. */
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

    ImageIcon icon;

    icon() {
      icon =
          new ImageIcon(ImageResource.class.getResource(this.name() + ".png"));
    }

    public Icon get() { return icon; }

    public Image getImage() { return icon.getImage(); }
  }

  private ImageResource() {}
}
