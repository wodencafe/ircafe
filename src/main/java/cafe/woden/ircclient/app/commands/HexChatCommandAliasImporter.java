package cafe.woden.ircclient.app.commands;

import cafe.woden.ircclient.model.UserCommandAlias;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Imports HexChat-style {@code commands.conf} user-command aliases. */
public final class HexChatCommandAliasImporter {

  private static final Pattern COMMAND_NAME_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9_-]*$");

  private HexChatCommandAliasImporter() {}

  public static ImportResult importFile(Path file) throws IOException {
    if (file == null) return new ImportResult(List.of(), 0, 0, 0);
    return parseLines(Files.readAllLines(file, StandardCharsets.UTF_8));
  }

  static ImportResult parseLines(List<String> lines) {
    if (lines == null || lines.isEmpty()) {
      return new ImportResult(List.of(), 0, 0, 0);
    }

    Map<String, MutableAlias> byCommand = new LinkedHashMap<>();
    String pendingName = "";
    int mergedDuplicateCommands = 0;
    int translatedPlaceholders = 0;
    int skippedInvalidEntries = 0;

    for (String rawLine : lines) {
      String line = normalizeLine(rawLine);
      if (line.isEmpty() || line.charAt(0) == '#') continue;

      int split = firstWhitespace(line);
      if (split <= 0) continue;

      String key = line.substring(0, split);
      String value = line.substring(split).trim();

      if ("NAME".equalsIgnoreCase(key)) {
        pendingName = value;
        continue;
      }

      if (!"CMD".equalsIgnoreCase(key)) continue;
      if (pendingName.isBlank() || value.isBlank()) {
        pendingName = "";
        continue;
      }

      String commandName = normalizeCommandName(pendingName);
      pendingName = "";
      if (commandName == null) {
        skippedInvalidEntries++;
        continue;
      }

      TemplateTranslation translated = translateTemplate(value);
      translatedPlaceholders += translated.replacements();

      String commandKey = commandName.toLowerCase(Locale.ROOT);
      MutableAlias existing = byCommand.get(commandKey);
      if (existing == null) {
        byCommand.put(commandKey, new MutableAlias(commandName, translated.template()));
      } else {
        mergedDuplicateCommands++;
        existing.template = mergeTemplate(existing.template, translated.template());
      }
    }

    List<UserCommandAlias> aliases =
        byCommand.values().stream()
            .map(m -> new UserCommandAlias(true, m.command, m.template))
            .toList();

    return new ImportResult(
        List.copyOf(aliases),
        mergedDuplicateCommands,
        translatedPlaceholders,
        skippedInvalidEntries);
  }

  private static String normalizeLine(String raw) {
    String line = Objects.toString(raw, "");
    if (!line.isEmpty() && line.charAt(0) == '\uFEFF') {
      line = line.substring(1);
    }
    return line.trim();
  }

  private static int firstWhitespace(String s) {
    for (int i = 0; i < s.length(); i++) {
      if (Character.isWhitespace(s.charAt(i))) return i;
    }
    return -1;
  }

  private static String normalizeCommandName(String raw) {
    String command = Objects.toString(raw, "").trim();
    if (command.startsWith("/")) command = command.substring(1).trim();

    int split = firstWhitespace(command);
    if (split > 0) {
      command = command.substring(0, split).trim();
    }
    if (command.isEmpty()) return null;
    if (!COMMAND_NAME_PATTERN.matcher(command).matches()) return null;
    return command.toLowerCase(Locale.ROOT);
  }

  private static String mergeTemplate(String first, String second) {
    String a = Objects.toString(first, "").trim();
    String b = Objects.toString(second, "").trim();
    if (a.isEmpty()) return b;
    if (b.isEmpty()) return a;
    if (a.equals(b)) return a;
    return a + "; " + b;
  }

  private static TemplateTranslation translateTemplate(String template) {
    String src = Objects.toString(template, "");
    if (src.isEmpty()) return new TemplateTranslation("", 0);

    StringBuilder out = new StringBuilder(src.length() + 32);
    int replacements = 0;

    for (int i = 0; i < src.length(); i++) {
      char ch = src.charAt(i);
      if (ch == '%' && i + 1 < src.length()) {
        char next = src.charAt(i + 1);
        if (next == '%') {
          out.append("%%");
          i++;
          continue;
        }
        switch (Character.toLowerCase(next)) {
          case 't' -> {
            out.append("%hexchat_time");
            replacements++;
            i++;
            continue;
          }
          case 'm' -> {
            out.append("%hexchat_machine");
            replacements++;
            i++;
            continue;
          }
          case 'v' -> {
            out.append("%hexchat_version");
            replacements++;
            i++;
            continue;
          }
          default -> {
            // Keep original token as-is.
          }
        }
      }

      out.append(ch);
    }

    return new TemplateTranslation(out.toString(), replacements);
  }

  public record ImportResult(
      List<UserCommandAlias> aliases,
      int mergedDuplicateCommands,
      int translatedPlaceholders,
      int skippedInvalidEntries) {}

  private record TemplateTranslation(String template, int replacements) {}

  private static final class MutableAlias {
    private final String command;
    private String template;

    private MutableAlias(String command, String template) {
      this.command = Objects.toString(command, "");
      this.template = Objects.toString(template, "");
    }
  }
}
