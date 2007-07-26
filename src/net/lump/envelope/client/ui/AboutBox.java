package us.lump.envelope.client.ui;

import us.lump.envelope.client.ui.defs.Strings;
import us.lump.envelope.client.ui.defs.Fonts;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ResourceBundle;

public class AboutBox extends JFrame implements ActionListener {
  protected JLabel titleLabel, aboutLabel[];
  protected static int labelCount = 8;
  protected static int aboutWidth = 280;
  protected static int aboutHeight = 230;
  protected static int aboutTop = 200;
  protected static int aboutLeft = 350;
  protected Font titleFont, bodyFont;
  protected ResourceBundle resbundle;

  public AboutBox() {
    super("");

    this.setResizable(false);
    SymWindow aSymWindow = new SymWindow();
    this.addWindowListener(aSymWindow);

    this.getContentPane().setLayout(new BorderLayout(15, 15));

    aboutLabel = new JLabel[labelCount];
    aboutLabel[0] = new JLabel("");
    aboutLabel[1] = new JLabel(Strings.get("envelope_budget"));
    aboutLabel[1].setFont(Fonts.getFont("sans14Bold"));
    aboutLabel[2] = new JLabel(Strings.get("version"));
    aboutLabel[2].setFont(Fonts.getFont("sans10"));
    aboutLabel[3] = new JLabel("");
    aboutLabel[4] = new JLabel("");
    aboutLabel[5] = new JLabel("JDK " + System.getProperty("java.version"));
    aboutLabel[5].setFont(Fonts.getFont("sans10"));
    aboutLabel[6] = new JLabel(Strings.get("copyright"));
    aboutLabel[7] = new JLabel("");

    Panel textPanel2 = new Panel(new GridLayout(labelCount, 1));
    for (int i = 0; i < labelCount; i++) {
      aboutLabel[i].setHorizontalAlignment(JLabel.CENTER);
      textPanel2.add(aboutLabel[i]);
    }

    this.getContentPane().add(textPanel2, BorderLayout.CENTER);
    this.pack();
    this.setSize(20 + (int)this.getSize().getWidth(),  20 + (int)this.getSize().getHeight());

//    this.setLocation(aboutLeft, aboutTop);
//    this.setSize(aboutWidth, aboutHeight);
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