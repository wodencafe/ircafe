package cafe.woden.ircclient.logging.viewer;

import cafe.woden.ircclient.logging.ChatLogRepository;
import cafe.woden.ircclient.logging.model.LogLine;
import cafe.woden.ircclient.logging.model.LogKind;
import cafe.woden.ircclient.logging.model.LogRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** DB-backed log viewer service. */
public final class DbChatLogViewerService implements ChatLogViewerService {

  private static final int DEFAULT_LIMIT = 500;
  private static final int MAX_LIMIT = 10_000;
  private static final int MAX_SCAN_LIMIT = 50_000;
  private static final int FILTER_SCAN_MULTIPLIER = 25;

  private static final ObjectMapper JSON = new ObjectMapper();

  private static final TextMatcher MATCH_ALL = value -> true;
  private static final Meta EMPTY_META = new Meta("", "", Map.of());
  private static final Pattern NUMERIC_REPLY_PREFIX = Pattern.compile("^\\s*\\[(\\d{3,4})]");

  private static final Set<String> SERVER_EVENT_FROM_TOKENS = Set.of(
      "server",
      "mode",
      "join",
      "part",
      "quit",
      "nick",
      "conn",
      "ui",
      "names",
      "who",
      "topic"
  );

  private static final Set<String> PROTOCOL_FROM_TOKENS = Set.of(
      "cap",
      "sasl",
      "raw",
      "protocol",
      "debug",
      "isupport",
      "who",
      "names",
      "list"
  );

  private static final String[] PROTOCOL_TEXT_MARKERS = {
      "cap ls",
      "cap req",
      "cap ack",
      "cap nak",
      "cap new",
      "cap del",
      "cap end",
      " authenticate ",
      " sasl",
      "isupport",
      "chathistory",
      "batch",
      "znc.in/",
      "draft/",
      "channel modes:",
      "end of /who",
      "end of who",
      "end of /names",
      "end of names",
      "end of /list",
      "end of list",
      "end of motd"
  };

  private final ChatLogRepository repo;

  public DbChatLogViewerService(ChatLogRepository repo) {
    this.repo = Objects.requireNonNull(repo, "repo");
  }

  @Override
  public boolean enabled() {
    return true;
  }

  @Override
  public ChatLogViewerResult search(ChatLogViewerQuery query) {
    if (query == null) return new ChatLogViewerResult(List.of(), 0, false, false);

    String serverId = Objects.toString(query.serverId(), "").trim();
    if (serverId.isEmpty()) return new ChatLogViewerResult(List.of(), 0, false, false);

    int wanted = clampLimit(query.limit());
    TextMatcher nickMatcher = compileMatcher(query.nickPattern(), query.nickMode(), "nick");
    TextMatcher messageMatcher = compileMatcher(query.messagePattern(), query.messageMode(), "message");
    TextMatcher hostmaskMatcher = compileMatcher(query.hostmaskPattern(), query.hostmaskMode(), "hostmask");
    TextMatcher channelMatcher = compileMatcher(query.channelPattern(), query.channelMode(), "channel");
    boolean includeServerEvents = query.includeServerEvents();
    boolean includeProtocolDetails = query.includeProtocolDetails();

    boolean hasPostFilters =
        nickMatcher != MATCH_ALL
            || messageMatcher != MATCH_ALL
            || hostmaskMatcher != MATCH_ALL
            || channelMatcher != MATCH_ALL
            || !includeServerEvents
            || !includeProtocolDetails;
    int scanLimit = wanted;
    if (hasPostFilters) {
      long expanded = (long) wanted * FILTER_SCAN_MULTIPLIER;
      scanLimit = (int) Math.max(wanted, Math.min(MAX_SCAN_LIMIT, expanded));
    }

    List<LogRow> candidates = repo.searchRows(serverId, query.fromEpochMs(), query.toEpochMs(), scanLimit);
    if (candidates == null || candidates.isEmpty()) {
      return new ChatLogViewerResult(List.of(), 0, false, false);
    }

    ArrayList<ChatLogViewerRow> out = new ArrayList<>(Math.min(wanted, candidates.size()));
    for (LogRow row : candidates) {
      if (row == null) continue;
      LogLine line = row.line();
      if (line == null) continue;

      Meta meta = parseMeta(line.metaJson());
      String fromNick = Objects.toString(line.fromNick(), "").trim();
      String target = Objects.toString(line.target(), "").trim();
      String text = Objects.toString(line.text(), "").trim();
      if (!nickMatcher.matches(fromNick)) continue;
      if (!messageMatcher.matches(text)) continue;
      if (!hostmaskMatcher.matches(meta.hostmask())) continue;
      if (!channelMatcher.matches(target)) continue;
      if (!includeServerEvents && isServerEventLine(line)) continue;
      if (!includeProtocolDetails && isProtocolDebugLine(line)) continue;

      out.add(new ChatLogViewerRow(
          row.id(),
          line.serverId(),
          target,
          line.tsEpochMs(),
          line.direction(),
          line.kind(),
          fromNick,
          meta.hostmask(),
          line.text(),
          meta.messageId(),
          meta.tags(),
          line.metaJson()
      ));
      if (out.size() >= wanted) break;
    }

    boolean reachedScanLimit = hasPostFilters && candidates.size() >= scanLimit;
    boolean scanCapped = reachedScanLimit && scanLimit >= MAX_SCAN_LIMIT;
    boolean truncated = out.size() >= wanted || reachedScanLimit;
    return new ChatLogViewerResult(List.copyOf(out), candidates.size(), truncated, scanCapped);
  }

