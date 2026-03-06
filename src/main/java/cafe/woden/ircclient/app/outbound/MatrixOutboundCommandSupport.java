package cafe.woden.ircclient.app.outbound;

import cafe.woden.ircclient.app.api.UiPort;
import cafe.woden.ircclient.model.TargetRef;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

final class MatrixOutboundCommandSupport {
  private static final Set<String> MATRIX_UPLOAD_MSGTYPES =
      Set.of("m.image", "m.file", "m.video", "m.audio");

  MatrixOutboundCommandSupport() {}

  void appendUploadHelp(UiPort ui, TargetRef out) {
    ui.appendStatus(
        out,
        "(help)",
        "/upload <m.image|m.file|m.video|m.audio> <path> [caption]  (msgtype shortcuts: image|file|video|audio)");
  }

  void appendUploadUsage(UiPort ui, TargetRef out) {
    ui.appendStatus(out, "(upload)", "Usage: /upload <msgtype> <path> [caption]");
    ui.appendStatus(
        out,
        "(upload)",
        "msgtype: m.image | m.file | m.video | m.audio (shortcuts: image|file|video|audio)");
  }

  String normalizeUploadMsgType(String raw) {
    String token = Objects.toString(raw, "").trim().toLowerCase(Locale.ROOT);
    if (token.isEmpty()) return "";
    return switch (token) {
      case "image" -> "m.image";
      case "file" -> "m.file";
      case "video" -> "m.video";
      case "audio" -> "m.audio";
      default -> MATRIX_UPLOAD_MSGTYPES.contains(token) ? token : "";
    };
  }

  String normalizeUploadPath(String raw) {
    return Objects.toString(raw, "").trim();
  }

  String defaultUploadCaption(String path) {
    String rawPath = Objects.toString(path, "").trim();
    if (rawPath.isEmpty()) return "";
    try {
      Path fileName = Path.of(rawPath).getFileName();
      if (fileName != null) {
        String fromPath = Objects.toString(fileName.toString(), "").trim();
        if (!fromPath.isEmpty()) return fromPath;
      }
    } catch (InvalidPathException ignored) {
      // Fall back to simple slash-segment extraction below.
    }
    int slash = Math.max(rawPath.lastIndexOf('/'), rawPath.lastIndexOf('\\'));
    if (slash >= 0 && slash + 1 < rawPath.length()) {
      return rawPath.substring(slash + 1).trim();
    }
    return rawPath;
  }

  String buildUploadPrivmsg(
      String target, String normalizedType, String sourcePath, String displayBody) {
    String roomTarget = Objects.toString(target, "").trim();
    String msgType = normalizeUploadMsgType(normalizedType);
    String uploadPath = normalizeUploadPath(sourcePath);
    String body = Objects.toString(displayBody, "").trim();
    if (roomTarget.isEmpty() || msgType.isEmpty() || uploadPath.isEmpty()) {
      return "";
    }

    String line =
        "@+matrix/msgtype="
            + escapeIrcv3TagValue(msgType)
            + ";+matrix/upload_path="
            + escapeIrcv3TagValue(uploadPath)
            + " PRIVMSG "
            + roomTarget;
    if (!body.isEmpty()) {
      line += " :" + body;
    }
    return line;
  }

  private static String escapeIrcv3TagValue(String value) {
    String raw = Objects.toString(value, "");
    if (raw.isEmpty()) return "";
    StringBuilder out = new StringBuilder(raw.length() + 8);
    for (int i = 0; i < raw.length(); i++) {
      char c = raw.charAt(i);
      switch (c) {
        case ';' -> out.append("\\:");
        case ' ' -> out.append("\\s");
        case '\\' -> out.append("\\\\");
        case '\r' -> out.append("\\r");
        case '\n' -> out.append("\\n");
        default -> out.append(c);
      }
    }
    return out.toString();
  }
}
