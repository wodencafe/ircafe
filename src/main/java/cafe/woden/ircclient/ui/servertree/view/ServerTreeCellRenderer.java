package cafe.woden.ircclient.ui.servertree.view;

import cafe.woden.ircclient.app.api.ConnectionState;
import cafe.woden.ircclient.app.api.TargetRef;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import cafe.woden.ircclient.ui.icons.SvgIcons.Palette;
import cafe.woden.ircclient.ui.servertree.model.ServerTreeNodeData;
import cafe.woden.ircclient.ui.servertree.policy.ServerTreeTypingTargetPolicy;
import cafe.woden.ircclient.ui.servertree.viewmodel.ServerTreeConnectionStateViewModel;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.util.Objects;
import javax.swing.Icon;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;

/** Dedicated tree cell renderer for server tree nodes, badges, and typing indicators. */
public final class ServerTreeCellRenderer extends DefaultTreeCellRenderer {

  public interface Context {
    boolean serverTreeNotificationBadgesEnabled();

    int unreadBadgeScalePercent();

    ServerTreeTypingIndicatorStyle typingIndicatorStyle();

    boolean typingIndicatorsTreeEnabled();

    boolean isPrivateMessageTarget(TargetRef ref);

    boolean isPrivateMessageOnline(TargetRef ref);

    boolean isApplicationJfrActive();

    boolean isInterceptorEnabled(TargetRef ref);

    boolean isMonitorGroupNode(DefaultMutableTreeNode node);

    boolean isInterceptorsGroupNode(DefaultMutableTreeNode node);

    boolean isOtherGroupNode(DefaultMutableTreeNode node);

    boolean isServerNode(DefaultMutableTreeNode node);

    String serverNodeDisplayLabel(String serverId);

    boolean isEphemeralServer(String serverId);

    ConnectionState connectionStateForServer(String serverId);

    boolean isIrcRootNode(DefaultMutableTreeNode node);

    boolean isApplicationRootNode(DefaultMutableTreeNode node);

    boolean isPrivateMessagesGroupNode(DefaultMutableTreeNode node);

    boolean isSojuNetworksGroupNode(DefaultMutableTreeNode node);

    boolean isZncNetworksGroupNode(DefaultMutableTreeNode node);
  }

  private static final int TREE_NODE_ICON_SIZE = 13;
  private static final int TYPING_ACTIVITY_FADE_MS = 900;
  private static final int TYPING_ACTIVITY_PULSE_MS = 1200;
  private static final int TYPING_ACTIVITY_DOT_COUNT = 3;
  private static final int TYPING_ACTIVITY_DOT_SIZE = 3;
  private static final int TYPING_ACTIVITY_DOT_GAP = 2;
  private static final int TYPING_ACTIVITY_DOT_FRAME_MS = 220;
  private static final int TYPING_ACTIVITY_LEFT_SLOT_WIDTH = 12;
  private static final Color TYPING_ACTIVITY_GLOW_DOT = new Color(65, 210, 108);
  private static final Color TYPING_ACTIVITY_GLOW_HALO = new Color(120, 255, 150);
  private static final Color TYPING_ACTIVITY_INDICATOR_FALLBACK = new Color(90, 150, 235);
  private static final Color DETACHED_WARNING_FILL = new Color(230, 164, 39);
  private static final Color DETACHED_WARNING_STROKE = new Color(152, 94, 0);
  private static final Color DETACHED_WARNING_TEXT = Color.WHITE;
  private static final int TREE_BADGE_HORIZONTAL_PADDING = 4;
  private static final int TREE_BADGE_VERTICAL_PADDING = 1;
  private static final int TREE_BADGE_MIN_WIDTH = 14;
  private static final int TREE_BADGE_MIN_HEIGHT = 12;
  private static final int TREE_BADGE_GAP = 3;
  private static final int TREE_BADGE_ARC = 8;
  private static final Color TREE_UNREAD_BADGE_BG = new Color(31, 111, 255);
  private static final Color TREE_HIGHLIGHT_BADGE_BG = new Color(205, 54, 54);
  private static final Color TREE_BADGE_FG = Color.WHITE;