  private static int clampLimit(int limit) {
    if (limit <= 0) return DEFAULT_LIMIT;
    return Math.min(limit, MAX_LIMIT);
  }

  private static TextMatcher compileMatcher(String pattern, ChatLogViewerMatchMode mode, String fieldLabel) {
    String p = Objects.toString(pattern, "").trim();
    if (p.isEmpty()) return MATCH_ALL;
    ChatLogViewerMatchMode m = (mode == null) ? ChatLogViewerMatchMode.CONTAINS : mode;

    return switch (m) {
      case CONTAINS -> {
        String needle = p.toLowerCase(Locale.ROOT);
        yield value -> Objects.toString(value, "").toLowerCase(Locale.ROOT).contains(needle);
      }
      case GLOB -> {
        Pattern re = compileGlob(p, fieldLabel);
        yield value -> re.matcher(Objects.toString(value, "")).matches();
      }
      case REGEX -> {
        Pattern re = compileRegex(p, fieldLabel);
        yield value -> re.matcher(Objects.toString(value, "")).find();
      }
    };
  }

  private static Pattern compileRegex(String pattern, String fieldLabel) {
    try {
      return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    } catch (PatternSyntaxException ex) {
      throw new IllegalArgumentException("Invalid " + fieldLabel + " regex: " + ex.getMessage(), ex);
    }
  }

  private static Pattern compileGlob(String glob, String fieldLabel) {
    StringBuilder sb = new StringBuilder(glob.length() + 12);
    sb.append('^');
    for (int i = 0; i < glob.length(); i++) {
      char c = glob.charAt(i);
      switch (c) {
        case '*' -> sb.append(".*");
        case '?' -> sb.append('.');
        case '.', '\\', '+', '(', ')', '[', ']', '{', '}', '^', '$', '|' -> sb.append('\\').append(c);
        default -> sb.append(c);
      }
    }
    sb.append('$');
    try {
      return Pattern.compile(sb.toString(), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    } catch (PatternSyntaxException ex) {
      throw new IllegalArgumentException("Invalid " + fieldLabel + " glob: " + ex.getMessage(), ex);
    }
  }

  private static Meta parseMeta(String metaJson) {
    String raw = Objects.toString(metaJson, "").trim();
    if (raw.isEmpty()) return EMPTY_META;

    try {
      JsonNode root = JSON.readTree(raw);
      if (root == null || !root.isObject()) return EMPTY_META;

      String messageId = text(root.get("messageId"));
      String hostmask = text(root.get("hostmask"));

      Map<String, String> tags = Map.of();
      JsonNode tagsNode = root.get("ircv3Tags");
      if (tagsNode != null && tagsNode.isObject()) {
        TreeMap<String, String> parsed = new TreeMap<>();
        tagsNode.fields().forEachRemaining(e -> {
          String key = Objects.toString(e.getKey(), "").trim();
          if (key.isEmpty()) return;
          parsed.put(key, text(e.getValue()));
        });
        if (!parsed.isEmpty()) tags = Map.copyOf(parsed);
      }

      if (hostmask.isBlank()) {
        hostmask = Objects.toString(tags.get("ircafe/hostmask"), "").trim();
      }
      if (hostmask.isBlank()) {
        hostmask = Objects.toString(tags.get("hostmask"), "").trim();
      }
      if (hostmask.isBlank()) {
        hostmask = Objects.toString(tags.get("userhost"), "").trim();
      }

      return new Meta(messageId, hostmask, tags);
    } catch (Exception ignored) {
      return EMPTY_META;
    }
  }

  private static String text(JsonNode n) {
    if (n == null || n.isNull()) return "";
    String s = n.asText("");
    return Objects.toString(s, "").trim();
  }

  private static boolean isServerEventLine(LogLine line) {
    if (line == null) return false;
    LogKind kind = line.kind();
    if (kind == null) kind = LogKind.STATUS;
    if (kind == LogKind.ERROR) return false;
    if (kind == LogKind.PRESENCE) return true;

    String from = fromToken(line.fromNick());
    if (SERVER_EVENT_FROM_TOKENS.contains(from)) return true;
    return kind == LogKind.STATUS;
  }

  private static boolean isProtocolDebugLine(LogLine line) {
    if (line == null) return false;
    LogKind kind = line.kind();
    if (kind == LogKind.CHAT || kind == LogKind.ACTION || kind == LogKind.SPOILER) return false;

    String from = fromToken(line.fromNick());
    if (PROTOCOL_FROM_TOKENS.contains(from)) return true;

    String text = Objects.toString(line.text(), "").trim();
    if (text.isEmpty()) return false;
    if (NUMERIC_REPLY_PREFIX.matcher(text).find()) return true;

    String lower = text.toLowerCase(Locale.ROOT);
    for (String marker : PROTOCOL_TEXT_MARKERS) {
      if (marker == null || marker.isBlank()) continue;
      if (lower.contains(marker)) return true;
    }
    return false;
  }

  private static String fromToken(String rawFrom) {
    String from = Objects.toString(rawFrom, "").trim().toLowerCase(Locale.ROOT);
    if (from.length() >= 2 && from.charAt(0) == '(' && from.charAt(from.length() - 1) == ')') {
      from = from.substring(1, from.length() - 1).trim();
    }
    return from;
  }

  private interface TextMatcher {
    boolean matches(String value);
  }

  private record Meta(String messageId, String hostmask, Map<String, String> tags) {}
}
