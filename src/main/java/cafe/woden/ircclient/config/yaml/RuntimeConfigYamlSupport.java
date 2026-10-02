package cafe.woden.ircclient.config.yaml;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import org.jmolecules.architecture.layered.InfrastructureLayer;
import org.slf4j.Logger;

/** Shared YAML document helpers for the focused runtime-configuration stores. */
@InfrastructureLayer
public final class RuntimeConfigYamlSupport {

  private RuntimeConfigYamlSupport() {}

  public static Optional<Object> readValue(
      Path file,
      RuntimeConfigDocumentStore documentStore,
      Logger log,
      String description,
      String... path) {
    try {
      if (file.toString().isBlank()) return Optional.empty();

      Map<String, Object> doc = documentStore.loadOrEmpty();
      return RuntimeConfigDocumentPathReader.readValue(doc, path);
    } catch (Exception e) {
      log.warn("[ircafe] Could not read {} from '{}'", description, file, e);
      return Optional.empty();
    }
  }

  public static Optional<Object> readExistingValue(
      Path file,
      RuntimeConfigDocumentStore documentStore,
      Logger log,
      String description,
      String... path) {
    try {
      if (file.toString().isBlank()) return Optional.empty();
      if (!documentStore.documentExists()) return Optional.empty();

      Map<String, Object> doc = documentStore.load();
      return RuntimeConfigDocumentPathReader.readValue(doc, path);
    } catch (Exception e) {
      log.warn("[ircafe] Could not read {} from '{}'", description, file, e);
      return Optional.empty();
    }
  }

  public static void putValue(
      Path file,
      RuntimeConfigDocumentStore documentStore,
      Logger log,
      String description,
      Object value,
      String... path) {
    mutateValue(
        file, documentStore, log, description, path, parent -> parent.put(last(path), value));
  }

  public static void removeValue(
      Path file,
      RuntimeConfigDocumentStore documentStore,
      Logger log,
      String description,
      String... path) {
    mutateValue(file, documentStore, log, description, path, parent -> parent.remove(last(path)));
  }

  public static void removeExistingValueAndPruneEmptyParents(
      Path file,
      RuntimeConfigDocumentStore documentStore,
      Logger log,
      String description,
      String... path) {
    try {
      if (file.toString().isBlank()) return;
      if (!documentStore.documentExists()) return;
      if (path.length == 0) {
        throw new IllegalArgumentException("Runtime config YAML path must not be empty");
      }

      Map<String, Object> doc = documentStore.load();
      String[] parentPath = Arrays.copyOf(path, path.length - 1);
      Map<String, Object> parent =
          parentPath.length == 0 ? doc : readMapPath(doc, parentPath).orElse(null);
      if (parent == null || !parent.containsKey(last(path))) return;

      parent.remove(last(path));
      if (parentPath.length > 0) {
        pruneEmptyMapPath(doc, parentPath);
      }
      documentStore.write(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not remove {} setting from '{}'", description, file, e);
    }
  }

  public static void mutateMap(
      Path file,
      RuntimeConfigDocumentStore documentStore,
      Logger log,
      String description,
      Consumer<Map<String, Object>> mutation,
      String... path) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = documentStore.loadOrEmpty();
      Map<String, Object> target = getOrCreateMapPath(doc, path);
      mutation.accept(target);

      documentStore.write(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist {} to '{}'", description, file, e);
    }
  }

  public static void mutateMapIfChanged(
      Path file,
      RuntimeConfigDocumentStore documentStore,
      Logger log,
      String description,
      Function<Map<String, Object>, Boolean> mutation,
      String... path) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = documentStore.loadOrEmpty();
      Map<String, Object> target = getOrCreateMapPath(doc, path);
      if (!Boolean.TRUE.equals(mutation.apply(target))) return;

