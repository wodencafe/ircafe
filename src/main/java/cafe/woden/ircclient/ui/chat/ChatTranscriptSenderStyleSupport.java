package cafe.woden.ircclient.ui.chat;

import java.util.Objects;
import javax.swing.text.AttributeSet;
import javax.swing.text.SimpleAttributeSet;

/** Shared sender-line style preparation helpers for chat, notice, and action rows. */
final class ChatTranscriptSenderStyleSupport {

  @FunctionalInterface
  interface LineMetaBinder {
    SimpleAttributeSet bind(AttributeSet base, ChatTranscriptStore.LineMeta meta);
  }

  @FunctionalInterface
  interface OutgoingColorApplier {
    void apply(
        SimpleAttributeSet fromStyle, SimpleAttributeSet messageStyle, boolean outgoingLocalEcho);
  }

  @FunctionalInterface
  interface NotificationHighlightApplier {
    void apply(
        SimpleAttributeSet fromStyle, SimpleAttributeSet messageStyle, String rawNotificationColor);
  }

  record Context(
      ChatStyles styles,
      NickColorService nickColors,
      LineMetaBinder lineMetaBinder,
      OutgoingColorApplier outgoingColorApplier,
      NotificationHighlightApplier notificationHighlightApplier) {
    Context {
      Objects.requireNonNull(styles, "styles");
      Objects.requireNonNull(lineMetaBinder, "lineMetaBinder");
      Objects.requireNonNull(outgoingColorApplier, "outgoingColorApplier");
      Objects.requireNonNull(notificationHighlightApplier, "notificationHighlightApplier");
    }
  }

  record PreparedStyles(SimpleAttributeSet fromStyle, SimpleAttributeSet messageStyle) {}

  private ChatTranscriptSenderStyleSupport() {}

  static PreparedStyles prepare(
      Context context,
      ChatTranscriptStore.LineMeta meta,
      String from,
      boolean outgoingLocalEcho,
      String notificationRuleHighlightColor) {
    return prepareChat(context, meta, from, outgoingLocalEcho, notificationRuleHighlightColor);
  }

  static PreparedStyles prepareChat(
      Context context,
      ChatTranscriptStore.LineMeta meta,
      String from,
      boolean outgoingLocalEcho,
      String notificationRuleHighlightColor) {
    return prepare(
        context,
        context.styles().from(),
        context.styles().message(),
        meta,
        from,
        true,
        outgoingLocalEcho,
        notificationRuleHighlightColor);
  }

  static PreparedStyles prepare(
      Context context,
      AttributeSet baseFromStyle,
      AttributeSet baseMessageStyle,
      ChatTranscriptStore.LineMeta meta,
      String from,
      boolean applyNickColor,
      boolean outgoingLocalEcho,
      String notificationRuleHighlightColor) {
    if (context == null) return null;

    AttributeSet fromStyle = baseFromStyle;
    if (applyNickColor
        && from != null
        && !from.isBlank()
        && context.nickColors() != null
        && context.nickColors().enabled()) {
      fromStyle = context.nickColors().forNick(from, fromStyle);
    }

    SimpleAttributeSet preparedFromStyle = context.lineMetaBinder().bind(fromStyle, meta);
    SimpleAttributeSet preparedMessageStyle = context.lineMetaBinder().bind(baseMessageStyle, meta);
    context
        .outgoingColorApplier()
        .apply(preparedFromStyle, preparedMessageStyle, outgoingLocalEcho);
    context
        .notificationHighlightApplier()
        .apply(preparedFromStyle, preparedMessageStyle, notificationRuleHighlightColor);
    return new PreparedStyles(preparedFromStyle, preparedMessageStyle);
  }
}
