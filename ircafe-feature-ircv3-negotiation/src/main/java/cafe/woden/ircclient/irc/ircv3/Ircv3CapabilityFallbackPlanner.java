package cafe.woden.ircclient.irc.ircv3;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/** Plans fallback CAP requests after LS/NEW without owning request gates or transport. */
public final class Ircv3CapabilityFallbackPlanner {

  public static final String MESSAGE_TAGS = "message-tags";
  public static final String BATCH = "batch";
  public static final String CHATHISTORY = "chathistory";
  public static final String DRAFT_CHATHISTORY = "draft/chathistory";

  public record State(
      boolean messageTagsAcked,
      boolean batchAcked,
      boolean chatHistoryAcked,
      Set<String> pendingCapabilities) {

    public State {
      pendingCapabilities = normalizeNames(pendingCapabilities);
    }
  }

  public record Plan(boolean requestMessageTags, boolean requestBatch, String historyCapability) {

    public Plan {
      historyCapability = Objects.toString(historyCapability, "").trim();
    }

    public boolean requestHistory() {
      return !historyCapability.isEmpty();
    }
  }

  public Plan plan(Ircv3CapabilityLine line, State state) {
    Objects.requireNonNull(line, "line");
    Objects.requireNonNull(state, "state");
    if (!line.isAction("LS", "NEW")) {
      return new Plan(false, false, "");
    }

    Set<String> offered = new LinkedHashSet<>();
    for (String rawToken : line.tokens()) {
      Ircv3CapabilityToken.parse(rawToken)
          .map(Ircv3CapabilityToken::normalizedName)
          .ifPresent(offered::add);
    }

    boolean requestMessageTags =
        offered.contains(MESSAGE_TAGS)
            && !state.messageTagsAcked()
            && !state.pendingCapabilities().contains(MESSAGE_TAGS);
    boolean requestBatch =
        offered.contains(BATCH)
            && !state.batchAcked()
            && !state.pendingCapabilities().contains(BATCH);

    String historyCapability = "";
    // CHATHISTORY is still a draft. An advertised unprefixed name must not opt us into
    // a future protocol version, including when the draft request is already pending.
    if (!state.chatHistoryAcked() && offered.contains(DRAFT_CHATHISTORY)) {
      historyCapability = DRAFT_CHATHISTORY;
    }
    if (state.pendingCapabilities().contains(historyCapability)) {
      historyCapability = "";
    }

    return new Plan(requestMessageTags, requestBatch, historyCapability);
  }

  private static Set<String> normalizeNames(Set<String> rawNames) {
    if (rawNames == null || rawNames.isEmpty()) return Set.of();
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    for (String rawName : rawNames) {
      Ircv3CapabilityToken.parse(rawName)
          .map(Ircv3CapabilityToken::normalizedName)
          .ifPresent(normalized::add);
    }
    return Set.copyOf(normalized);
  }
}
