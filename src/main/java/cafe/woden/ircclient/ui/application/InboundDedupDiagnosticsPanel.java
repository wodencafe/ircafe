package cafe.woden.ircclient.ui.application;

import cafe.woden.ircclient.diagnostics.RuntimeDiagnosticEvent;
import cafe.woden.ircclient.ui.icons.SvgIcons;
import io.reactivex.rxjava3.core.Flowable;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import net.miginfocom.swing.MigLayout;

/** Dedicated diagnostics UI for inbound duplicate suppression telemetry. */
public final class InboundDedupDiagnosticsPanel extends JPanel {
  private static final DateTimeFormatter EXPORT_TS_FMT =
      DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
          .withLocale(Locale.ROOT)
          .withZone(ZoneOffset.UTC);
  private static final DateTimeFormatter ROW_TS_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
          .withLocale(Locale.ROOT)
          .withZone(ZoneId.systemDefault());

  private static final Pattern DEDUP_RECORD_PATTERN =
      Pattern.compile(
          "InboundMessageDedupDiagnostics\\[serverId=([^,\\]]*), target=([^,\\]]*), eventType=([^,\\]]*), suppressedCount=(\\d+), suppressedTotal=(\\d+), messageIdSample=([^\\]]*)\\]");

  private static final String DEDUP_PAYLOAD_TYPE =
      "cafe.woden.ircclient.app.core.IrcMediator$InboundMessageDedupDiagnostics";

  private static final int ACTION_ICON_SIZE = 16;
  private static final Dimension ACTION_BUTTON_SIZE = new Dimension(28, 28);

  private final RuntimeEventsPanel eventsPanel;
  private final java.util.function.Supplier<List<RuntimeDiagnosticEvent>> sourceEventsSupplier;
  private final JButton exportSupportButton = new JButton();
  private volatile boolean exportInProgress;

  public InboundDedupDiagnosticsPanel(
      java.util.function.Supplier<List<RuntimeDiagnosticEvent>> sourceEventsSupplier,
      Flowable<?> refreshTrigger) {
    super(new BorderLayout(0, 6));
    this.sourceEventsSupplier =
        Objects.requireNonNull(sourceEventsSupplier, "sourceEventsSupplier");
    this.eventsPanel =
        new RuntimeEventsPanel(
            "Inbound Dedup",
            "Suppressed inbound duplicate msgid activity (per server/target/event type).",
            this::filteredEvents,
            null,
            "inbound-dedup",
            refreshTrigger);
    add(eventsPanel, BorderLayout.CENTER);

    configureExportSupportButton();
    JPanel footer = new JPanel(new MigLayout("insets 0 8 8 8, fillx", "[]push[]", "[]"));
    footer.setOpaque(false);
    footer.add(exportSupportButton);
    footer.add(new JLabel("Export ZIP (CSV + summary) for support/debugging"), "alignx right");
    add(footer, BorderLayout.SOUTH);
  }

  public void refreshNow() {
    eventsPanel.refreshNow();
  }

  private void configureExportSupportButton() {
    exportSupportButton.setText("");
    exportSupportButton.setIcon(SvgIcons.action("copy", ACTION_ICON_SIZE));
    exportSupportButton.setDisabledIcon(SvgIcons.actionDisabled("copy", ACTION_ICON_SIZE));
    exportSupportButton.setToolTipText(
        "Export inbound dedup support bundle (CSV + aggregated summary)");
    exportSupportButton.setFocusable(false);
    exportSupportButton.setPreferredSize(ACTION_BUTTON_SIZE);
    exportSupportButton
        .getAccessibleContext()
        .setAccessibleName("Export inbound dedup support bundle");
    exportSupportButton.addActionListener(e -> exportSupportBundle());
    updateExportButtonState();
  }

