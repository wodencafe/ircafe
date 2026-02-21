package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.ui.settings.ChatThemeSettings;
import cafe.woden.ircclient.ui.settings.ChatThemeSettingsBus;
import java.awt.Color;
import javax.swing.UIManager;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
@Lazy
public class ChatStyles {

  private final ChatThemeSettingsBus chatThemeSettings;

  public static final String ATTR_URL = "chat.url";
  public static final String ATTR_CHANNEL = "chat.channel";
  public static final String ATTR_MSG_REF = "chat.msgRef";
  public static final String ATTR_STYLE = "chat.style";

  // mIRC formatting metadata (preserved across restyle/theme changes)
  public static final String ATTR_IRC_BOLD = "chat.irc.bold";
  public static final String ATTR_IRC_ITALIC = "chat.irc.italic";
  public static final String ATTR_IRC_UNDERLINE = "chat.irc.underline";
  public static final String ATTR_IRC_REVERSE = "chat.irc.reverse";
  /** Integer foreground mIRC color code (0-15), or null for default. */
  public static final String ATTR_IRC_FG = "chat.irc.fg";
  public static final String ATTR_OVERRIDE_FG = "chat.override.fg";
  
  public static final String ATTR_OUTGOING = "chat.outgoing";

  // Line metadata (for filters / inspector UI)
  public static final String ATTR_META_BUFFER_KEY = "chat.meta.bufferKey";
  public static final String ATTR_META_KIND = "chat.meta.kind";
  public static final String ATTR_META_DIRECTION = "chat.meta.direction";
  public static final String ATTR_META_FROM = "chat.meta.from";
  public static final String ATTR_META_TAGS = "chat.meta.tags";
  public static final String ATTR_META_EPOCH_MS = "chat.meta.epochMs";
  public static final String ATTR_META_MSGID = "chat.meta.msgid";
  public static final String ATTR_META_IRCV3_TAGS = "chat.meta.ircv3.tags";
  public static final String ATTR_META_PENDING_ID = "chat.meta.pending.id";
  public static final String ATTR_META_PENDING_STATE = "chat.meta.pending.state";
  public static final String ATTR_META_FILTER_RULE_ID = "chat.meta.filter.ruleId";
  public static final String ATTR_META_FILTER_RULE_NAME = "chat.meta.filter.ruleName";
  public static final String ATTR_META_FILTER_ACTION = "chat.meta.filter.action";
  public static final String ATTR_META_FILTER_MULTIPLE = "chat.meta.filter.multiple";
  /** Integer background mIRC color code (0-15), or null for default. */
  public static final String ATTR_IRC_BG = "chat.irc.bg";

  public static final String STYLE_TIMESTAMP = "timestamp";
  public static final String STYLE_FROM = "from";
  public static final String STYLE_MESSAGE = "message";
  public static final String STYLE_NOTICE_FROM = "noticeFrom";
  public static final String STYLE_NOTICE_MESSAGE = "noticeMessage";
  public static final String STYLE_STATUS = "status";
  public static final String STYLE_PRESENCE = "presence";
  public static final String STYLE_ERROR = "error";
  public static final String STYLE_LINK = "link";
  public static final String STYLE_MENTION = "mention";
  public static final String STYLE_ACTION_FROM = "actionFrom";
  public static final String STYLE_ACTION_MESSAGE = "actionMessage";

  private SimpleAttributeSet tsStyle;
  private SimpleAttributeSet fromStyle;
  private SimpleAttributeSet msgStyle;
  private SimpleAttributeSet noticeFromStyle;
  private SimpleAttributeSet noticeMsgStyle;
  private SimpleAttributeSet statusStyle;
  private SimpleAttributeSet presenceStyle;
  private SimpleAttributeSet errorStyle;
  private SimpleAttributeSet linkStyle;
  private SimpleAttributeSet mentionStyle;
  private SimpleAttributeSet actionFromStyle;
  private SimpleAttributeSet actionMsgStyle;

