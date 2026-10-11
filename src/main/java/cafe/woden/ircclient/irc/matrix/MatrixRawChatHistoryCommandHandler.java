package cafe.woden.ircclient.irc.matrix;

import io.reactivex.rxjava3.core.Completable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

/** Validates raw CHATHISTORY syntax and delegates to the existing history request operations. */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixRawChatHistoryCommandHandler {
  @FunctionalInterface
  interface SelectorInvoker {
    Completable invoke(String serverId, String target, String selector, int limit);
  }

  @FunctionalInterface
  interface RangeInvoker {
    Completable invoke(
        String serverId, String target, String startSelector, String endSelector, int limit);
  }

  @NonNull private final SelectorInvoker beforeInvoker;
  @NonNull private final SelectorInvoker latestInvoker;
  @NonNull private final RangeInvoker betweenInvoker;
  @NonNull private final SelectorInvoker aroundInvoker;

  Completable handle(String serverId, List<String> arguments) {
    List<String> args = arguments == null ? List.of() : arguments;
    if (args.isEmpty()) {
      throw new IllegalArgumentException(
          "CHATHISTORY requires a mode (BEFORE|LATEST|BETWEEN|AROUND)");
    }
    String mode = normalize(args.get(0)).toUpperCase(Locale.ROOT);
    return switch (mode) {
      case "BEFORE" -> handleBefore(serverId, args);
      case "LATEST" -> handleLatest(serverId, args);
      case "BETWEEN" -> handleBetween(serverId, args);
      case "AROUND" -> handleAround(serverId, args);
      default ->
          throw new IllegalArgumentException(
              "CHATHISTORY mode must be BEFORE, LATEST, BETWEEN, or AROUND");
    };
  }

  private Completable handleBefore(String serverId, List<String> args) {
    if (args.size() < 3 || args.size() > 4) {
      throw new IllegalArgumentException("CHATHISTORY BEFORE requires <target> <selector> [limit]");
    }
    String target = normalize(args.get(1));
    String selector = normalize(args.get(2));
    if (target.isEmpty() || selector.isEmpty()) {
      throw new IllegalArgumentException("CHATHISTORY BEFORE requires <target> <selector> [limit]");
    }
    int limit = args.size() > 3 ? parseRawChatHistoryLimit(args.get(3)) : 50;
    return beforeInvoker.invoke(serverId, target, selector, limit);
  }

  private Completable handleLatest(String serverId, List<String> args) {
    if (args.size() < 2 || args.size() > 4) {
      throw new IllegalArgumentException(
          "CHATHISTORY LATEST requires <target> [selector|*] [limit]");
    }
    String target = normalize(args.get(1));
    if (target.isEmpty()) {
      throw new IllegalArgumentException(
          "CHATHISTORY LATEST requires <target> [selector|*] [limit]");
    }

    String selector = "*";
    int limit = 50;
    if (args.size() >= 3) {
      String third = normalize(args.get(2));
      if (args.size() == 3 && looksLikeInteger(third)) {
        limit = parseRawChatHistoryLimit(third);
      } else {
        selector = third;
        if (selector.isEmpty()) {
          throw new IllegalArgumentException(
              "CHATHISTORY LATEST requires <target> [selector|*] [limit]");
        }
        if (args.size() >= 4) {
          limit = parseRawChatHistoryLimit(args.get(3));
        }
      }
    }
    return latestInvoker.invoke(serverId, target, selector, limit);
  }

  private Completable handleBetween(String serverId, List<String> args) {
    if (args.size() < 4 || args.size() > 5) {
      throw new IllegalArgumentException(
          "CHATHISTORY BETWEEN requires <target> <start-selector> <end-selector> [limit]");
    }
    String target = normalize(args.get(1));
    String startSelector = normalize(args.get(2));
    String endSelector = normalize(args.get(3));
    if (target.isEmpty() || startSelector.isEmpty() || endSelector.isEmpty()) {
      throw new IllegalArgumentException(
          "CHATHISTORY BETWEEN requires <target> <start-selector> <end-selector> [limit]");
    }
    int limit = args.size() > 4 ? parseRawChatHistoryLimit(args.get(4)) : 50;
    return betweenInvoker.invoke(serverId, target, startSelector, endSelector, limit);
  }

  private Completable handleAround(String serverId, List<String> args) {
    if (args.size() < 3 || args.size() > 4) {
      throw new IllegalArgumentException("CHATHISTORY AROUND requires <target> <selector> [limit]");
    }
    String target = normalize(args.get(1));
    String selector = normalize(args.get(2));
    if (target.isEmpty() || selector.isEmpty()) {
      throw new IllegalArgumentException("CHATHISTORY AROUND requires <target> <selector> [limit]");
    }
    int limit = args.size() > 3 ? parseRawChatHistoryLimit(args.get(3)) : 50;
    return aroundInvoker.invoke(serverId, target, selector, limit);
  }

  private static int parseRawChatHistoryLimit(String rawValue) {
    String token = normalize(rawValue);
    if (!looksLikeInteger(token)) {
      throw new IllegalArgumentException("CHATHISTORY limit must be a positive integer");
    }
    try {
      int parsed = Integer.parseInt(token);
      if (parsed <= 0) {
        throw new IllegalArgumentException("CHATHISTORY limit must be a positive integer");
      }
      return parsed;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("CHATHISTORY limit must be a positive integer", ex);
    }
  }

  private static boolean looksLikeInteger(String rawValue) {
    String token = normalize(rawValue);
    if (token.isEmpty()) {
      return false;
    }
    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      if (i == 0 && c == '+') {
        if (token.length() == 1) return false;
        continue;
      }
      if (c < '0' || c > '9') {
        return false;
      }
    }
    return true;
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
