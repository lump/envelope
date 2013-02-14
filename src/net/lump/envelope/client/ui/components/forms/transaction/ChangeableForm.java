package net.lump.envelope.client.ui.components.forms.transaction;

import net.lump.envelope.shared.entity.Transaction;

import javax.swing.*;

public class ChangeableForm {
  private ChangeableDateChooser transactionDate;
  private ChangeableComboBox entity;
  private ChangeableJTextField description;
  private ChangeableMoneyTextField amount;
  private ChangeableComboBox categoriesComboBox;
  private JTable allocationsTable;
  private JPanel messagePanel;
  private JLabel messageLabel;
  private AllocationFormTableModel tableModel;

  private Transaction editingTransaction = null;
  private Transaction originalTransaction = null;

  public ChangeableForm() {}
}
