package cafe.woden.ircclient.ui.util;

import cafe.woden.ircclient.ui.chat.ChatStyles;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.Document;
import javax.swing.text.Element;
import javax.swing.text.StyledDocument;
import javax.swing.text.JTextComponent;

/**
 * Simple line inspector for chat transcripts.
 *
 * <p>Relies on per-line metadata attributes stored on inserted document runs.
 */
public final class ChatLineInspectorDialog {

  private ChatLineInspectorDialog() {
  }

  public static void showAtPoint(Component owner, JTextComponent transcript, Point viewPoint) {
    if (transcript == null || viewPoint == null) return;

    // Convert the popup point to the transcript's coordinate system (defensive when invoked from nested components).
    Point p = new Point(viewPoint);
    SwingUtilities.convertPointToScreen(p, transcript);
    SwingUtilities.convertPointFromScreen(p, transcript);

    int pos;
    try {
      pos = transcript.viewToModel2D(p);
    } catch (Exception e) {
      try {
        pos = transcript.viewToModel(p);
      } catch (Exception ignored) {
        return;
      }
    }
    showAtPosition(owner, transcript, pos);
  }

  public static void showAtPosition(Component owner, JTextComponent transcript, int modelPos) {
    if (transcript == null) return;

    Document doc = transcript.getDocument();
    if (doc == null) return;

    String text = extractParagraphText(doc, modelPos);

    AttributeSet attrs = null;
    if (doc instanceof StyledDocument sd) {
      try {
        int safePos = Math.max(0, Math.min(modelPos, Math.max(0, sd.getLength() - 1)));
        attrs = sd.getCharacterElement(safePos).getAttributes();
      } catch (Exception ignored) {
        attrs = null;
      }
    } else {
      try {
        Element root = doc.getDefaultRootElement();
        int idx = root.getElementIndex(modelPos);
        Element el = root.getElement(idx);
        if (el != null) attrs = el.getAttributes();
      } catch (Exception ignored) {
        attrs = null;
      }
    }

    String info = format(attrs, text);

    JTextArea area = new JTextArea(info);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setCaretPosition(0);
    try {
      area.setFont(defaultMonospaceFont());
    } catch (Exception ignored) {
    }

    JScrollPane scroll = new JScrollPane(area);
    Component anchor = owner != null ? owner : transcript;
    Window ownerWindow = anchor != null ? SwingUtilities.getWindowAncestor(anchor) : null;
    Component dialogAnchor = ownerWindow != null ? ownerWindow : anchor;

    Dimension preferred = preferredDialogSize(dialogAnchor);
    scroll.setPreferredSize(preferred);

    JOptionPane pane = new JOptionPane(
        scroll,
        JOptionPane.INFORMATION_MESSAGE,
        JOptionPane.DEFAULT_OPTION
    );
    JDialog dialog = pane.createDialog(dialogAnchor, "Line Inspector");
    dialog.setResizable(true);
    dialog.setMinimumSize(new Dimension(Math.min(520, preferred.width), Math.min(300, preferred.height)));
    dialog.pack();
    dialog.setLocationRelativeTo(dialogAnchor);
    dialog.setVisible(true);
  }

  private static Font defaultMonospaceFont() {
    Font base = UIManager.getFont("TextArea.font");
    if (base == null) base = UIManager.getFont("TextPane.font");
    if (base == null) base = UIManager.getFont("Label.font");
    int size = base != null ? base.getSize() : 12;
    return new Font(Font.MONOSPACED, Font.PLAIN, Math.max(10, size));
  }

  private static Dimension preferredDialogSize(Component anchor) {
    int availableWidth = 1280;
    int availableHeight = 720;

    GraphicsConfiguration gc = anchor != null ? anchor.getGraphicsConfiguration() : null;
    if (gc == null) {
      try {
        gc = GraphicsEnvironment
            .getLocalGraphicsEnvironment()
            .getDefaultScreenDevice()
            .getDefaultConfiguration();
      } catch (Exception ignored) {
        gc = null;
      }
    }

    if (gc != null) {
      try {
        Rectangle bounds = gc.getBounds();
        Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(gc);
        availableWidth = Math.max(320, bounds.width - insets.left - insets.right);
        availableHeight = Math.max(240, bounds.height - insets.top - insets.bottom);
      } catch (Exception ignored) {
      }
    }

    int preferredWidth = Math.min(760, Math.max(560, availableWidth - 220));
    int preferredHeight = Math.min(520, Math.max(340, availableHeight - 260));

    preferredWidth = Math.min(preferredWidth, Math.max(320, availableWidth - 80));
    preferredHeight = Math.min(preferredHeight, Math.max(240, availableHeight - 100));

    return new Dimension(preferredWidth, preferredHeight);
  }

