package net.lump.envelope.client.ui.components;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;

/**
 * A little spinning line.
 *
 * @author Troy Bowman
 * @version $Id: Spinner.java,v 1.7 2010/02/08 06:56:30 troy Exp $
 */

public class Spinner extends JComponent {
  static final int steps = 30;
  static final int degrees = 360;
  static final int step = degrees / steps;
  int blurBacklog = 0;
  Stroke stroke;
  Dimension size;
  AffineTransform translate = new AffineTransform();
  AffineTransform rotate = new AffineTransform();
  Thread animator;
  boolean twirling = false;
  private static Color[] shades;
  private long lastDraw = 0;

  static {
    int y = 16;
    shades = new Color[y];
    for (int x = 0; x < y; x++)
      shades[x] = new Color((12 * x) + 64, (12 * x) + 64, (12 * x) + 64);
  }

  public Spinner() {
    this(20, 20);
  }

  public Spinner(int width, int height) {
    size = new Dimension(width, height);
    setPreferredSize(size);
    setMaximumSize(size);
    setMinimumSize(size);
    translate.translate(width / 2.0F, height / 2.0F);
    stroke = new BasicStroke(width / 10F,
                             BasicStroke.CAP_ROUND,
                             BasicStroke.JOIN_ROUND);

//    java.util.Timer timer = new java.util.Timer("Spinner");
//    timer.scheduleAtFixedRate(
//        new TimerTask() { public void run() { if (twirling) repaint(); } },
//        1, 30);
//argh, stipd java.util.Timer isn't responsive enough    

    animator = new Thread(new Runnable() {

      private void redo() {
        if (twirling || blurBacklog > 0)
          paintImmediately(new Rectangle(new Point(0, 0), size));
        Timer timer = new Timer(33,
                                new ActionListener() {
                                  public void actionPerformed(ActionEvent e) {
                                    redo();
                                  }
                                });
        timer.setRepeats(false);
        timer.start();
      }

      public void run() {
        redo();
      }
    });
    animator.setPriority(Thread.NORM_PRIORITY + 1);
    animator.start();
  }

  public void stopSpinning() {
    setSpinning(false);
  }

  public void startSpinning() {
    setSpinning(true);
  }

  public synchronized void setSpinning(boolean spinning) {
    twirling = spinning;
  }

  public void paintComponent(Graphics g1) {
    super.paintComponent(g1);
    long now = System.currentTimeMillis();

    Graphics2D g = (Graphics2D)g1;
    g.transform(translate);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_ON);
    g.setStroke(stroke);

    if (now > (lastDraw + 33)) {
      lastDraw = now;

      if (twirling) {
        rotate.rotate(Math.toRadians(step));
        if (blurBacklog < shades.length) blurBacklog++;
      }
      else if (blurBacklog > 0) {
        blurBacklog--;
      }
    }

    g.transform(rotate);

    if (blurBacklog > 0) {
      // rotate our reference rotation one step
      // rotate back shades.length steps
      AffineTransform tmpRotate = new AffineTransform();
      tmpRotate.rotate(Math.toRadians(step * blurBacklog * -1));
      g.transform(tmpRotate);

      // for each shade, draw a line
      for (int x = blurBacklog - 1; x > -1; x--)
        // if we're twirling, grow
        if (twirling)
          rotateAndPaintLine(g, shades[x]);
          // else, decay
        else
          rotateAndPaintLine(g, shades[x + (shades.length - blurBacklog)]);
    }
    paintLine(g, Color.black);
  }

  private void rotateAndPaintLine(Graphics2D g, Color color) {
    AffineTransform tmpRotate = new AffineTransform();
    tmpRotate.rotate(Math.toRadians(step));
    g.transform(tmpRotate);
    paintLine(g, color);
  }

  private void paintLine(Graphics2D g, Color color) {
    g.setPaint(color);
    g.draw(new Line2D.Float(((size.width / 3F) * -1), 0.0F,
                            (size.width / 3F), 0.0F));
  }
}