  public ChatStyles(ChatThemeSettingsBus chatThemeSettings) {
    this.chatThemeSettings = chatThemeSettings;
    reload();
  }

  
  public synchronized void reload() {
    Color fg = UIManager.getColor("TextPane.foreground");
    Color dim = UIManager.getColor("Label.disabledForeground");
    Color bg = UIManager.getColor("TextPane.background");

    if (fg == null) fg = UIManager.getColor("Label.foreground");
    if (bg == null) bg = UIManager.getColor("Panel.background");

    Color link = UIManager.getColor("Component.linkColor");
    if (link == null) link = UIManager.getColor("Label.foreground");

    // Theme-aware warning/error colors. FlatLaf exposes various keys depending on component type
    // and whether we're using outline/border colors. Try several common ones before falling back.
    Color warn = firstNonNull(
        UIManager.getColor("Component.warningColor"),
        UIManager.getColor("Component.warning.outlineColor"),
        UIManager.getColor("Component.warning.borderColor"),
        UIManager.getColor("Component.warning.focusedBorderColor"),
        UIManager.getColor("Component.warning.focusColor")
    );
    Color err = firstNonNull(
        UIManager.getColor("Component.errorColor"),
        UIManager.getColor("Component.error.outlineColor"),
        UIManager.getColor("Component.error.borderColor"),
        UIManager.getColor("Component.error.focusedBorderColor"),
        UIManager.getColor("Component.error.focusColor")
    );
    if (warn == null) warn = new Color(0xF0B000);
    if (err == null) err = new Color(0xD05050);

    Color selBg = UIManager.getColor("TextPane.selectionBackground");

    ChatThemeSettings s = chatThemeSettings != null ? chatThemeSettings.get() : null;
    ChatThemeSettings.Preset preset = s != null ? s.preset() : ChatThemeSettings.Preset.DEFAULT;

    // Best-effort "accent" fallback (used by ACCENTED/HIGH_CONTRAST presets)
    Color accent = UIManager.getColor("@accentColor");
    if (accent == null) accent = UIManager.getColor("Component.focusColor");
    if (accent == null) accent = link;

    int mentionStrength = s != null ? s.mentionStrength() : 35;
    double mentionWeight = Math.max(0.0, Math.min(1.0, mentionStrength / 100.0));

    // Base colors derived from preset.
    Color tsFg = dim;
    Color sysFg = dim;
    // Mention base: prefer selection background (theme-native). If not available, prefer accent.
    // Avoid hard-coded colors where possible to prevent clashes with theme pack themes.
    Color mentionBase = selBg != null ? selBg : accent;

    switch (preset) {
      case SOFT -> {
        tsFg = mix(dim, bg, 0.55);
        sysFg = mix(dim, bg, 0.50);
        mentionBase = mix(mentionBase, bg, 0.65);
      }
      case ACCENTED -> {
        tsFg = mix(accent, fg, 0.45);
        sysFg = mix(accent, dim != null ? dim : fg, 0.25);
        mentionBase = mix(accent, bg, 0.25);
      }
      case HIGH_CONTRAST -> {
        tsFg = fg;
        sysFg = fg;
        mentionBase = selBg != null ? selBg : accent;
      }
      default -> {
        // DEFAULT
      }
    }

    // Explicit overrides win.
    if (s != null && s.timestampColor() != null) {
      Color o = parseHexColor(s.timestampColor());
      if (o != null) tsFg = o;
    }
    if (s != null && s.systemColor() != null) {
      Color o = parseHexColor(s.systemColor());
      if (o != null) sysFg = o;
    }

    Color mentionBg;
    if (s != null && s.mentionBgColor() != null) {
      Color o = parseHexColor(s.mentionBgColor());
      mentionBg = o != null ? o : mix(mentionBase, bg, mentionWeight);
    } else {
      // Mention highlight: a subtle background derived from the preset.
      mentionBg = mix(mentionBase, bg, mentionWeight);
    }

    Color mentionFg = fg;
    if (mentionFg != null && mentionBg != null) {
      double cr = contrastRatio(mentionFg, mentionBg);
      if (cr > 0.0 && cr < 3.0) {
        mentionFg = bestTextColor(mentionBg, mentionFg);
      }
    }

    tsStyle = attrs(STYLE_TIMESTAMP, tsFg, bg, false, false);
    fromStyle = attrs(STYLE_FROM, fg, bg, true, false);
    msgStyle = attrs(STYLE_MESSAGE, fg, bg, false, false);
    noticeFromStyle = attrs(STYLE_NOTICE_FROM, warn, bg, true, false);
    noticeMsgStyle = attrs(STYLE_NOTICE_MESSAGE, warn, bg, false, false);
    statusStyle = attrs(STYLE_STATUS, sysFg, bg, false, true);
    presenceStyle = attrs(STYLE_PRESENCE, sysFg, bg, false, true);
    errorStyle = attrs(STYLE_ERROR, err, bg, true, false);

    linkStyle = attrs(STYLE_LINK, link, bg, false, false);
    StyleConstants.setUnderline(linkStyle, true);

    mentionStyle = attrs(STYLE_MENTION, mentionFg, mentionBg, false, false);

    // /me ACTION lines
    actionFromStyle = attrs(STYLE_ACTION_FROM, fg, bg, true, true);
    actionMsgStyle = attrs(STYLE_ACTION_MESSAGE, fg, bg, false, true);
  }