  private final String ircRootLabel;
  private final String applicationRootLabel;
  private final Context context;

  private float typingIndicatorAlpha = 0f;
  private boolean typingIndicatorSlotVisible = false;
  private boolean detachedWarningIndicatorVisible = false;
  private int unreadBadgeCount = 0;
  private int highlightBadgeCount = 0;

  public ServerTreeCellRenderer(String ircRootLabel, String applicationRootLabel, Context context) {
    this.ircRootLabel = Objects.requireNonNull(ircRootLabel, "ircRootLabel");
    this.applicationRootLabel =
        Objects.requireNonNull(applicationRootLabel, "applicationRootLabel");
    this.context = Objects.requireNonNull(context, "context");
  }

  public static int typingSlotWidthForStyle(ServerTreeTypingIndicatorStyle style) {
    return Math.max(TYPING_ACTIVITY_LEFT_SLOT_WIDTH, typingIndicatorWidthForStyle(style) + 2);
  }

  private static int typingIndicatorWidthForStyle(ServerTreeTypingIndicatorStyle style) {
    return switch (style == null ? ServerTreeTypingIndicatorStyle.DOTS : style) {
      case KEYBOARD -> 10;
      case GLOW_DOT -> 8;
      case DOTS ->
          TYPING_ACTIVITY_DOT_COUNT * TYPING_ACTIVITY_DOT_SIZE
              + (TYPING_ACTIVITY_DOT_COUNT - 1) * TYPING_ACTIVITY_DOT_GAP;
    };
  }

  @Override
  public java.awt.Component getTreeCellRendererComponent(
      JTree tree,
      Object value,
      boolean sel,
      boolean expanded,
      boolean leaf,
      int row,
      boolean hasFocus) {

    java.awt.Component c =
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);
    Font base = UIManager.getFont("Tree.font");
    if (base == null) base = tree.getFont();
    if (base == null) base = getFont();
    typingIndicatorAlpha = 0f;
    typingIndicatorSlotVisible = false;
    detachedWarningIndicatorVisible = false;
    unreadBadgeCount = 0;
    highlightBadgeCount = 0;

