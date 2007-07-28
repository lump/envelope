/*
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package us.lump.lib.util;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * Generic class which activates Emacs keybindings for java input fields.
 * <p/>
 *
 * @todo Some actions don't work at the end of line, that's because they depend on built-in actions.
 */
public class EmacsKeyBindings {
  //--- Constant(s) ---

  public static final String killLineAction = "emacs-kill-line";

  public static final String killRingSaveAction = "emacs-kill-ring-save";

  public static final String killRegionAction = "emacs-kill-region";

  public static final String backwardKillWordAction = "emacs-backward-kill-word";

  public static final String capitalizeWordAction = "emacs-capitalize-word";

  public static final String downcaseWordAction = "emacs-downcase-word";

  public static final String killWordAction = "emacs-kill-word";

  public static final String setMarkCommandAction = "emacs-set-mark-command";

  public static final String upcaseWordAction = "emacs-upcase-word";

  public static final JTextComponent.KeyBinding[] EMACS_KEY_BINDINGS = {
          new JTextComponent.KeyBinding(
                  KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_MASK),
                  DefaultEditorKit.pasteAction),
          new JTextComponent.KeyBinding(
                  KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.ALT_MASK),
                  DefaultEditorKit.copyAction),
          new JTextComponent.KeyBinding(
                  KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_MASK),
                  DefaultEditorKit.cutAction),
          new JTextComponent.KeyBinding(
                  KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_MASK),
                  DefaultEditorKit.endLineAction),
          new JTextComponent.KeyBinding(
                  KeyStroke.getKeyStroke(KeyEvent.VK_A, InputEvent.CTRL_MASK),
                  DefaultEditorKit.beginLineAction),
          new JTextComponent.KeyBinding(
                  KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.CTRL_MASK),
                  DefaultEditorKit.deleteNextCharAction),
          new JTextComponent.KeyBinding(
                  KeyStroke.getKeyStroke(KeyEvent.VK_N, InputEvent.CTRL_MASK),
                  DefaultEditorKit.downAction),
          new JTextComponent.KeyBinding(
                  KeyStroke.getKeyStroke(KeyEvent.VK_P, InputEvent.CTRL_MASK),
                  DefaultEditorKit.upAction),
          new JTextComponent.KeyBinding(
                  KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.ALT_MASK),
                  DefaultEditorKit.previousWordAction),
          new JTextComponent.KeyBinding(
                  KeyStroke.getKeyStroke(KeyEvent.VK_LESS, InputEvent.ALT_MASK),
                  DefaultEditorKit.beginAction),
          new JTextComponent.KeyBinding(
                  KeyStroke.getKeyStroke(KeyEvent.VK_LESS, InputEvent.ALT_MASK + InputEvent.SHIFT_MASK),
                  DefaultEditorKit.endAction),
          new JTextComponent.KeyBinding(
                  KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.ALT_MASK),
                  DefaultEditorKit.nextWordAction),
          new JTextComponent.KeyBinding(
                  KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_MASK),
                  DefaultEditorKit.forwardAction),
          new JTextComponent.KeyBinding(
                  KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_MASK),
                  DefaultEditorKit.backwardAction),
          new JTextComponent.KeyBinding(KeyStroke.getKeyStroke(
                  KeyEvent.VK_V, InputEvent.CTRL_MASK),
                  DefaultEditorKit.pageDownAction),
          new JTextComponent.KeyBinding(
                  KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.ALT_MASK),
                  DefaultEditorKit.pageUpAction),
  };

  /**
   * Activates Emacs keybindings for <code>JTextArea</code>,
   * <code>JTextPane</code>, <code>JTextField</code> and
   * <code>JEditorPane</code>.
   */
  public static void loadEmacsKeyBindings() {
    JTextComponent[] jtcs = {new JTextArea(), new JTextPane(), new JTextField(), new JEditorPane()};

    for (JTextComponent jtc : jtcs) {
      Keymap k = jtc.getKeymap();

      JTextComponent.loadKeymap(k, EMACS_KEY_BINDINGS, jtc.getActions());

      k.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_D, InputEvent.ALT_MASK),
              new KillWordAction(killWordAction));
      k.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_SPACE, InputEvent.ALT_MASK),
              new BackwardKillWordAction(backwardKillWordAction));
      k.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, InputEvent.CTRL_MASK),
              new SetMarkCommandAction(setMarkCommandAction));
      k.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.ALT_MASK),
              new KillRingSaveAction(killRingSaveAction));
      k.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_W, InputEvent.CTRL_MASK),
              new KillRegionAction(killRegionAction));
      k.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_MASK),
              new KillLineAction(killLineAction));
      k.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_MASK),
              new YankAction("emacs-yank"));
      k.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.ALT_MASK),
              new YankPopAction("emacs-yank-pop"));
      k.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.ALT_MASK),
              new CapitalizeWordAction(capitalizeWordAction));
      k.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.ALT_MASK),
              new DowncaseWordAction(downcaseWordAction));
      k.addActionForKeyStroke(KeyStroke.getKeyStroke(KeyEvent.VK_U, InputEvent.ALT_MASK),
              new UpcaseWordAction(upcaseWordAction));
    }
  }

  //--- Inner Class(es) ---

  public static class KillWordAction extends TextAction {
    KillWordAction(String nm) {
      super(nm);
    }

    public void actionPerformed(ActionEvent e) {
      JTextComponent jtc = getTextComponent(e);
      if (jtc != null) {
        try {
          int offs = jtc.getCaretPosition();
          jtc.setSelectionStart(offs);
          offs = Utilities.getNextWord(jtc, offs);
          jtc.setSelectionEnd(offs);
          jtc.cut();
        }
        catch (BadLocationException ble) {
          jtc.getToolkit().beep();
        }
      }
    }
  }

  public static class BackwardKillWordAction extends TextAction {
    BackwardKillWordAction(String nm) {
      super(nm);
    }

    public void actionPerformed(ActionEvent e) {
      JTextComponent jtc = getTextComponent(e);
      if (jtc != null) {
        try {
          int offs = jtc.getCaretPosition();
          jtc.setSelectionEnd(offs);
          offs = Utilities.getPreviousWord(jtc, offs);
          jtc.setSelectionStart(offs);
          jtc.cut();
        }
        catch (BadLocationException ble) {
          jtc.getToolkit().beep();
        }
      }
    }
  }

  public static class KillRingSaveAction extends TextAction {
    KillRingSaveAction(String nm) {
      super(nm);
    }

    public void actionPerformed(ActionEvent e) {
      JTextComponent jtc = getTextComponent(e);
      if (jtc != null && SetMarkCommandAction.isMarked(jtc)) {
        jtc.setSelectionStart(SetMarkCommandAction.getCaretPosition());
        jtc.moveCaretPosition(jtc.getCaretPosition());
        jtc.copy();
        YankAction.add(jtc.getSelectedText());
        SetMarkCommandAction.reset();
        // todo reset selection
      }
    }
  }

  public static class KillRegionAction extends TextAction {
    KillRegionAction(String nm) {
      super(nm);
    }

    public void actionPerformed(ActionEvent e) {
      JTextComponent jtc = getTextComponent(e);
      if (jtc != null && SetMarkCommandAction.isMarked(jtc)) {
        jtc.setSelectionStart(SetMarkCommandAction.getCaretPosition());
        jtc.moveCaretPosition(jtc.getCaretPosition());
        SetMarkCommandAction.reset();
        YankAction.add(jtc.getSelectedText());
        jtc.cut();
      }
    }
  }

  public static class KillLineAction extends TextAction {
    KillLineAction(String nm) {
      super(nm);
    }

    public void actionPerformed(ActionEvent e) {
      JTextComponent jtc = getTextComponent(e);
      if (jtc != null) {
        try {
          int start = jtc.getCaretPosition();
          int end = Utilities.getRowEnd(jtc, start);
          if (start == end && jtc.isEditable()) {
            Document doc = jtc.getDocument();
            doc.remove(end, 1);
          } else {
            jtc.setSelectionStart(start);
            jtc.setSelectionEnd(end);
            YankAction.add(jtc.getSelectedText());
            jtc.cut();
            // jtc.replaceSelection("");
          }
        }
        catch (BadLocationException ble) {
          jtc.getToolkit().beep();
        }
      }
    }
  }

  public static class SetMarkCommandAction extends TextAction {
    private static int position = -1;
    private static JTextComponent jtc;

    SetMarkCommandAction(String nm) {
      super(nm);
    }

    public void actionPerformed(ActionEvent e) {
      jtc = getTextComponent(e);
      if (jtc != null) {
        position = jtc.getCaretPosition();
      }
    }

    public static boolean isMarked(JTextComponent jt) {
      return (jtc == jt && position != -1);
    }

    public static void reset() {
      jtc = null;
      position = -1;
    }

    public static int getCaretPosition() {
      return position;
    }
  }

  public static class YankAction extends TextAction {
    public static LinkedList<String> killring = new LinkedList<String>();
    public static int start = -1;
    public static int end = -1;

    public YankAction(String nm) {
      super(nm);
    }

    public void actionPerformed(ActionEvent event) {

      JTextComponent jtc = getTextComponent(event);

      if (jtc != null) {
        start = jtc.getCaretPosition();
        jtc.paste();
        end = jtc.getCaretPosition();
        try {
          add(jtc.getText(start, end));
        } catch (BadLocationException e) {
          e.printStackTrace();
        }
      }
    }


    /**
     * Uniquely adds <code>item</code> to killring, i.e. if
     * <code>item</code> is already in killring, it's moved to the front,
     * otherwise it's added as first element.
     */
    public static void add(String item) {
      for (Iterator i = killring.iterator(); i.hasNext();) {
        if (item.equals(i.next())) {
          i.remove();
          break;
        }
      }
      killring.addFirst(item);
    }

    /**
     * Returns killring successor of <code>item</code> and adds
     * <code>item</code> to killring.
     *
     * @param predecessor
     * @return Returns first item if <code>item == null</code>.
     */
    public static String getNext(String predecessor) {
      if (killring.size() == 0) {
        return null;
      }

      if (predecessor == null) {
        return killring.getFirst();
      }

      for (Iterator i = killring.iterator(); i.hasNext();) {
        if (predecessor.equals(i.next())) {
          i.remove();
          if (i.hasNext()) {
            String result = (String) i.next();
            killring.addFirst(predecessor);
            return result;
          } else {
            break;
          }
        }
      }

      String result = killring.getFirst();
      killring.addFirst(predecessor);
      return result;
    }
  }

  public static class YankPopAction extends TextAction {

    public YankPopAction(String nm) {
      super(nm);
    }

    public void actionPerformed(ActionEvent event) {
      JTextComponent jtc = getTextComponent(event);

      if (jtc != null && YankAction.killring.size() > 0) {
        jtc.setSelectionStart(YankAction.start);
        jtc.setSelectionEnd(YankAction.end);
        String toYank = YankAction.getNext(jtc.getSelectedText());
        if (toYank != null) {
          jtc.replaceSelection(toYank);
          YankAction.end = jtc.getCaretPosition();
        } else {
          jtc.getToolkit().beep();
        }
      }
    }
  }

  /**
   *
   */
  public static class CapitalizeWordAction extends TextAction {
    public CapitalizeWordAction(String nm) {
      super(nm);
    }

    /**
     * At first the same code as in {@link DowncaseWordAction} is
     * performed, to ensure the word is in lower case, then the first
     * letter is capialized.
     */
    public void actionPerformed(ActionEvent event) {
      JTextComponent jtc = getTextComponent(event);

      if (jtc != null) {
        try {
          /* downcase code */
          int start = jtc.getCaretPosition();
          int end = Utilities.getNextWord(jtc, start);
          jtc.setSelectionStart(start);
          jtc.setSelectionEnd(end);
          String word = jtc.getText(start, end - start);
          jtc.replaceSelection(word.toLowerCase());

          /* actual capitalize code */
          int offs = Utilities.getWordStart(jtc, start);
          // get first letter
          String c = jtc.getText(offs, 1);
          // we're at the end of the previous word
          if (c.equals(" ")) {
            /* ugly java workaround to get the beginning of the
          word.  */
            offs = Utilities.getWordStart(jtc, ++offs);
            c = jtc.getText(offs, 1);
          }
          if (Character.isLetter(c.charAt(0))) {
            jtc.setSelectionStart(offs);
            jtc.setSelectionEnd(offs + 1);
            jtc.replaceSelection(c.toUpperCase());
          }
          end = Utilities.getWordEnd(jtc, offs);
          jtc.setCaretPosition(end);
        }
        catch (BadLocationException ble) {
          jtc.getToolkit().beep();
        }
      }
    }
  }

  public static class DowncaseWordAction extends TextAction {
    public DowncaseWordAction(String nm) {
      super(nm);
    }

    public void actionPerformed(ActionEvent event) {
      JTextComponent jtc = getTextComponent(event);

      if (jtc != null) {
        try {
          int start = jtc.getCaretPosition();
          int end = Utilities.getNextWord(jtc, start);
          jtc.setSelectionStart(start);
          jtc.setSelectionEnd(end);
          String word = jtc.getText(start, end - start);
          jtc.replaceSelection(word.toLowerCase());
        }
        catch (BadLocationException ble) {
          jtc.getToolkit().beep();
        }
      }
    }
  }

  public static class UpcaseWordAction extends TextAction {
    public UpcaseWordAction(String nm) {
      super(nm);
    }

    public void actionPerformed(ActionEvent event) {
      JTextComponent jtc = getTextComponent(event);

      if (jtc != null) {
        try {
          int start = jtc.getCaretPosition();
          int end = Utilities.getNextWord(jtc, start);
          jtc.setSelectionStart(start);
          jtc.setSelectionEnd(end);
          String word = jtc.getText(start, end - start);
          jtc.replaceSelection(word.toUpperCase());
        }
        catch (BadLocationException ble) {
          jtc.getToolkit().beep();
        }
      }
    }
  }
}