  public AttributeSet timestamp() { return tsStyle; }
  public AttributeSet from() { return fromStyle; }
  public AttributeSet message() { return msgStyle; }
  public AttributeSet noticeFrom() { return noticeFromStyle; }
  public AttributeSet noticeMessage() { return noticeMsgStyle; }
  public AttributeSet status() { return statusStyle; }
  public AttributeSet presence() { return presenceStyle; }
  public AttributeSet error() { return errorStyle; }
  public AttributeSet link() { return linkStyle; }
  public AttributeSet mention() { return mentionStyle; }
  public AttributeSet actionFrom() { return actionFromStyle; }
  public AttributeSet actionMessage() { return actionMsgStyle; }

  public AttributeSet byStyleId(String id) {
    if (id == null) return msgStyle;
    return switch (id) {
      case STYLE_TIMESTAMP -> tsStyle;
      case STYLE_FROM -> fromStyle;
      case STYLE_MESSAGE -> msgStyle;
      case STYLE_NOTICE_FROM -> noticeFromStyle;
      case STYLE_NOTICE_MESSAGE -> noticeMsgStyle;
      case STYLE_STATUS -> statusStyle;
      case STYLE_PRESENCE -> presenceStyle;
      case STYLE_ERROR -> errorStyle;
      case STYLE_LINK -> linkStyle;
      case STYLE_MENTION -> mentionStyle;
      case STYLE_ACTION_FROM -> actionFromStyle;
      case STYLE_ACTION_MESSAGE -> actionMsgStyle;
      default -> msgStyle;
    };
  }

  private static SimpleAttributeSet attrs(String styleId, Color fg, Color bg, boolean bold, boolean italic) {
    SimpleAttributeSet a = new SimpleAttributeSet();
    a.addAttribute(ATTR_STYLE, styleId);

    if (fg != null) {
      StyleConstants.setForeground(a, fg);
    }
    if (bg != null) {
      StyleConstants.setBackground(a, bg);
    }

    StyleConstants.setBold(a, bold);
    StyleConstants.setItalic(a, italic);
    return a;
  }

  private static Color mix(Color a, Color b, double aWeight) {
    if (a == null) return b;
    if (b == null) return a;

    double bw = 1.0 - aWeight;
    int r = clamp((int) Math.round(a.getRed() * aWeight + b.getRed() * bw));
    int g = clamp((int) Math.round(a.getGreen() * aWeight + b.getGreen() * bw));
    int bl = clamp((int) Math.round(a.getBlue() * aWeight + b.getBlue() * bw));
    return new Color(r, g, bl);
  }

  private static int clamp(int v) {
    return Math.max(0, Math.min(255, v));
  }

  private static Color parseHexColor(String raw) {
    if (raw == null) return null;
    String s = raw.trim();
    if (s.isEmpty()) return null;
    if (s.startsWith("#")) s = s.substring(1);
    if (s.startsWith("0x") || s.startsWith("0X")) s = s.substring(2);
    if (s.length() == 3) {
      // #RGB -> #RRGGBB
      char r = s.charAt(0);
      char g = s.charAt(1);
      char b = s.charAt(2);
      s = "" + r + r + g + g + b + b;
    }
    if (s.length() != 6) return null;
    try {
      int rgb = Integer.parseInt(s, 16);
      return new Color(rgb);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static Color bestTextColor(Color bg, Color currentFg) {
    if (bg == null) return currentFg != null ? currentFg : Color.BLACK;

    Color black = Color.BLACK;
    Color white = Color.WHITE;

    double crBlack = contrastRatio(black, bg);
    double crWhite = contrastRatio(white, bg);

    Color best = crBlack >= crWhite ? black : white;
    double bestCr = Math.max(crBlack, crWhite);

    if (currentFg != null) {
      double crCur = contrastRatio(currentFg, bg);
      if (crCur >= bestCr) return currentFg;
    }
    return best;
  }

  private static double contrastRatio(Color fg, Color bg) {
    if (fg == null || bg == null) return 0.0;

    double l1 = relativeLuminance(fg);
    double l2 = relativeLuminance(bg);
    if (l1 < l2) {
      double t = l1;
      l1 = l2;
      l2 = t;
    }
    return (l1 + 0.05) / (l2 + 0.05);
  }

  private static double relativeLuminance(Color c) {
    double r = srgbToLinear(c.getRed());
    double g = srgbToLinear(c.getGreen());
    double b = srgbToLinear(c.getBlue());
    return (0.2126 * r) + (0.7152 * g) + (0.0722 * b);
  }

  private static double srgbToLinear(int channel) {
    double v = channel / 255.0;
    return (v <= 0.04045) ? (v / 12.92) : Math.pow((v + 0.055) / 1.055, 2.4);
  }

  @SafeVarargs
  private static <T> T firstNonNull(T... values) {
    if (values == null) return null;
    for (T v : values) {
      if (v != null) return v;
    }
    return null;
  }
}