  private static String extractParagraphText(Document doc, int pos) {
    try {
      Element root = doc.getDefaultRootElement();
      int idx = root.getElementIndex(pos);
      Element para = root.getElement(idx);
      if (para == null) return "";
      int start = para.getStartOffset();
      int end = para.getEndOffset();
      int len = Math.max(0, end - start);
      if (len == 0) return "";
      String t = doc.getText(start, len);
      // Trim trailing newline for display.
      if (t.endsWith("\n")) t = t.substring(0, t.length() - 1);
      // Avoid silly huge dumps.
      if (t.length() > 600) t = t.substring(0, 599) + "…";
      return t;
    } catch (Exception ignored) {
      return "";
    }
  }

  private static String format(AttributeSet attrs, String paragraphText) {
    String bufferKey = getString(attrs, ChatStyles.ATTR_META_BUFFER_KEY);
    String kind = getString(attrs, ChatStyles.ATTR_META_KIND);
    String dir = getString(attrs, ChatStyles.ATTR_META_DIRECTION);
    String from = getString(attrs, ChatStyles.ATTR_META_FROM);
    String tags = getString(attrs, ChatStyles.ATTR_META_TAGS);
    String msgId = getString(attrs, ChatStyles.ATTR_META_MSGID);
    String ircv3Tags = getString(attrs, ChatStyles.ATTR_META_IRCV3_TAGS);

    String filterName = getString(attrs, ChatStyles.ATTR_META_FILTER_RULE_NAME);
    String filterId = getString(attrs, ChatStyles.ATTR_META_FILTER_RULE_ID);
    String filterAction = getString(attrs, ChatStyles.ATTR_META_FILTER_ACTION);
    Boolean filterMultiple = getBoolean(attrs, ChatStyles.ATTR_META_FILTER_MULTIPLE);

    Long epochMs = getLong(attrs, ChatStyles.ATTR_META_EPOCH_MS);

    StringBuilder sb = new StringBuilder();

    if (!bufferKey.isBlank()) sb.append("Buffer: ").append(bufferKey).append('\n');
    if (!kind.isBlank()) sb.append("Kind: ").append(kind).append('\n');
    if (!dir.isBlank()) sb.append("Direction: ").append(dir).append('\n');
    if (!from.isBlank()) sb.append("From: ").append(from).append('\n');

    if (!tags.isBlank()) sb.append("Tags: ").append(tags).append('\n');
    if (!msgId.isBlank()) sb.append("Message ID: ").append(msgId).append('\n');
    if (!ircv3Tags.isBlank()) sb.append("IRCv3 tags: ").append(ircv3Tags).append('\n');

    if (!filterName.isBlank()) {
      sb.append("Matched filter: ").append(filterName);
      if (!filterId.isBlank()) sb.append(" (id=").append(filterId).append(")");
      if (!filterAction.isBlank()) sb.append(" action=").append(filterAction);
      sb.append('\n');
    }
    if (Boolean.TRUE.equals(filterMultiple) && filterName.isBlank()) {
      sb.append("Matched filter: (multiple)\n");
    }
    if (filterMultiple != null) {
      sb.append("Multiple matches: ").append(filterMultiple).append('\n');
    }

    if (epochMs != null && epochMs > 0) {
      try {
        var z = ZoneId.systemDefault();
        var dt = Instant.ofEpochMilli(epochMs).atZone(z);
        String pretty = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.ROOT).format(dt);
        sb.append("Time: ").append(pretty).append(" (epochMs=").append(epochMs).append(')').append('\n');
      } catch (Exception e) {
        sb.append("Time: epochMs=").append(epochMs).append('\n');
      }
    }

    if (sb.length() == 0) {
      sb.append("(No metadata for this line.)\n");
    }

    if (paragraphText != null && !paragraphText.isBlank()) {
      sb.append('\n').append("Text:\n").append(paragraphText).append('\n');
    }

    return sb.toString();
  }

  private static String getString(AttributeSet attrs, String key) {
    if (attrs == null || key == null) return "";
    try {
      Object v = attrs.getAttribute(key);
      return Objects.toString(v, "").trim();
    } catch (Exception ignored) {
      return "";
    }
  }

  private static Boolean getBoolean(AttributeSet attrs, String key) {
    if (attrs == null || key == null) return null;
    try {
      Object v = attrs.getAttribute(key);
      if (v instanceof Boolean b) return b;
      String s = Objects.toString(v, "").trim();
      if (s.isEmpty()) return null;
      if ("true".equalsIgnoreCase(s)) return Boolean.TRUE;
      if ("false".equalsIgnoreCase(s)) return Boolean.FALSE;
      return null;
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Long getLong(AttributeSet attrs, String key) {
    if (attrs == null || key == null) return null;
    try {
      Object v = attrs.getAttribute(key);
      if (v instanceof Long l) return l;
      if (v instanceof Number n) return n.longValue();
      String s = Objects.toString(v, "").trim();
      if (s.isEmpty()) return null;
      return Long.parseLong(s);
    } catch (Exception ignored) {
      return null;
    }
  }
}
