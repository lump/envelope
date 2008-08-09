package us.lump.envelope.client.ui.components;

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
 * @version $Revision: 1.2 $
 */

public class Spinner extends JComponent {
  static final int steps = 30;
  static final int degrees = 360;
  static final int step = degrees / steps;
  Stroke stroke;
  Dimension size;
  AffineTransform translate = new AffineTransform();
  AffineTransform rotate = new AffineTransform();
  Thread animator;
  boolean twirling = false;

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
        if (twirling) paintImmediately(new Rectangle(new Point(0, 0), size));
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

    Graphics2D g = (Graphics2D)g1;
    g.transform(translate);
    if (twirling) rotate.rotate(Math.toRadians(step));
    g.transform(rotate);
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                       RenderingHints.VALUE_ANTIALIAS_ON);
    g.setPaint(Color.BLACK);
    g.setStroke(stroke);
    g.draw(new Line2D.Float(((size.width / 3F) * -1), 0.0F,
                            (size.width / 3F), 0.0F));
  }
}
