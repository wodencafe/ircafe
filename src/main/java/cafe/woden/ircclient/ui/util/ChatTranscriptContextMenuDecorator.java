package cafe.woden.ircclient.ui.util;

import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.StringSelection;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Point;
import java.time.Duration;

/**
 * Decorates a chat transcript {@link JTextComponent} with a right-click context menu.
 * <ul>
 *   <li>Default: Copy / Select All / Find Text</li>
 *   <li>If right-clicking on a URL token: Open Link in Browser / Copy Link Address / Save Link As...</li>
 * </ul>
 */
public final class ChatTranscriptContextMenuDecorator implements AutoCloseable {

  private final JTextComponent transcript;
  private final Runnable openFind;
  private final Function<Point, String> urlAt;
  private final Consumer<String> openUrl;
  private final MouseAdapter mouse;

  private final JPopupMenu menu = new JPopupMenu();
  private final JMenuItem copyItem = new JMenuItem("Copy");
  private final JMenuItem selectAllItem = new JMenuItem("Select All");
  private final JMenuItem findItem = new JMenuItem("Find Text");

  private final JMenuItem openLinkItem = new JMenuItem("Open Link in Browser");
  private final JMenuItem copyLinkItem = new JMenuItem("Copy Link Address");
  private final JMenuItem saveLinkItem = new JMenuItem("Save Link As...");

  private volatile String currentPopupUrl;

  // Some sites reject programmatic downloads unless the request looks like a browser.
  private static final String DEFAULT_UA =
      "Mozilla/5.0 (X11; Linux x86_64; rv:120.0) Gecko/20100101 Firefox/120.0";

  private boolean closed = false;

  private ChatTranscriptContextMenuDecorator(
      JTextComponent transcript,
      Function<Point, String> urlAt,
      Consumer<String> openUrl,
      Runnable openFind
  ) {
    this.transcript = Objects.requireNonNull(transcript, "transcript");
    this.urlAt = urlAt;
    this.openUrl = openUrl;
    this.openFind = (openFind != null) ? openFind : () -> {};

    copyItem.addActionListener(this::onCopy);
    selectAllItem.addActionListener(this::onSelectAll);
    findItem.addActionListener(this::onFind);

    openLinkItem.addActionListener(this::onOpenLink);
    copyLinkItem.addActionListener(this::onCopyLink);
    saveLinkItem.addActionListener(this::onSaveLink);

    // Build initial (non-link) menu.
    rebuildMenu(null);

    this.mouse = new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        maybeShow(e);
      }

      @Override
      public void mouseReleased(MouseEvent e) {
        maybeShow(e);
      }