      documentStore.write(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist {} to '{}'", description, file, e);
    }
  }

  public static void mutateMapAndRemoveIfEmpty(
      Path file,
      RuntimeConfigDocumentStore documentStore,
      Logger log,
      String description,
      Consumer<Map<String, Object>> mutation,
      String... path) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = documentStore.loadOrEmpty();
      Map<String, Object> target = getOrCreateMapPath(doc, path);
      mutation.accept(target);
      pruneEmptyMapPath(doc, path);

      documentStore.write(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist {} to '{}'", description, file, e);
    }
  }

  public static void mutateExistingMapAndRemoveIfEmpty(
      Path file,
      RuntimeConfigDocumentStore documentStore,
      Logger log,
      String description,
      Function<Map<String, Object>, Boolean> mutation,
      String... path) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = documentStore.loadOrEmpty();
      Optional<Map<String, Object>> target = readMapPath(doc, path);
      if (target.isEmpty()) return;
      if (!Boolean.TRUE.equals(mutation.apply(target.get()))) return;

      pruneEmptyMapPath(doc, path);
      documentStore.write(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist {} to '{}'", description, file, e);
    }
  }

  public static void mutateDocument(
      Path file,
      RuntimeConfigDocumentStore documentStore,
      Logger log,
      String description,
      Function<Map<String, Object>, Boolean> mutation) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = documentStore.loadOrEmpty();
      if (!Boolean.TRUE.equals(mutation.apply(doc))) return;

      documentStore.write(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist {} to '{}'", description, file, e);
    }
  }

  public static Map<String, Object> getOrCreateMapPath(Map<String, Object> root, String... path) {
    Map<String, Object> current = root;
    for (String segment : path) {
      current = getOrCreateMap(current, segment);
    }
    return current;
  }

  public static Optional<Map<String, Object>> readMapPath(
      Map<String, Object> root, String... path) {
    Map<String, Object> current = root;
    for (String segment : path) {
      Optional<Map<String, Object>> next = readMap(current, segment);
      if (next.isEmpty()) return Optional.empty();
      current = next.get();
    }
    return Optional.of(current);
  }

  @SuppressWarnings("unchecked")
  public static Map<String, Object> getOrCreateMap(Map<String, Object> parent, String key) {
    Object existing = parent.get(key);
    if (existing instanceof Map<?, ?> map) return (Map<String, Object>) map;
    Map<String, Object> created = new LinkedHashMap<>();
    parent.put(key, created);
    return created;
  }

  @SuppressWarnings("unchecked")
  public static Optional<Map<String, Object>> readMap(Map<String, Object> parent, String key) {
    if (parent == null) return Optional.empty();

    Object existing = parent.get(key);
    if (existing instanceof Map<?, ?> map) return Optional.of((Map<String, Object>) map);
    return Optional.empty();
  }

  public static void removeIfEmpty(
      Map<String, Object> parent, String key, Map<String, Object> child) {
    if (parent != null && child != null && child.isEmpty()) {
      parent.remove(key);
    }
  }

  private static void pruneEmptyMapPath(Map<String, Object> doc, String[] path) {
    if (path.length == 0) {
      throw new IllegalArgumentException("Runtime config YAML path must not be empty");
    }

    List<Map<String, Object>> parents = new ArrayList<>(path.length);
    Map<String, Object> current = doc;
    for (String segment : path) {
      Optional<Map<String, Object>> child = readMap(current, segment);
      if (child.isEmpty()) return;
      parents.add(current);
      current = child.get();
    }

    for (int i = path.length - 1; i >= 0; i--) {
      Map<String, Object> parent = parents.get(i);
      Optional<Map<String, Object>> child = readMap(parent, path[i]);
      if (child.isEmpty() || !child.get().isEmpty()) return;
      parent.remove(path[i]);
    }
  }

  private static void mutateValue(
      Path file,
      RuntimeConfigDocumentStore documentStore,
      Logger log,
      String description,
      String[] path,
      Consumer<Map<String, Object>> mutation) {
    try {
      if (file.toString().isBlank()) return;

      Map<String, Object> doc = documentStore.loadOrEmpty();
      Map<String, Object> parent = parentMap(doc, path);
      mutation.accept(parent);

      documentStore.write(doc);
    } catch (Exception e) {
      log.warn("[ircafe] Could not persist {} setting to '{}'", description, file, e);
    }
  }

  private static Map<String, Object> parentMap(Map<String, Object> doc, String[] path) {
    if (path.length == 0) {
      throw new IllegalArgumentException("Runtime config YAML path must not be empty");
    }
    if (path.length == 1) return doc;

    String[] parentPath = Arrays.copyOf(path, path.length - 1);
    return getOrCreateMapPath(doc, parentPath);
  }

  private static String last(String[] path) {
    if (path.length == 0) {
      throw new IllegalArgumentException("Runtime config YAML path must not be empty");
    }
    return path[path.length - 1];
  }

  public static Optional<Boolean> asBoolean(Object value) {
    if (value instanceof Boolean b) return Optional.of(b);
    if (value instanceof String s) {
      String t = s.trim();
      if (t.equalsIgnoreCase("true")) return Optional.of(Boolean.TRUE);
      if (t.equalsIgnoreCase("false")) return Optional.of(Boolean.FALSE);
    }
    if (value instanceof Number n) {
      int i = n.intValue();
      if (i == 0) return Optional.of(Boolean.FALSE);
      if (i == 1) return Optional.of(Boolean.TRUE);
    }
    return Optional.empty();
  }

  @SuppressWarnings("unchecked")
  public static List<String> getOrCreateStringList(Map<String, Object> parent, String key) {
    Object existing = parent.get(key);
    if (existing instanceof List<?>) {
      return (List<String>) existing;
    }
    List<String> created = new ArrayList<>();
    parent.put(key, created);
    return created;
  }

  public static List<String> sanitizeStringList(Object raw) {
    if (!(raw instanceof List<?> list) || list.isEmpty()) return List.of();

    ArrayList<String> out = new ArrayList<>(list.size());
    for (Object entry : list) {
      String value = Objects.toString(entry, "").trim();
      if (!value.isEmpty()) out.add(value);
    }
    return out.isEmpty() ? List.of() : List.copyOf(out);
  }

  public static boolean containsIgnoreCase(List<String> values, String needle) {
    if (values == null || values.isEmpty()) return false;
    String normalizedNeedle = Objects.toString(needle, "").trim();
    if (normalizedNeedle.isEmpty()) return false;
    for (String value : values) {
      if (normalizedNeedle.equalsIgnoreCase(Objects.toString(value, "").trim())) return true;
    }
    return false;
  }

  public static boolean isEmptySettingValue(Object value) {
    if (value == null) return true;
    if (value instanceof CharSequence text) return text.toString().isBlank();
    if (value instanceof Collection<?> collection) return collection.isEmpty();
    return false;
  }

  public static Optional<Integer> asInt(Object value) {
    if (value instanceof Number n) return Optional.of(n.intValue());
    if (value instanceof String s) {
      String t = s.trim();
      if (t.isEmpty()) return Optional.empty();
      try {
        return Optional.of(Integer.parseInt(t));
      } catch (Exception ignored) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  public static Optional<Long> asLong(Object value) {
    if (value instanceof Number n) return Optional.of(n.longValue());
    if (value instanceof String s) {
      String t = s.trim();
      if (t.isEmpty()) return Optional.empty();
      try {
        return Optional.of(Long.parseLong(t));
      } catch (Exception ignored) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }
}