  private void exportSupportBundle() {
    if (exportInProgress) return;
    List<RuntimeDiagnosticEvent> rows = filteredEvents();
    if (rows.isEmpty()) {
      JOptionPane.showMessageDialog(
          SwingUtilities.getWindowAncestor(this),
          "No inbound dedup rows are available to export yet.",
          "Export Support Bundle",
          JOptionPane.INFORMATION_MESSAGE);
      return;
    }
    setExportInProgress(true);
    CompletableFuture.supplyAsync(() -> createSupportBundle(rows))
        .whenComplete(
            (report, error) ->
                SwingUtilities.invokeLater(
                    () -> {
                      setExportInProgress(false);
                      if (error != null) {
                        showMultilineDialog(
                            "Export Error",
                            "Failed to export inbound dedup support bundle:\n\n"
                                + Objects.toString(error.getMessage(), ""),
                            JOptionPane.ERROR_MESSAGE);
                        return;
                      }
                      if (report == null) {
                        showMultilineDialog(
                            "Export Error",
                            "Failed to export inbound dedup support bundle: no report returned.",
                            JOptionPane.ERROR_MESSAGE);
                        return;
                      }
                      showMultilineDialog(
                          report.success() ? "Export Complete" : "Export Error",
                          report.summary(),
                          report.success()
                              ? JOptionPane.INFORMATION_MESSAGE
                              : JOptionPane.ERROR_MESSAGE);
                    }));
  }

  private void setExportInProgress(boolean inProgress) {
    exportInProgress = inProgress;
    updateExportButtonState();
  }

  private void updateExportButtonState() {
    exportSupportButton.setEnabled(!exportInProgress);
    exportSupportButton.setToolTipText(
        exportInProgress
            ? "Export in progress..."
            : "Export inbound dedup support bundle (CSV + aggregated summary)");
  }

  private List<RuntimeDiagnosticEvent> filteredEvents() {
    List<RuntimeDiagnosticEvent> source;
    try {
      source = sourceEventsSupplier.get();
    } catch (Exception e) {
      source = List.of();
    }
    if (source == null || source.isEmpty()) return List.of();
    ArrayList<RuntimeDiagnosticEvent> out = new ArrayList<>(source.size());
    for (RuntimeDiagnosticEvent event : source) {
      ParsedDedup parsed = parse(event);
      if (parsed == null) continue;
      String summary =
          parsed.serverId()
              + " "
              + parsed.target()
              + " "
              + parsed.eventType()
              + " suppressed="
              + parsed.suppressedCount()
              + " (total="
              + parsed.suppressedTotal()
              + ")";
      if (!parsed.messageIdSample().isBlank()) {
        summary += " msgid=" + abbreviate(parsed.messageIdSample(), 32);
      }
      out.add(
          new RuntimeDiagnosticEvent(
              event.at(),
              event.level(),
              "InboundDedup",
              summary,
              Objects.toString(event.details(), "")));
    }
    return List.copyOf(out);
  }

  private static ParsedDedup parse(RuntimeDiagnosticEvent event) {
    if (event == null) return null;
    String details = Objects.toString(event.details(), "");
    String summary = Objects.toString(event.summary(), "");
    if (!details.contains("payloadType=" + DEDUP_PAYLOAD_TYPE)
        && !summary.contains("InboundMessageDedupDiagnostics[")) {
      return null;
    }
    String raw = extractDedupRecord(summary, details);
    if (raw.isBlank()) return null;
    Matcher matcher = DEDUP_RECORD_PATTERN.matcher(raw);
    if (!matcher.find()) return null;
    String serverId = Objects.toString(matcher.group(1), "").trim();
    String target = Objects.toString(matcher.group(2), "").trim();
    String eventType = Objects.toString(matcher.group(3), "").trim();
    long suppressedCount = parseLong(matcher.group(4));
    long suppressedTotal = parseLong(matcher.group(5));
    String messageIdSample = Objects.toString(matcher.group(6), "").trim();
    if (serverId.isEmpty()) serverId = "(unknown-server)";
    if (target.isEmpty()) target = "(unknown-target)";
    if (eventType.isEmpty()) eventType = "(unknown-type)";
    return new ParsedDedup(
        serverId,
        target,
        eventType,
        Math.max(0L, suppressedCount),
        Math.max(0L, suppressedTotal),
        messageIdSample);
  }

  private static String extractDedupRecord(String summary, String details) {
    String s = Objects.toString(summary, "");
    int idx = s.indexOf("InboundMessageDedupDiagnostics[");
    if (idx >= 0) return s.substring(idx);
    String d = Objects.toString(details, "");
    String[] lines = d.split("\\R");
    for (String line : lines) {
      String trimmed = Objects.toString(line, "").trim();
      if (!trimmed.startsWith("payload=")) continue;
      int payloadIdx = trimmed.indexOf("InboundMessageDedupDiagnostics[");
      if (payloadIdx >= 0) return trimmed.substring(payloadIdx);
    }
    return "";
  }

