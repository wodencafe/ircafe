package cafe.woden.ircclient.logging.viewer;

import cafe.woden.ircclient.logging.ChatLogRepository;
import cafe.woden.ircclient.logging.model.LogLine;
import cafe.woden.ircclient.logging.model.LogRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    TextMatcher hostmaskMatcher = compileMatcher(query.hostmaskPattern(), query.hostmaskMode(), "hostmask");
    TextMatcher channelMatcher = compileMatcher(query.channelPattern(), query.channelMode(), "channel");

    boolean hasPostFilters = nickMatcher != MATCH_ALL || hostmaskMatcher != MATCH_ALL || channelMatcher != MATCH_ALL;
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
      if (!nickMatcher.matches(fromNick)) continue;
      if (!hostmaskMatcher.matches(meta.hostmask())) continue;
      if (!channelMatcher.matches(target)) continue;

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

  private interface TextMatcher {
    boolean matches(String value);
  }

  private record Meta(String messageId, String hostmask, Map<String, String> tags) {}
}
