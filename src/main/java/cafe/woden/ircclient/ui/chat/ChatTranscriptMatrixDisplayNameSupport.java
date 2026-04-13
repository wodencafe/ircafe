package cafe.woden.ircclient.ui.chat;

import cafe.woden.ircclient.irc.roster.UserListStore;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import javax.swing.text.AttributeSet;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyledDocument;

/** Shared Matrix-specific sender-label rendering and relabeling helpers for transcripts. */
final class ChatTranscriptMatrixDisplayNameSupport {

  record Context(
      UiSettingsBus uiSettings,
      UserListStore userListStore,
      Function<TargetRef, StyledDocument> documentForTarget) {
    Context {
      Objects.requireNonNull(documentForTarget, "documentForTarget");
    }
  }

  private record FromRunReplacement(
      int startOffset, int endOffset, String replacementText, AttributeSet attrs) {}

  private ChatTranscriptMatrixDisplayNameSupport() {}

  static String renderTranscriptFrom(Context context, TargetRef ref, String from) {
    String raw = Objects.toString(from, "").trim();
    if (raw.isEmpty()) return raw;
    if (!looksLikeMatrixUserId(raw)) return raw;
    if (context == null || context.userListStore() == null || ref == null) return raw;

    String sid = Objects.toString(ref.serverId(), "").trim();
    if (sid.isEmpty()) return raw;

    String realName =
        Objects.toString(context.userListStore().getLearnedRealName(sid, raw), "").trim();
    if (realName.isEmpty() || realName.equalsIgnoreCase(raw)) return raw;

    return "verbose".equals(matrixTranscriptNameDisplayMode(context))
        ? realName + " (" + raw + ")"
        : realName;
  }

  static String matrixTranscriptNameDisplayMode(Context context) {
    try {
      UiSettings settings =
          context != null && context.uiSettings() != null ? context.uiSettings().get() : null;
      return normalizeMatrixUserListNameDisplayMode(
          settings == null ? "" : settings.matrixUserListNameDisplayMode());
    } catch (Exception ignored) {
      return "compact";
    }
  }

  static boolean looksLikeMatrixUserId(String token) {
    String value = Objects.toString(token, "").trim();
    if (!value.startsWith("@")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  static String normalizeMatrixUserListNameDisplayMode(String raw) {
    String value = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (value.isEmpty()) return "compact";
    return switch (value) {
      case "compact", "display-name-only", "displayname", "name-only" -> "compact";
      case "verbose", "display-name-and-user-id", "displayname-and-userid", "full" -> "verbose";
      default -> "compact";
    };
  }

  static int refreshMatrixDisplayNames(Context context, TargetRef ref, String matrixUserIdFilter) {
    if (context == null || ref == null) return 0;
    String userIdFilter = Objects.toString(matrixUserIdFilter, "").trim();
    if (!userIdFilter.isEmpty() && !looksLikeMatrixUserId(userIdFilter)) return 0;

    StyledDocument doc = context.documentForTarget().apply(ref);
    if (doc == null) return 0;

    int len = doc.getLength();
    if (len <= 0) return 0;

    ArrayList<FromRunReplacement> replacements = new ArrayList<>();
    int off = 0;
    while (off < len) {
      Element el = doc.getCharacterElement(off);
      if (el == null) break;

      int start = Math.max(0, Math.min(el.getStartOffset(), len));
      int end = Math.max(start, Math.min(el.getEndOffset(), len));
      if (end <= start) {
        off++;
        continue;
      }

      AttributeSet attrs = el.getAttributes();
      String styleId = Objects.toString(attrs.getAttribute(ChatStyles.ATTR_STYLE), "").trim();
      if (!isMatrixTranscriptFromStyle(styleId)) {
        off = end;
        continue;
      }

      String rawFrom = Objects.toString(attrs.getAttribute(ChatStyles.ATTR_META_FROM), "").trim();
      if (!looksLikeMatrixUserId(rawFrom)) {
        off = end;
        continue;
      }
      if (!userIdFilter.isEmpty() && !rawFrom.equalsIgnoreCase(userIdFilter)) {
        off = end;
        continue;
      }

      String renderedFrom = renderTranscriptFrom(context, ref, rawFrom);
      if (renderedFrom.isBlank() || renderedFrom.equalsIgnoreCase(rawFrom)) {
        off = end;
        continue;
      }

      String existing;
      try {
        existing = doc.getText(start, end - start);
      } catch (Exception ignored) {
        off = end;
        continue;
      }

      String replacement = renderedFrom + matrixFromSuffix(styleId, existing);
      if (!existing.equals(replacement)) {
        replacements.add(
            new FromRunReplacement(start, end, replacement, new SimpleAttributeSet(attrs)));
      }
      off = end;
    }

    for (int i = replacements.size() - 1; i >= 0; i--) {
      FromRunReplacement rep = replacements.get(i);
      int removeLen = Math.max(0, rep.endOffset() - rep.startOffset());
      try {
        doc.remove(rep.startOffset(), removeLen);
        doc.insertString(rep.startOffset(), rep.replacementText(), rep.attrs());
      } catch (Exception ignored) {
      }
    }
    return replacements.size();
  }

  static boolean isMatrixTranscriptFromStyle(String styleId) {
    return ChatStyles.STYLE_FROM.equals(styleId)
        || ChatStyles.STYLE_NOTICE_FROM.equals(styleId)
        || ChatStyles.STYLE_ACTION_FROM.equals(styleId);
  }

  static String matrixFromSuffix(String styleId, String existingText) {
    if (ChatStyles.STYLE_ACTION_FROM.equals(styleId)) return "";

    String existing = Objects.toString(existingText, "");
    if (existing.endsWith(": ")) return ": ";
    if (existing.endsWith(":")) return ":";
    return ": ";
  }
}
