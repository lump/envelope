package net.lump.envelope.client.ui.components;

import net.lump.envelope.client.ui.defs.Colors;
import net.lump.envelope.client.ui.defs.Fonts;
import net.lump.envelope.client.ui.defs.Strings;
import net.lump.envelope.client.ui.images.ImageResource;
import net.lump.lib.Money;

import javax.swing.*;
import java.awt.*;

/**
 * The incoming / balance / outgoing line under a list of allocations, with the red
 * imbalance warning above it when they do not come to the amount they should.
 *
 * <p>The transaction form's copy of these labels is laid out by its generated
 * {@code .form}, so it borrows only the two static helpers -- the icons and the
 * warning -- and keeps its own layout.  Anywhere else can use the panel whole, as
 * the allocation preset editor does.  Either way the two read alike, because
 * they are drawn by the same code.
 *
 * @author troy
 */
public class AllocationTotals extends JPanel {

  private final JPanel imbalancePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 2));
  private final JLabel imbalanceLabel = new JLabel();
  private final JLabel in = new JLabel();
  private final JLabel balance = new JLabel();
  private final JLabel out = new JLabel();

  /** @param balanceKey the Strings key for the middle label's tooltip */
  public AllocationTotals(String balanceKey) {
    super(new BorderLayout(0, 4));
    decorate(in, balance, out, balanceKey);

    // Hold the warning's height while it is hidden, so the dialog does not jump
    // by a line each time the total crosses the target.
    imbalancePanel.add(imbalanceLabel);
    int high = getFontMetrics(Fonts.sans_14_bold.getFont()).getHeight() + 6;
    imbalancePanel.setPreferredSize(new Dimension(0, high));
    add(imbalancePanel, BorderLayout.NORTH);

    JPanel line = new JPanel(new BorderLayout());
    balance.setHorizontalAlignment(SwingConstants.CENTER);
    line.add(in, BorderLayout.WEST);
    line.add(balance, BorderLayout.CENTER);
    line.add(out, BorderLayout.EAST);
    add(line, BorderLayout.CENTER);

    show(Money.ZERO, Money.ZERO, Money.ZERO, null);
  }

  /**
   * @param incoming the positive rows, as a positive amount
   * @param sum      what the rows come to
   * @param outgoing the negative rows, as a positive amount
   * @param target   what the rows should come to, or null for no warning
   */
  public void show(Money incoming, Money sum, Money outgoing, Money target) {
    in.setText(incoming.toString());
    balance.setText(sum.toString());
    out.setText(outgoing.toString());
    showImbalance(imbalancePanel, imbalanceLabel, target == null ? null : target.subtract(sum));
  }

  /** The icons and tooltips the three totals wear wherever they are shown. */
  public static void decorate(JLabel in, JLabel balance, JLabel out, String balanceKey) {
    in.setIcon(ImageResource.icon.inbox_16.get());
    in.setToolTipText(Strings.get("incoming"));
    balance.setIcon(ImageResource.icon.balance_16.get());
    balance.setToolTipText(Strings.get(balanceKey));
    out.setIcon(ImageResource.icon.outbox_16.get());
    out.setToolTipText(Strings.get("outgoing"));
  }

  /**
   * Show the gap between what allocations come to and what they should, or clear
   * the warning when there is none.
   *
   * @param gap the target less the sum; null or zero clears it
   */
  public static void showImbalance(JPanel panel, JLabel label, Money gap) {
    if (gap != null && gap.compareTo(Money.ZERO) != 0) {
      panel.setBackground(Colors.getColor("red"));
      panel.setBorder(BorderFactory.createLineBorder(Colors.getColor("black")));
      label.setForeground(Colors.getColor("white"));
      label.setFont(Fonts.sans_14_bold.getFont());
      label.setText("Imbalance: " + gap);
    }
    else {
      panel.setBackground(null);
      panel.setBorder(null);
      label.setText(null);
    }
  }
}
