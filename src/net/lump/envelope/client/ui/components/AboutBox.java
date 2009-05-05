package us.lump.envelope.client.ui.components;

import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.images.ImageResource;
import us.lump.lib.util.Revision;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

public class AboutBox extends JFrame implements ActionListener {
  protected JLabel titleLabel, aboutLabel[];
  protected static int aboutTop = 200;
  protected static int aboutLeft = 350;
  protected Font titleFont, bodyFont;
  protected ResourceBundle resbundle;

  public AboutBox() {
    super("");

    this.setIconImages(ImageResource.getFrameList());
    this.setResizable(false);
    SymWindow aSymWindow = new SymWindow();
    this.addWindowListener(aSymWindow);

    this.getContentPane().setLayout(new BorderLayout(15, 15));
    JLabel image = new JLabel();
    Icon icon = ImageResource.icon.envelope_256.get();
    image.setIcon(icon);

    JLabel[] jlabels = new JLabel[]{new JLabel(Strings.get("envelope.budget")), new JLabel(Revision.nameOrState()),
        new JLabel(Strings.get("copyright")), new JLabel(), new JLabel("JDK " + System.getProperty("java.version")),};

    GridLayout gl = new GridLayout(jlabels.length, 1);
    Panel textPanel = new Panel(gl);
    for (JLabel c : jlabels) {
      c.setHorizontalAlignment(JLabel.CENTER);
      textPanel.add(c);
    }

    this.getContentPane().add(image, BorderLayout.NORTH);
    image.setHorizontalAlignment(JLabel.CENTER);
    this.getContentPane().add(textPanel, BorderLayout.CENTER);

    this.pack();
    this.setSize(20 + (int)this.getSize().getWidth(), 20 + (int)this.getSize().getHeight());
  }

  class SymWindow extends java.awt.event.WindowAdapter {
    public void windowClosing(java.awt.event.WindowEvent event) {
      setVisible(false);
    }
  }

  public void actionPerformed(ActionEvent newEvent) {
    setVisible(false);
  }
}