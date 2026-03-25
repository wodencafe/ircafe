package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.app.commands.HexChatCommandAliasImporter;
import cafe.woden.ircclient.model.UserCommandAlias;
import java.awt.Component;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;

final class HexChatAliasImportDialogSupport {
  private HexChatAliasImportDialogSupport() {}

  static void importAliases(Component parent, UserCommandAliasesTableModel model, JTable table) {
    stopEditing(table);

    Component owner = SwingUtilities.getWindowAncestor(parent);
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle("Import HexChat commands.conf");
    chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
    chooser.setAcceptAllFileFilterUsed(true);

    File suggested = suggestedHexChatCommandsConfFile();
    if (suggested != null) {
      File parentDir = suggested.getParentFile();
      if (parentDir != null && parentDir.isDirectory()) {
        chooser.setCurrentDirectory(parentDir);
      }
      chooser.setSelectedFile(suggested);
    } else {
      chooser.setSelectedFile(new File("commands.conf"));
    }

    int result = chooser.showOpenDialog(owner);
    if (result != JFileChooser.APPROVE_OPTION) return;

    File selected = chooser.getSelectedFile();
    if (selected == null) return;

    HexChatCommandAliasImporter.ImportResult imported;
    try {
      imported = HexChatCommandAliasImporter.importFile(selected.toPath());
    } catch (Exception ex) {
      JOptionPane.showMessageDialog(
          owner,
          "Could not import HexChat aliases from:\n" + selected + "\n\n" + ex.getMessage(),
          "Import failed",
          JOptionPane.ERROR_MESSAGE);
      return;
    }

    if (imported.aliases().isEmpty()) {
      JOptionPane.showMessageDialog(
          owner,
          "No aliases were found in the selected file.",
          "HexChat import",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }

    Set<String> existing = new HashSet<>();
    for (UserCommandAlias alias : model.snapshot()) {
      String key = normalizeAliasCommandKey(alias != null ? alias.name() : null);
      if (!key.isEmpty()) existing.add(key);
    }

    int added = 0;
    int skippedExisting = 0;
    int firstAdded = -1;
    for (UserCommandAlias alias : imported.aliases()) {
      String key = normalizeAliasCommandKey(alias != null ? alias.name() : null);
      if (key.isEmpty()) continue;
      if (existing.contains(key)) {
        skippedExisting++;
        continue;
      }
      int idx = model.addAlias(alias);
      if (firstAdded < 0) firstAdded = idx;
      existing.add(key);
      added++;
    }

    if (firstAdded >= 0) {
      int view = table.convertRowIndexToView(firstAdded);
      if (view >= 0) {
        table.getSelectionModel().setSelectionInterval(view, view);
        table.scrollRectToVisible(table.getCellRect(view, 0, true));
      }
    }

    JOptionPane.showMessageDialog(
        owner,
        buildSummary(imported, added, skippedExisting),
        "HexChat import complete",
        JOptionPane.INFORMATION_MESSAGE);
  }

  private static void stopEditing(JTable table) {
    if (table == null || !table.isEditing()) return;
    try {
      table.getCellEditor().stopCellEditing();
    } catch (Exception ignored) {
    }
  }

  private static String buildSummary(
      HexChatCommandAliasImporter.ImportResult imported, int added, int skippedExisting) {
    StringBuilder summary = new StringBuilder();
    if (added > 0) {
      summary.append("Imported ").append(added).append(" alias");
      if (added != 1) summary.append('e').append('s');
      summary.append('.');
    } else {
      summary.append("No new aliases were imported.");
    }

    if (skippedExisting > 0) {
      summary.append("\nSkipped ").append(skippedExisting).append(" alias");
      if (skippedExisting != 1) summary.append('e').append('s');
      summary.append(" because the command name already exists.");
    }

    if (imported.mergedDuplicateCommands() > 0) {
      summary
          .append("\nMerged ")
          .append(imported.mergedDuplicateCommands())
          .append(" duplicate command");
      if (imported.mergedDuplicateCommands() != 1) summary.append('s');
      summary.append(" from HexChat.");
    }

    if (imported.translatedPlaceholders() > 0) {
      summary
          .append("\nTranslated ")
          .append(imported.translatedPlaceholders())
          .append(" HexChat placeholder");
      if (imported.translatedPlaceholders() != 1) summary.append('s');
      summary.append(" (%t/%m/%v).");
    }

    if (imported.skippedInvalidEntries() > 0) {
      summary
          .append("\nSkipped ")
          .append(imported.skippedInvalidEntries())
          .append(" invalid command name");
      if (imported.skippedInvalidEntries() != 1) summary.append('s');
      summary.append('.');
    }
    return summary.toString();
  }

  private static String normalizeAliasCommandKey(String raw) {
    String command = Objects.toString(raw, "").trim();
    if (command.startsWith("/")) command = command.substring(1).trim();
    int split = command.indexOf(' ');
    if (split >= 0) command = command.substring(0, split).trim();
    return command.toLowerCase(Locale.ROOT);
  }

  private static File suggestedHexChatCommandsConfFile() {
    String home = Objects.toString(System.getProperty("user.home"), "").trim();
    if (home.isEmpty()) return null;

    Path userHome = Path.of(home);
    List<Path> candidates =
        List.of(
            userHome.resolve(".config").resolve("hexchat").resolve("commands.conf"),
            userHome.resolve(".xchat2").resolve("commands.conf"),
            userHome
                .resolve("AppData")
                .resolve("Roaming")
                .resolve("HexChat")
                .resolve("commands.conf"));

    for (Path candidate : candidates) {
      if (candidate != null && Files.isRegularFile(candidate)) return candidate.toFile();
    }
    return candidates.getFirst().toFile();
  }
}
