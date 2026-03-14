package cafe.woden.ircclient.irc.matrix;

import io.reactivex.rxjava3.core.Completable;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import lombok.AccessLevel;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
final class MatrixRawLookupCommandHandler {
  @FunctionalInterface
  interface WhowasInvoker {
    Completable invoke(String serverId, String target, int count);
  }

  @NonNull private final BiFunction<String, String, Completable> whoisInvoker;
  @NonNull private final BiFunction<String, String, Completable> requestNamesInvoker;
  @NonNull private final WhowasInvoker whowasInvoker;

  Completable handleWho(String serverId, List<String> arguments) {
    String token = argOrBlank(arguments, 0, "WHO requires a target");
    if (looksLikeMatrixUserId(token)) {
      return whoisInvoker.apply(serverId, token);
    }
    if (looksLikeMatrixRoomId(token)
        || looksLikeMatrixRoomAlias(token)
        || (token.startsWith("#") && token.indexOf(':') < 0)) {
      return requestNamesInvoker.apply(serverId, token);
    }
    throw new IllegalArgumentException("WHO supports Matrix user ids and room targets only");
  }

  Completable handleWhowas(String serverId, List<String> arguments) {
    String target = argOrBlank(arguments, 0, "WHOWAS requires a target");
    int count = 0;
    if (arguments != null && arguments.size() >= 2) {
      String token = normalize(arguments.get(1));
      if (!token.isEmpty()) {
        if (!looksLikeInteger(token)) {
          throw new IllegalArgumentException("WHOWAS count must be a positive integer");
        }
        count = parsePositiveInt(token);
      }
    }
    return whowasInvoker.invoke(serverId, target, count);
  }

  private static String argOrBlank(List<String> args, int index, String error) {
    if (args == null || index < 0 || index >= args.size()) {
      throw new IllegalArgumentException(error);
    }
    String value = normalize(args.get(index));
    if (value.isEmpty()) {
      throw new IllegalArgumentException(error);
    }
    return value;
  }

  private static int parsePositiveInt(String token) {
    String value = normalize(token);
    if (!looksLikeInteger(value)) {
      throw new IllegalArgumentException("WHOWAS count must be a positive integer");
    }
    try {
      int parsed = Integer.parseInt(value);
      if (parsed <= 0) {
        throw new IllegalArgumentException("WHOWAS count must be a positive integer");
      }
      return parsed;
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("WHOWAS count must be a positive integer", ex);
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

  private static boolean looksLikeMatrixRoomId(String token) {
    String value = normalize(token);
    if (!value.startsWith("!")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  private static boolean looksLikeMatrixUserId(String token) {
    String value = normalize(token);
    if (!value.startsWith("@")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  private static boolean looksLikeMatrixRoomAlias(String token) {
    String value = normalize(token);
    if (!value.startsWith("#")) return false;
    int colon = value.indexOf(':');
    return colon > 1 && colon < value.length() - 1;
  }

  private static String normalize(String value) {
    return Objects.toString(value, "").trim();
  }
}