  private static long parseLong(String raw) {
    try {
      return Long.parseLong(Objects.toString(raw, "").trim());
    } catch (Exception ignored) {
      return 0L;
    }
  }

  private static SupportBundleReport createSupportBundle(List<RuntimeDiagnosticEvent> rows) {
    List<RuntimeDiagnosticEvent> safeRows = rows == null ? List.of() : List.copyOf(rows);
    Instant startedAt = Instant.now();
    String baseName = "ircafe-inbound-dedup-support-" + EXPORT_TS_FMT.format(startedAt);
    Path exportDir = resolveSupportExportDirectory();
    Path stagingDir = exportDir.resolve(baseName);
    Path bundleZipPath = exportDir.resolve(baseName + ".zip");

    StringBuilder summary =
        new StringBuilder()
            .append("Inbound dedup support bundle")
            .append('\n')
            .append("Generated at: ")
            .append(ROW_TS_FMT.format(startedAt))
            .append('\n')
            .append("Rows: ")
            .append(safeRows.size())
            .append('\n');
    try {
      Files.createDirectories(exportDir);
      Files.createDirectories(stagingDir);

      writeTextFile(stagingDir.resolve("inbound-dedup-events.csv"), toCsv(safeRows));
      writeTextFile(stagingDir.resolve("inbound-dedup-summary.txt"), aggregateSummary(safeRows));
      writeTextFile(stagingDir.resolve("README.txt"), supportReadme());

      zipDirectory(stagingDir, bundleZipPath);
      deleteRecursively(stagingDir);

      summary.append("Bundle: ").append(bundleZipPath.toAbsolutePath());
      return new SupportBundleReport(bundleZipPath, summary.toString(), true);
    } catch (Exception e) {
      deleteRecursivelyQuietly(stagingDir);
      String err =
          "Failed to create support bundle: " + Objects.toString(e.getMessage(), e.toString());
      return new SupportBundleReport(null, summary.append(err).toString(), false);
    }
  }

  private static String toCsv(List<RuntimeDiagnosticEvent> rows) {
    StringBuilder out = new StringBuilder(4096);
    out.append(
        "time,level,serverId,target,eventType,suppressedCount,suppressedTotal,messageIdSample,summary\n");
    for (RuntimeDiagnosticEvent row : rows) {
      ParsedDedup parsed = parse(row);
      if (parsed == null) continue;
      out.append(csvCell(row.at() == null ? "" : ROW_TS_FMT.format(row.at()))).append(',');
      out.append(csvCell(row.level())).append(',');
      out.append(csvCell(parsed.serverId())).append(',');
      out.append(csvCell(parsed.target())).append(',');
      out.append(csvCell(parsed.eventType())).append(',');
      out.append(csvCell(Long.toString(parsed.suppressedCount()))).append(',');
      out.append(csvCell(Long.toString(parsed.suppressedTotal()))).append(',');
      out.append(csvCell(parsed.messageIdSample())).append(',');
      out.append(csvCell(row.summary())).append('\n');
    }
    return out.toString();
  }

  private static String aggregateSummary(List<RuntimeDiagnosticEvent> rows) {
    Map<String, Aggregate> byKey = new LinkedHashMap<>();
    for (RuntimeDiagnosticEvent row : rows) {
      ParsedDedup parsed = parse(row);
      if (parsed == null) continue;
      String key = parsed.serverId() + " | " + parsed.target() + " | " + parsed.eventType();
      Aggregate aggregate = byKey.computeIfAbsent(key, __ -> new Aggregate());
      aggregate.rows++;
      aggregate.suppressed += Math.max(0L, parsed.suppressedCount());
      aggregate.lastTotal = Math.max(aggregate.lastTotal, Math.max(0L, parsed.suppressedTotal()));
      if (!parsed.messageIdSample().isBlank()) {
        aggregate.samples.add(abbreviate(parsed.messageIdSample(), 64));
      }
    }
    if (byKey.isEmpty()) {
      return "No parsed inbound dedup rows were found.\n";
    }
    StringBuilder out = new StringBuilder(4096);
    out.append("Inbound dedup aggregate summary\n\n");
    byKey.forEach(
        (key, aggregate) -> {
          out.append(key)
              .append('\n')
              .append("  rows=")
              .append(aggregate.rows)
              .append(", suppressedSum=")
              .append(aggregate.suppressed)
              .append(", lastSuppressedTotal=")
              .append(aggregate.lastTotal)
              .append('\n');
          if (!aggregate.samples.isEmpty()) {
            out.append("  msgidSamples=").append(String.join(", ", aggregate.samples)).append('\n');
          }
          out.append('\n');
        });
    return out.toString();
  }

