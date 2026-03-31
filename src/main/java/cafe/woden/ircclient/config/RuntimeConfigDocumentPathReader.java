package cafe.woden.ircclient.config;

import java.util.Map;
import java.util.Optional;

final class RuntimeConfigDocumentPathReader {

  private RuntimeConfigDocumentPathReader() {}

  static Optional<Object> readValue(Map<String, Object> doc, String... path) {
    Object current = doc;
    for (String segment : path) {
      if (!(current instanceof Map<?, ?> map)) {
        return Optional.empty();
      }
      if (!map.containsKey(segment)) {
        return Optional.empty();
      }
      current = map.get(segment);
    }
    return Optional.ofNullable(current);
  }
}