      private void maybeShow(MouseEvent e) {
        if (closed) return;
        if (e == null) return;

        // Cross-platform: popup trigger may fire on pressed or released.
        if (!e.isPopupTrigger() && !SwingUtilities.isRightMouseButton(e)) return;
        if (!transcript.isShowing() || !transcript.isEnabled()) return;

        String url = safeHit(urlAt, e.getPoint());
        currentPopupUrl = url;
        rebuildMenu(url);
        updateEnabledState(url);
        menu.show(transcript, e.getX(), e.getY());
      }
    };

    transcript.addMouseListener(this.mouse);
  }

  /**
   * Backward-compatible overload: installs the default Copy / Select All / Find menu.
   */
  public static ChatTranscriptContextMenuDecorator decorate(JTextComponent transcript, Runnable openFind) {
    return new ChatTranscriptContextMenuDecorator(transcript, null, null, openFind);
  }

  public static ChatTranscriptContextMenuDecorator decorate(
      JTextComponent transcript,
      Function<Point, String> urlAt,
      Consumer<String> openUrl,
      Runnable openFind
  ) {
    return new ChatTranscriptContextMenuDecorator(transcript, urlAt, openUrl, openFind);
  }

  private void rebuildMenu(String url) {
    menu.removeAll();

    if (url != null) {
      menu.add(openLinkItem);
      menu.add(copyLinkItem);
      menu.add(saveLinkItem);
      menu.addSeparator();
      menu.add(selectAllItem);
      menu.addSeparator();
      menu.add(findItem);
    } else {
      menu.add(copyItem);
      menu.add(selectAllItem);
      menu.addSeparator();
      menu.add(findItem);
    }
  }

  private void updateEnabledState(String url) {
    try {
      int start = transcript.getSelectionStart();
      int end = transcript.getSelectionEnd();
      copyItem.setEnabled(start != end);
    } catch (Exception ignored) {
      copyItem.setEnabled(true);
    }

    boolean hasUrl = (url != null && !url.isBlank());
    openLinkItem.setEnabled(hasUrl);
    copyLinkItem.setEnabled(hasUrl);
    saveLinkItem.setEnabled(hasUrl);
  }

  private void onCopy(ActionEvent e) {
    try {
      transcript.copy();
    } catch (Exception ignored) {
    }
  }

  private void onOpenLink(ActionEvent e) {
    String url = currentPopupUrl;
    if (url == null || url.isBlank()) return;
    if (openUrl == null) return;
    try {
      openUrl.accept(url);
    } catch (Exception ignored) {
    }
  }

  private void onCopyLink(ActionEvent e) {
    String url = currentPopupUrl;
    if (url == null || url.isBlank()) return;
    try {
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(url), null);
    } catch (Exception ignored) {
    }
  }

  private void onSaveLink(ActionEvent e) {
    String url = currentPopupUrl;
    if (url == null || url.isBlank()) return;

    try {
      String suggested = suggestFileName(url);
      JFileChooser chooser = new JFileChooser();
      chooser.setDialogTitle("Save Link As...");
      chooser.setSelectedFile(new java.io.File(suggested));
      int result = chooser.showSaveDialog(transcript);
      if (result != JFileChooser.APPROVE_OPTION) return;

      Path out = chooser.getSelectedFile().toPath();
      if (Files.exists(out)) {
        int overwrite = JOptionPane.showConfirmDialog(
            transcript,
            "File already exists. Overwrite?\n\n" + out,
            "Overwrite file?",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE
        );
        if (overwrite != JOptionPane.YES_OPTION) return;
      }

      // Download off the EDT.
      Thread t = new Thread(() -> downloadToFile(url, out), "ircafe-save-link");
      t.setDaemon(true);
      t.start();
    } catch (Exception ignored) {
    }
  }

  private void onSelectAll(ActionEvent e) {
    try {
      transcript.requestFocusInWindow();
      transcript.selectAll();
    } catch (Exception ignored) {
    }
  }

  private void onFind(ActionEvent e) {
    try {
      openFind.run();
    } catch (Exception ignored) {
    }
  }

  private static String safeHit(Function<Point, String> f, Point p) {
    if (f == null || p == null) return null;
    try {
      return f.apply(p);
    } catch (Exception ignored) {
      return null;
    }
  }

  private static String suggestFileName(String url) {
    try {
      URI u = URI.create(url);
      String path = u.getPath();
      if (path == null || path.isBlank()) return "download";
      int slash = path.lastIndexOf('/');
      String name = (slash >= 0) ? path.substring(slash + 1) : path;
      if (name == null || name.isBlank()) return "download";
      // Very small sanitation: avoid OS-path separators.
      name = name.replaceAll("[\\\\/:*?\"<>|]", "_");
      return name.isBlank() ? "download" : name;
    } catch (Exception ignored) {
      return "download";
    }
  }

private void downloadToFile(String url, Path out) {
  HttpClient client = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NORMAL)
      .connectTimeout(Duration.ofSeconds(20))
      .build();

  try {
    URI uri = URI.create(url);

    // First attempt: "browser-ish" headers (many hosts 403 Java defaults).
    HttpRequest.Builder b = HttpRequest.newBuilder(uri)
        .timeout(Duration.ofMinutes(2))
        .header("User-Agent", DEFAULT_UA)
        .header("Accept", "*/*")
        .header("Accept-Language", "en-US,en;q=0.9")
        .GET();

    HttpResponse<InputStream> resp = client.send(b.build(), HttpResponse.BodyHandlers.ofInputStream());
    int code = resp.statusCode();

    // Second attempt: some hosts require a plausible Referer (anti-hotlinking).
    if (code == 403 && uri.getHost() != null) {
      String origin = uri.getScheme() + "://" + uri.getHost() + "/";
      resp = client.send(b.header("Referer", origin).build(), HttpResponse.BodyHandlers.ofInputStream());
      code = resp.statusCode();
    }

    if (code < 200 || code >= 300) {
      int finalCode = code;
      SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
          transcript,
          "Failed to download link (HTTP " + finalCode + "):\n\n" + url
              + "\n\nNote: Some sites block direct downloads from apps unless you\n"
              + "are signed in (cookies) or they require special headers.\n"
              + "If this keeps happening, use 'Open Link in Browser' then Save As there.",
          "Save Link As...",
          JOptionPane.ERROR_MESSAGE
      ));
      return;
    }

    Path parent = out.getParent();
    if (parent != null) Files.createDirectories(parent);

    try (InputStream in = resp.body()) {
      Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
    }

    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
        transcript,
        "Saved to:\n\n" + out,
        "Save Link As...",
        JOptionPane.INFORMATION_MESSAGE
    ));
  } catch (Exception ex) {
    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(
        transcript,
        "Failed to download link:\n\n" + url + "\n\n" + ex.getMessage(),
        "Save Link As...",
        JOptionPane.ERROR_MESSAGE
    ));
  }
}



  @Override
  public void close() {
    if (closed) return;
    closed = true;
    try {
      transcript.removeMouseListener(mouse);
    } catch (Exception ignored) {
    }
  }
}