  private static String supportReadme() {
    return """
        IRCafe Inbound Dedup Support Bundle

        Files:
        - inbound-dedup-events.csv : raw rows rendered in the Inbound Dedup diagnostics table
        - inbound-dedup-summary.txt : aggregate totals grouped by server/target/eventType

        Generated by Application -> Inbound Dedup -> Export support bundle.
        """;
  }

  private static Path resolveSupportExportDirectory() {
    String userHome = Objects.toString(System.getProperty("user.home"), "").trim();
    if (!userHome.isEmpty()) {
      return Path.of(userHome, ".config", "ircafe", "diagnostics", "support");
    }
    return Path.of(System.getProperty("java.io.tmpdir"), "ircafe", "diagnostics", "support");
  }

  private static String csvCell(String raw) {
    String s = Objects.toString(raw, "");
    boolean needsQuotes =
        s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
    if (!needsQuotes) return s;
    return '"' + s.replace("\"", "\"\"") + '"';
  }

  private static void writeTextFile(Path path, String text) throws Exception {
    if (path == null) return;
    if (path.getParent() != null) {
      Files.createDirectories(path.getParent());
    }
    Files.writeString(
        path,
        Objects.toString(text, ""),
        StandardCharsets.UTF_8,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE);
  }

  private static void zipDirectory(Path sourceDir, Path zipPath) throws Exception {
    if (sourceDir == null || zipPath == null) return;
    if (zipPath.getParent() != null) {
      Files.createDirectories(zipPath.getParent());
    }
    try (ZipOutputStream zos =
            new ZipOutputStream(
                Files.newOutputStream(
                    zipPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE));
        Stream<Path> walk = Files.walk(sourceDir)) {
      for (Path p : (Iterable<Path>) walk::iterator) {
        if (p == null || Files.isDirectory(p)) continue;
        Path rel = sourceDir.relativize(p);
        String entryName = rel.toString().replace('\\', '/');
        ZipEntry entry = new ZipEntry(entryName);
        entry.setTime(Files.getLastModifiedTime(p).toMillis());
        zos.putNextEntry(entry);
        Files.copy(p, zos);
        zos.closeEntry();
      }
    }
  }

  private static void deleteRecursively(Path root) throws Exception {
    if (root == null || !Files.exists(root)) return;
    try (Stream<Path> walk = Files.walk(root)) {
      List<Path> all = walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList();
      for (Path p : all) {
        Files.deleteIfExists(p);
      }
    }
  }

  private static void deleteRecursivelyQuietly(Path root) {
    try {
      deleteRecursively(root);
    } catch (Exception ignored) {
    }
  }

  private static String abbreviate(String raw, int maxLen) {
    String s = Objects.toString(raw, "").trim();
    if (s.length() <= Math.max(0, maxLen)) return s;
    if (maxLen <= 3) return s.substring(0, Math.max(0, maxLen));
    return s.substring(0, maxLen - 3) + "...";
  }

  private void showMultilineDialog(String title, String body, int messageType) {
    JTextArea text = new JTextArea(Objects.toString(body, ""));
    text.setEditable(false);
    text.setLineWrap(true);
    text.setWrapStyleWord(true);
    text.setCaretPosition(0);
    JScrollPane scroll = new JScrollPane(text);
    scroll.setPreferredSize(new Dimension(860, 520));
    JOptionPane.showMessageDialog(
        SwingUtilities.getWindowAncestor(this), scroll, title, messageType);
  }

  private record ParsedDedup(
      String serverId,
      String target,
      String eventType,
      long suppressedCount,
      long suppressedTotal,
      String messageIdSample) {}

  private static final class Aggregate {
    long rows;
    long suppressed;
    long lastTotal;
    final List<String> samples = new ArrayList<>();
  }

  private record SupportBundleReport(Path bundlePath, String summary, boolean success) {}
}