    if (value instanceof DefaultMutableTreeNode node) {
      Object userObject = node.getUserObject();
      if (userObject instanceof ServerTreeNodeData nodeData) {
        setText(nodeData.label);
        boolean detachedChannel =
            nodeData.ref != null && nodeData.ref.isChannel() && nodeData.detached;
        int style = nodeData.highlightUnread > 0 ? Font.BOLD : Font.PLAIN;
        if (context.serverTreeNotificationBadgesEnabled()) {
          unreadBadgeCount = Math.max(0, nodeData.unread);
          highlightBadgeCount = Math.max(0, nodeData.highlightUnread);
        }
        if (detachedChannel) {
          style |= Font.ITALIC;
        }
        setFont(base.deriveFont(style));
        if (!sel && detachedChannel) {
          Color muted = UIManager.getColor("Label.disabledForeground");
          if (muted == null) muted = UIManager.getColor("Component.disabledForeground");
          if (muted != null) setForeground(muted);
        }
        if (nodeData.ref != null && nodeData.ref.isChannel()) {
          setTreeIcon("channel");
        } else if (context.isPrivateMessageTarget(nodeData.ref)) {
          boolean online = context.isPrivateMessageOnline(nodeData.ref);
          String name = online ? "pm-online" : "pm-offline";
          Palette palette = online ? Palette.TREE_PM_ONLINE : Palette.TREE_PM_OFFLINE;
          Icon icon = SvgIcons.icon(name, TREE_NODE_ICON_SIZE, palette);
          setIcon(icon);
          setDisabledIcon(icon);
        } else if (nodeData.ref != null && nodeData.ref.isApplicationUnhandledErrors()) {
          setTreeIcon("info");
        } else if (nodeData.ref != null && nodeData.ref.isApplicationAssertjSwing()) {
          setTreeIcon("settings");
        } else if (nodeData.ref != null && nodeData.ref.isApplicationJhiccup()) {
          setTreeIcon("refresh");
        } else if (nodeData.ref != null && nodeData.ref.isApplicationInboundDedup()) {
          setTreeIcon("copy");
        } else if (nodeData.ref != null && nodeData.ref.isApplicationJfr()) {
          boolean active = context.isApplicationJfrActive();
          String iconName = active ? "play" : "pause";
          Palette palette = active ? Palette.TREE_PM_ONLINE : Palette.TREE_DISABLED;
          Icon icon = SvgIcons.icon(iconName, TREE_NODE_ICON_SIZE, palette);
          setIcon(icon);
          setDisabledIcon(icon);
        } else if (nodeData.ref != null && nodeData.ref.isApplicationSpring()) {
          setTreeIcon("theme");
        } else if (nodeData.ref != null && nodeData.ref.isApplicationTerminal()) {
          setTreeIcon("terminal");
        } else if (nodeData.ref != null && nodeData.ref.isStatus()) {
          setTreeIcon("dock-left");
        } else if (nodeData.ref != null && nodeData.ref.isNotifications()) {
          setTreeIcon("info");
        } else if (nodeData.ref != null && nodeData.ref.isLogViewer()) {
          setTreeIcon("copy");
        } else if (nodeData.ref != null && nodeData.ref.isInterceptor()) {
          setTreeIcon(context.isInterceptorEnabled(nodeData.ref) ? "interceptor" : "pause");
        } else if (nodeData.ref != null && nodeData.ref.isChannelList()) {
          setTreeIcon("add");
        } else if (nodeData.ref != null && nodeData.ref.isWeechatFilters()) {
          setTreeIcon("settings");
        } else if (nodeData.ref != null && nodeData.ref.isIgnores()) {
          setTreeIcon("ban");
        } else if (nodeData.ref != null && nodeData.ref.isDccTransfers()) {
          setTreeIcon("dock-right");
        } else if (nodeData.ref == null && context.isMonitorGroupNode(node)) {
          setTreeIcon("eye");
        } else if (nodeData.ref == null && context.isInterceptorsGroupNode(node)) {
          setTreeIcon("yin-yang");
        } else if (nodeData.ref == null && context.isOtherGroupNode(node)) {
          setTreeIcon("settings");
        }
        if (ServerTreeTypingTargetPolicy.supportsTypingActivity(nodeData.ref)) {
          detachedWarningIndicatorVisible = nodeData.hasDetachedWarning();
          typingIndicatorSlotVisible =
              detachedWarningIndicatorVisible || context.typingIndicatorsTreeEnabled();
          if (context.typingIndicatorsTreeEnabled() && !detachedWarningIndicatorVisible) {
            typingIndicatorAlpha =
                nodeData.typingDotAlpha(
                    System.currentTimeMillis(), TYPING_ACTIVITY_PULSE_MS, TYPING_ACTIVITY_FADE_MS);
          }
        }
      } else if (userObject instanceof String id && context.isServerNode(node)) {
        setText(context.serverNodeDisplayLabel(id));
        if (context.isEphemeralServer(id)) {
          setFont(base.deriveFont(Font.ITALIC));
        } else {
          setFont(base.deriveFont(Font.PLAIN));
        }
        ConnectionState state = context.connectionStateForServer(id);
        String iconName = ServerTreeConnectionStateViewModel.serverNodeIconName(state);
        Palette palette = ServerTreeConnectionStateViewModel.serverNodeIconPalette(state);
        Icon icon = SvgIcons.icon(iconName, TREE_NODE_ICON_SIZE, palette);
        Icon disabled = SvgIcons.icon(iconName, TREE_NODE_ICON_SIZE, Palette.TREE_DISABLED);
        setIcon(icon);
        setDisabledIcon(disabled);
      } else if (context.isIrcRootNode(node)) {
        setText(ircRootLabel);
        setFont(base.deriveFont(Font.PLAIN));
        setTreeIcon("chat");
      } else if (context.isApplicationRootNode(node)) {
        setText(applicationRootLabel);
        setFont(base.deriveFont(Font.PLAIN));
        setTreeIcon("settings");
      } else if (context.isPrivateMessagesGroupNode(node)) {
        setFont(base.deriveFont(Font.PLAIN));
        setTreeIcon("account-unknown");
      } else if (context.isMonitorGroupNode(node)) {
        setFont(base.deriveFont(Font.PLAIN));
        setTreeIcon("eye");
      } else if (context.isInterceptorsGroupNode(node)) {
        setFont(base.deriveFont(Font.PLAIN));
        setTreeIcon("yin-yang");
      } else if (context.isOtherGroupNode(node)) {
        setFont(base.deriveFont(Font.PLAIN));
        setTreeIcon("settings");
      } else if (context.isSojuNetworksGroupNode(node) || context.isZncNetworksGroupNode(node)) {
        setFont(base.deriveFont(Font.PLAIN));
        setTreeIcon("dock-left");
      } else {
        setFont(base.deriveFont(Font.PLAIN));
      }
    } else {
      setFont(base.deriveFont(Font.PLAIN));
    }

