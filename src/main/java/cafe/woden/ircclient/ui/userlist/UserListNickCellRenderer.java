package cafe.woden.ircclient.ui.userlist;

import cafe.woden.ircclient.irc.IrcEvent.AccountState;
import cafe.woden.ircclient.irc.IrcEvent.AwayState;
import cafe.woden.ircclient.irc.IrcEvent.NickInfo;
import cafe.woden.ircclient.ui.chat.NickColorService;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.icons.SvgIcons.Palette;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.JLabel;
import javax.swing.JList;

public final class UserListNickCellRenderer extends DefaultListCellRenderer {
  private static final int ACCOUNT_ICON_SIZE = 10;
  private static final int TYPING_ICON_SIZE = 12;
  private static final int TYPING_ICON_RIGHT_PAD = 6;

  private final NickColorService nickColors;
  private final Function<NickInfo, IgnoreMark> ignoreMarkLookup;
  private final Function<String, Float> typingAlphaLookup;
  private final Predicate<String> pausedTypingLookup;
  private final Icon keyboardIcon = SvgIcons.icon("keyboard", TYPING_ICON_SIZE, Palette.QUIET);

  private float typingIconAlpha = 0f;

  public record IgnoreMark(boolean ignore, boolean softIgnore) {}

  public UserListNickCellRenderer(
      NickColorService nickColors,
      Function<NickInfo, IgnoreMark> ignoreMarkLookup,
      Function<String, Float> typingAlphaLookup,
      Predicate<String> pausedTypingLookup) {
    this.nickColors = nickColors;
    this.ignoreMarkLookup =
        ignoreMarkLookup != null ? ignoreMarkLookup : __ -> new IgnoreMark(false, false);
    this.typingAlphaLookup = typingAlphaLookup != null ? typingAlphaLookup : __ -> 0f;
    this.pausedTypingLookup = pausedTypingLookup != null ? pausedTypingLookup : __ -> false;
  }

  @Override
  public java.awt.Component getListCellRendererComponent(
      JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
    JLabel label =
        (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    NickInfo nickInfo = (value instanceof NickInfo n) ? n : null;

    String nick = nickInfo == null ? "" : Objects.toString(nickInfo.nick(), "");
    String prefix = nickInfo == null ? "" : Objects.toString(nickInfo.prefix(), "");
    String display = prefix + nick;

    IgnoreMark mark = ignoreMarkLookup.apply(nickInfo);
    if (mark == null) {
      mark = new IgnoreMark(false, false);
    }
    AwayState away =
        (nickInfo == null || nickInfo.awayState() == null)
            ? AwayState.UNKNOWN
            : nickInfo.awayState();
    AccountState account =
        (nickInfo == null || nickInfo.accountState() == null)
            ? AccountState.UNKNOWN
            : nickInfo.accountState();

    label.setIcon(accountIcon(account));
    label.setIconTextGap(6);
    if (mark.ignore()) display += "  [IGN]";
    if (mark.softIgnore()) display += "  [SOFT]";
    label.setText(display);

    Font font = label.getFont();
    int style = font.getStyle();
    if (mark.ignore()) style |= Font.BOLD;
    if (mark.softIgnore()) style |= Font.ITALIC;
    if (away == AwayState.AWAY) style |= Font.ITALIC;
    label.setFont(font.deriveFont(style));

    if (!nick.isBlank() && nickColors != null && nickColors.enabled()) {
      Color fg = nickColors.colorForNick(nick, label.getBackground(), label.getForeground());
      label.setForeground(fg);
    }

    float alpha = typingAlphaLookup.apply(nick);
    if (pausedTypingLookup.test(nick)) {
      alpha = Math.min(alpha, 0.4f);
    }
    typingIconAlpha = alpha;

    int rightPad =
        (keyboardIcon == null) ? 2 : (keyboardIcon.getIconWidth() + TYPING_ICON_RIGHT_PAD + 2);
    label.setBorder(BorderFactory.createEmptyBorder(1, 2, 1, rightPad));
    return label;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    if (keyboardIcon == null || typingIconAlpha <= 0.01f) return;

    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setComposite(AlphaComposite.SrcOver.derive(Math.max(0f, Math.min(1f, typingIconAlpha))));
      int x = Math.max(0, getWidth() - keyboardIcon.getIconWidth() - TYPING_ICON_RIGHT_PAD);
      int y = Math.max(0, (getHeight() - keyboardIcon.getIconHeight()) / 2);
      keyboardIcon.paintIcon(this, g2, x, y);
    } finally {
      g2.dispose();
    }
  }

  private static Icon accountIcon(AccountState state) {
    AccountState current = state != null ? state : AccountState.UNKNOWN;
    return switch (current) {
      case LOGGED_IN -> SvgIcons.icon("account-in", ACCOUNT_ICON_SIZE, Palette.QUIET);
      case LOGGED_OUT -> SvgIcons.icon("account-out", ACCOUNT_ICON_SIZE, Palette.QUIET);
      default -> SvgIcons.icon("account-unknown", ACCOUNT_ICON_SIZE, Palette.QUIET);
    };
  }
}
