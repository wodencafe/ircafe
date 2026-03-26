package cafe.woden.ircclient.ui.settings;

import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;

final class ChatDisplayControlsSupport {
  private ChatDisplayControlsSupport() {}

  static ImageEmbedControls buildImageEmbedControls(
      UiSettings current, List<AutoCloseable> closeables) {
    JCheckBox imageEmbeds = new JCheckBox("Enable inline image embeds (direct links)");
    imageEmbeds.setSelected(current.imageEmbedsEnabled());
    imageEmbeds.setToolTipText(
        "If enabled, IRCafe will download and render images from direct image URLs in chat.");

    JCheckBox imageEmbedsCollapsed = new JCheckBox("Collapse inline images by default");
    imageEmbedsCollapsed.setSelected(current.imageEmbedsCollapsedByDefault());
    imageEmbedsCollapsed.setToolTipText(
        "If enabled, newly inserted inline images start collapsed (header shown; click to expand).");
    imageEmbedsCollapsed.setEnabled(imageEmbeds.isSelected());
    JSpinner imageMaxWidth =
        PreferencesDialog.numberSpinner(current.imageEmbedsMaxWidthPx(), 0, 4096, 10, closeables);
    imageMaxWidth.setToolTipText(
        "Maximum width for inline images (pixels).\n"
            + "If 0, IRCafe will only scale images down to fit the chat viewport.");
    imageMaxWidth.setEnabled(imageEmbeds.isSelected());
    JSpinner imageMaxHeight =
        PreferencesDialog.numberSpinner(current.imageEmbedsMaxHeightPx(), 0, 4096, 10, closeables);
    imageMaxHeight.setToolTipText(
        "Maximum height for inline images (pixels).\n"
            + "If 0, IRCafe will only scale images down based on viewport width (and max width cap, if set).");
    imageMaxHeight.setEnabled(imageEmbeds.isSelected());

    JCheckBox animateGifs = new JCheckBox("Animate GIFs");
    animateGifs.setSelected(current.imageEmbedsAnimateGifs());
    animateGifs.setToolTipText("If disabled, animated GIFs render as a still image (first frame).");
    animateGifs.setEnabled(imageEmbeds.isSelected());

    imageEmbeds.addActionListener(
        e -> {
          boolean enabled = imageEmbeds.isSelected();
          imageEmbedsCollapsed.setEnabled(enabled);
          imageMaxWidth.setEnabled(enabled);
          imageMaxHeight.setEnabled(enabled);
          animateGifs.setEnabled(enabled);
        });

    JPanel imagePanel =
        new JPanel(
            new MigLayout("insets 0, fillx, wrap 2", "[grow,fill]8[nogrid]", "[]4[]4[]4[]4[]"));
    imagePanel.setOpaque(false);
    imagePanel.add(imageEmbeds, "span 2, wrap");
    imagePanel.add(imageEmbedsCollapsed, "span 2, wrap");
    imagePanel.add(new JLabel("Max image width (px, 0 = no limit):"));
    imagePanel.add(imageMaxWidth, "w 110!");
    imagePanel.add(new JLabel("Max image height (px, 0 = no limit):"));
    imagePanel.add(imageMaxHeight, "w 110!");
    imagePanel.add(animateGifs, "span 2, wrap");

    return new ImageEmbedControls(
        imageEmbeds, imageEmbedsCollapsed, imageMaxWidth, imageMaxHeight, animateGifs, imagePanel);
  }

  static LinkPreviewControls buildLinkPreviewControls(
      UiSettings current, EmbedCardStyle currentEmbedCardStyle) {
    JCheckBox linkPreviews = new JCheckBox("Enable link previews (OpenGraph cards)");
    linkPreviews.setSelected(current.linkPreviewsEnabled());
    linkPreviews.setToolTipText(
        "If enabled, IRCafe will fetch page metadata (title/description/image) and show a preview card under messages.\n"
            + "Note: this makes network requests to the linked sites.");

    JCheckBox linkPreviewsCollapsed = new JCheckBox("Collapse link previews by default");
    linkPreviewsCollapsed.setSelected(current.linkPreviewsCollapsedByDefault());
    linkPreviewsCollapsed.setToolTipText(
        "If enabled, newly inserted link previews start collapsed (header shown; click to expand).");
    linkPreviewsCollapsed.setEnabled(linkPreviews.isSelected());
    linkPreviews.addActionListener(
        e -> linkPreviewsCollapsed.setEnabled(linkPreviews.isSelected()));

    JComboBox<EmbedCardStyle> cardStyle = new JComboBox<>(EmbedCardStyle.values());
    cardStyle.setSelectedItem(
        currentEmbedCardStyle != null ? currentEmbedCardStyle : EmbedCardStyle.DEFAULT);
    cardStyle.setToolTipText(
        "Visual preset for inline cards used by link previews and image embeds.");

    JPanel linkPanel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]4[]8[]"));
    linkPanel.setOpaque(false);
    linkPanel.add(linkPreviews);
    linkPanel.add(linkPreviewsCollapsed);
    JPanel styleRow = new JPanel(new MigLayout("insets 0", "[][grow,fill]", "[]"));
    styleRow.setOpaque(false);
    styleRow.add(new JLabel("Card style"));
    styleRow.add(cardStyle, "w 180!");
    linkPanel.add(styleRow, "growx");

    return new LinkPreviewControls(linkPreviews, linkPreviewsCollapsed, cardStyle, linkPanel);
  }

  static TimestampControls buildTimestampControls(UiSettings current) {
    JCheckBox enabled = new JCheckBox("Show timestamps");
    enabled.setSelected(current.timestampsEnabled());
    enabled.setToolTipText("Prefix transcript lines with a time like [12:34:56].");

    JTextField format = new JTextField(current.timestampFormat(), 16);
    format.setToolTipText("java.time DateTimeFormatter pattern (e.g., HH:mm:ss or h:mm a).");

    JCheckBox includeChatMessages = new JCheckBox("Include regular chat messages");
    includeChatMessages.setSelected(current.timestampsIncludeChatMessages());
    includeChatMessages.setToolTipText(
        "When enabled, timestamps are also shown on normal chat messages (not just status lines).");

    JCheckBox includePresenceMessages = new JCheckBox("Include presence / folded messages");
    includePresenceMessages.setSelected(current.timestampsIncludePresenceMessages());
    includePresenceMessages.setToolTipText(
        "When enabled, timestamps are shown for join/part/quit/nick presence lines and expanded fold details.");

    Runnable syncEnabled =
        () -> {
          boolean on = enabled.isSelected();
          format.setEnabled(on);
          includeChatMessages.setEnabled(on);
          includePresenceMessages.setEnabled(on);
        };
    enabled.addItemListener(e -> syncEnabled.run());
    syncEnabled.run();

    JPanel panel =
        new JPanel(new MigLayout("insets 0, fillx, wrap 1", "[grow,fill]", "[]6[]6[]6[]"));
    panel.setOpaque(false);
    panel.add(enabled);
    JPanel formatRow = new JPanel(new MigLayout("insets 0, fillx, wrap 2", "[][grow,fill]", "[]"));
    formatRow.setOpaque(false);
    formatRow.add(new JLabel("Format"));
    formatRow.add(format, "w 200!");
    panel.add(formatRow);
    panel.add(includeChatMessages);
    panel.add(includePresenceMessages);

    return new TimestampControls(
        enabled, format, includeChatMessages, includePresenceMessages, panel);
  }
}