    return c;
  }

  @Override
  public Dimension getPreferredSize() {
    Dimension base = super.getPreferredSize();
    int extra = badgesPreferredWidth();
    if (extra <= 0) return base;
    return new Dimension(base.width + extra, base.height);
  }

  @Override
  public java.awt.Insets getInsets() {
    java.awt.Insets insets = super.getInsets();
    if (!typingIndicatorSlotVisible || insets == null) return insets;
    return new java.awt.Insets(
        insets.top,
        insets.left + typingIndicatorReserveLeftInset(typingStyle()),
        insets.bottom,
        insets.right);
  }

  @Override
  public java.awt.Insets getInsets(java.awt.Insets insets) {
    java.awt.Insets resolved = super.getInsets(insets);
    if (!typingIndicatorSlotVisible || resolved == null) return resolved;
    resolved.left = resolved.left + typingIndicatorReserveLeftInset(typingStyle());
    return resolved;
  }

  @Override
  protected void paintComponent(Graphics g) {
    super.paintComponent(g);

    Graphics2D g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      if (typingIndicatorSlotVisible
          && (detachedWarningIndicatorVisible || typingIndicatorAlpha > 0.01f)) {
        ServerTreeTypingIndicatorStyle style = typingStyle();
        int width = indicatorWidth(style);
        int height = indicatorHeight(style);
        java.awt.Insets insets = getInsets();
        int leftInset = insets != null ? insets.left : 0;
        int slotWidth = typingIndicatorSlotWidth(style);
        int slotLeft = Math.max(0, leftInset - slotWidth - 1);
        int x = slotLeft + Math.max(0, (slotWidth - width) / 2);
        int y = Math.max(0, (getHeight() - height) / 2);
        float alpha = Math.max(0f, Math.min(1f, typingIndicatorAlpha));

        if (detachedWarningIndicatorVisible) {
          drawDisconnectedWarningIndicator(g2, slotLeft, 0, slotWidth, getHeight());
        } else {
          switch (style) {
            case KEYBOARD -> drawKeyboardIndicator(g2, x, y, width, height, alpha);
            case GLOW_DOT -> drawGlowDotIndicator(g2, x, y, width, height, alpha);
            case DOTS -> drawDotsIndicator(g2, x, y, alpha);
          }
        }
      }
      paintUnreadBadges(g2);
    } finally {
      g2.dispose();
    }
  }

  private void setTreeIcon(String name) {
    Icon icon = SvgIcons.icon(name, TREE_NODE_ICON_SIZE, Palette.TREE);
    Icon disabled = SvgIcons.icon(name, TREE_NODE_ICON_SIZE, Palette.TREE_DISABLED);
    setIcon(icon);
    setDisabledIcon(disabled);
  }

  private ServerTreeTypingIndicatorStyle typingStyle() {
    ServerTreeTypingIndicatorStyle style = context.typingIndicatorStyle();
    return style == null ? ServerTreeTypingIndicatorStyle.DOTS : style;
  }

  private static int indicatorWidth(ServerTreeTypingIndicatorStyle style) {
    return switch (style) {
      case KEYBOARD -> 10;
      case GLOW_DOT -> 8;
      case DOTS ->
          TYPING_ACTIVITY_DOT_COUNT * TYPING_ACTIVITY_DOT_SIZE
              + (TYPING_ACTIVITY_DOT_COUNT - 1) * TYPING_ACTIVITY_DOT_GAP;
    };
  }

  private static int typingIndicatorSlotWidth(ServerTreeTypingIndicatorStyle style) {
    return Math.max(TYPING_ACTIVITY_LEFT_SLOT_WIDTH, indicatorWidth(style) + 2);
  }

  private static int typingIndicatorReserveLeftInset(ServerTreeTypingIndicatorStyle style) {
    return typingIndicatorSlotWidth(style) + 1;
  }

  private static int indicatorHeight(ServerTreeTypingIndicatorStyle style) {
    return switch (style) {
      case KEYBOARD -> 7;
      case GLOW_DOT -> 8;
      case DOTS -> TYPING_ACTIVITY_DOT_SIZE;
    };
  }

  private void drawDotsIndicator(Graphics2D g2, int x, int y, float alpha) {
    int dot = TYPING_ACTIVITY_DOT_SIZE;
    int gap = TYPING_ACTIVITY_DOT_GAP;
    int phase =
        (int)
            ((System.currentTimeMillis() / Math.max(80, TYPING_ACTIVITY_DOT_FRAME_MS))
                % TYPING_ACTIVITY_DOT_COUNT);
    Color base = typingIndicatorColor();
    g2.setComposite(AlphaComposite.SrcOver);
    for (int i = 0; i < TYPING_ACTIVITY_DOT_COUNT; i++) {
      float pulse = (i == phase) ? 1.0f : 0.42f;
      int a = Math.max(12, Math.min(255, Math.round(255f * alpha * pulse)));
      g2.setColor(withAlpha(base, a));
      g2.fillOval(x + (i * (dot + gap)), y, dot, dot);
    }
  }

  private void drawKeyboardIndicator(
      Graphics2D g2, int x, int y, int width, int height, float alpha) {
    Color base = typingIndicatorColor();
    int fillA = Math.max(8, Math.min(255, Math.round(50f * alpha)));
    int strokeA = Math.max(18, Math.min(255, Math.round(225f * alpha)));
    int keyA = Math.max(14, Math.min(255, Math.round(165f * alpha)));
    g2.setComposite(AlphaComposite.SrcOver);
    g2.setColor(withAlpha(base, fillA));
    g2.fillRoundRect(x, y, width, height, 3, 3);
    g2.setColor(withAlpha(base, strokeA));
    g2.drawRoundRect(x, y, width - 1, height - 1, 3, 3);

    int keyY1 = y + 2;
    int keyY2 = y + 4;
    g2.setColor(withAlpha(base, keyA));
    int[] top = {x + 2, x + 4, x + 6, x + 8};
    for (int keyX : top) {
      g2.fillRect(keyX, keyY1, 1, 1);
    }
    g2.fillRect(x + 3, keyY2, 4, 1);
  }

  private void drawGlowDotIndicator(
      Graphics2D g2, int x, int y, int width, int height, float alpha) {
    int dot = 6;
    int halo = 4;
    int cx = x + Math.max(0, (width - dot) / 2);
    int cy = y + Math.max(0, (height - dot) / 2);
    g2.setComposite(AlphaComposite.SrcOver.derive(Math.min(0.5f, alpha * 0.45f)));
    g2.setColor(TYPING_ACTIVITY_GLOW_HALO);
    g2.fillOval(cx - (halo / 2), cy - (halo / 2), dot + halo, dot + halo);

    g2.setComposite(AlphaComposite.SrcOver.derive(alpha));
    g2.setColor(TYPING_ACTIVITY_GLOW_DOT);
    g2.fillOval(cx, cy, dot, dot);
  }

  private void drawDisconnectedWarningIndicator(
      Graphics2D g2, int slotLeft, int y, int slotWidth, int slotHeight) {
    int icon = Math.max(8, Math.min(10, Math.min(slotWidth - 2, slotHeight - 4)));
    int x = slotLeft + Math.max(0, (slotWidth - icon) / 2);
    int ty = y + Math.max(1, (slotHeight - icon) / 2);

    int topX = x + (icon / 2);
    int topY = ty;
    int leftX = x;
    int leftY = ty + icon - 1;
    int rightX = x + icon - 1;
    int rightY = leftY;

    Polygon triangle =
        new Polygon(new int[] {topX, leftX, rightX}, new int[] {topY, leftY, rightY}, 3);
    g2.setComposite(AlphaComposite.SrcOver);
    g2.setColor(DETACHED_WARNING_FILL);
    g2.fillPolygon(triangle);
    g2.setColor(DETACHED_WARNING_STROKE);
    g2.drawPolygon(triangle);

    int cx = topX;
    int exTop = ty + Math.max(2, icon / 4);
    int exBottom = ty + Math.max(exTop + 1, icon - 4);
    g2.setColor(DETACHED_WARNING_TEXT);
    g2.drawLine(cx, exTop, cx, exBottom);
    g2.fillOval(cx - 1, ty + icon - 3, 2, 2);
  }

  private int badgesPreferredWidth() {
    if (!context.serverTreeNotificationBadgesEnabled()) return 0;
    if (unreadBadgeCount <= 0 && highlightBadgeCount <= 0) return 0;
    FontMetrics fm = getFontMetrics(badgeFont());
    if (fm == null) return 0;
    int width = badgeClusterWidth(fm);
    return width > 0 ? (width + scaledBadgeGap()) : 0;
  }

  private void paintUnreadBadges(Graphics2D g2) {
    if (!context.serverTreeNotificationBadgesEnabled()) return;
    if (unreadBadgeCount <= 0 && highlightBadgeCount <= 0) return;
    Font badgeFont = badgeFont();
    FontMetrics fm = g2.getFontMetrics(badgeFont);
    if (fm == null) return;

    int badgeHeight =
        Math.max(scaledBadgeMinHeight(), fm.getAscent() + (scaledBadgeVerticalPadding() * 2));
    int x = badgeStartX(fm);
    int y = Math.max(0, (getHeight() - badgeHeight) / 2);

    if (unreadBadgeCount > 0) {
      String text = Integer.toString(unreadBadgeCount);
      int w = badgeWidthForText(fm, text);
      paintBadge(g2, x, y, w, badgeHeight, TREE_UNREAD_BADGE_BG, text, fm, badgeFont);
      x += w + scaledBadgeGap();
    }

    if (highlightBadgeCount > 0) {
      String text = Integer.toString(highlightBadgeCount);
      int w = badgeWidthForText(fm, text);
      paintBadge(g2, x, y, w, badgeHeight, TREE_HIGHLIGHT_BADGE_BG, text, fm, badgeFont);
    }
  }

  private int badgeStartX(FontMetrics fm) {
    int x = 0;
    java.awt.Insets insets = getInsets();
    if (insets != null) x += insets.left;
    Icon icon = getIcon();
    if (icon != null) x += icon.getIconWidth() + Math.max(0, getIconTextGap());
    String text = Objects.toString(getText(), "");
    if (!text.isEmpty()) x += fm.stringWidth(text);
    x += scaledBadgeGap();
    return x;
  }

  private int badgeClusterWidth(FontMetrics fm) {
    int width = 0;
    if (unreadBadgeCount > 0) {
      width += badgeWidthForText(fm, Integer.toString(unreadBadgeCount));
    }
    if (highlightBadgeCount > 0) {
      if (width > 0) width += scaledBadgeGap();
      width += badgeWidthForText(fm, Integer.toString(highlightBadgeCount));
    }
    return width;
  }

  private int badgeWidthForText(FontMetrics fm, String text) {
    int textWidth = fm == null ? 0 : fm.stringWidth(Objects.toString(text, ""));
    return Math.max(scaledBadgeMinWidth(), textWidth + (scaledBadgeHorizontalPadding() * 2));
  }

  private void paintBadge(
      Graphics2D g2,
      int x,
      int y,
      int width,
      int height,
      Color bg,
      String text,
      FontMetrics fm,
      Font badgeFont) {
    if (g2 == null) return;
    g2.setComposite(AlphaComposite.SrcOver);
    g2.setColor(bg);
    int arc = scaledBadgeArc();
    g2.fillRoundRect(x, y, width, height, arc, arc);
    g2.setFont(badgeFont);
    int textX = x + Math.max(0, (width - fm.stringWidth(text)) / 2);
    int textY = y + Math.max(0, ((height - fm.getHeight()) / 2) + fm.getAscent());
    g2.setColor(TREE_BADGE_FG);
    g2.drawString(text, textX, textY);
  }

  private Font badgeFont() {
    Font base = getFont();
    if (base == null) base = UIManager.getFont("Tree.font");
    if (base == null) base = UIManager.getFont("defaultFont");
    if (base == null) return new Font("SansSerif", Font.PLAIN, 12);
    int scalePercent = Math.max(50, Math.min(150, context.unreadBadgeScalePercent()));
    float scaledSize = Math.max(8f, base.getSize2D() * (scalePercent / 100f));
    return base.deriveFont(scaledSize);
  }

  private int scaledBadgeHorizontalPadding() {
    return scaleBadgeMetric(TREE_BADGE_HORIZONTAL_PADDING, 1);
  }

  private int scaledBadgeVerticalPadding() {
    return scaleBadgeMetric(TREE_BADGE_VERTICAL_PADDING, 1);
  }

  private int scaledBadgeMinWidth() {
    return scaleBadgeMetric(TREE_BADGE_MIN_WIDTH, 10);
  }

  private int scaledBadgeMinHeight() {
    return scaleBadgeMetric(TREE_BADGE_MIN_HEIGHT, 8);
  }

  private int scaledBadgeGap() {
    return scaleBadgeMetric(TREE_BADGE_GAP, 1);
  }

  private int scaledBadgeArc() {
    return scaleBadgeMetric(TREE_BADGE_ARC, 4);
  }

  private int scaleBadgeMetric(int base, int minimum) {
    float factor = Math.max(50, Math.min(150, context.unreadBadgeScalePercent())) / 100f;
    return Math.max(minimum, Math.round(base * factor));
  }

  private Color typingIndicatorColor() {
    Color color = UIManager.getColor("@accentColor");
    if (color == null) color = UIManager.getColor("Component.focusColor");
    if (color == null) color = UIManager.getColor("Label.foreground");
    if (color == null) color = TYPING_ACTIVITY_INDICATOR_FALLBACK;
    return color;
  }

  private static Color withAlpha(Color color, int alpha) {
    Color base = color == null ? Color.GRAY : color;
    int a = Math.max(0, Math.min(255, alpha));
    return new Color(base.getRed(), base.getGreen(), base.getBlue(), a);
  }
}
