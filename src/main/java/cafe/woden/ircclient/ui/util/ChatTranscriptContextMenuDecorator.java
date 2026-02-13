package cafe.woden.ircclient.ui.util;

import cafe.woden.ircclient.net.HttpLite;
import cafe.woden.ircclient.net.NetProxyContext;
import cafe.woden.ircclient.net.ProxyPlan;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.datatransfer.StringSelection;
import java.io.InputStream;
import java.net.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.function.Consumer;
import java.util.function.Function;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import java.awt.Point;
import javax.swing.text.Document;

/**
 * Decorates a chat transcript {@link JTextComponent} with a right-click context menu.
 * <ul>
   *   <li>Default: Copy / Select All / Find Text / Reload Recent History / Clear</li>
 *   <li>If right-clicking on a URL token: Open Link in Browser / Copy Link Address / Save Link As...</li>
 * </ul>
 */
public final class ChatTranscriptContextMenuDecorator implements AutoCloseable {

  private final JTextComponent transcript;
  private final Runnable openFind;
  private final Function<Point, String> urlAt;
  private final Function<Point, String> nickAt;
  private final Function<String, JPopupMenu> nickMenuFor;
  private final Consumer<String> openUrl;
  private final MouseAdapter mouse;

  // Optional: allow the caller to provide a per-view proxy plan (e.g., per server).
  // If null, fall back to the global NetProxyContext proxy.
  private final Supplier<ProxyPlan> proxyPlanSupplier;

  private final JPopupMenu menu = new JPopupMenu();
  private final JMenuItem copyItem = new JMenuItem("Copy");
  private final JMenuItem selectAllItem = new JMenuItem("Select All");
  private final JMenuItem findItem = new JMenuItem("Find Text");
  private final JMenuItem reloadRecentItem = new JMenuItem("Reload Recent History");
  private final JMenuItem clearItem = new JMenuItem("Clear");

  private volatile Runnable clearAction;
  private volatile Runnable reloadRecentAction;

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
      Function<Point, String> nickAt,
      Function<String, JPopupMenu> nickMenuFor,
      Consumer<String> openUrl,
      Runnable openFind,
      Supplier<ProxyPlan> proxyPlanSupplier
  ) {
    this.transcript = Objects.requireNonNull(transcript, "transcript");
    this.urlAt = urlAt;
    this.nickAt = nickAt;
    this.nickMenuFor = nickMenuFor;
    this.openUrl = openUrl;
    this.openFind = (openFind != null) ? openFind : () -> {};
    this.proxyPlanSupplier = proxyPlanSupplier;

    copyItem.addActionListener(this::onCopy);
    selectAllItem.addActionListener(this::onSelectAll);
    findItem.addActionListener(this::onFind);
    reloadRecentItem.addActionListener(this::onReloadRecent);
    clearItem.addActionListener(this::onClear);

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

        // If this click is on a nick token and a nick-menu factory is provided, prefer that menu.
        String nick = safeHit(nickAt, e.getPoint());
        if (nick != null && !nick.isBlank() && nickMenuFor != null) {
          JPopupMenu nickMenu = null;
          try {
            nickMenu = nickMenuFor.apply(nick);
          } catch (Exception ignored) {
          }
          if (nickMenu != null && nickMenu.getComponentCount() > 0) {
            currentPopupUrl = null;
            // If the menu instance was created before the Look & Feel was applied, it may appear unstyled.
            // Refresh UI delegates at show time to match the current FlatLaf theme.
            try {
              SwingUtilities.updateComponentTreeUI(nickMenu);
            } catch (Exception ignored) {
            }
            nickMenu.show(transcript, e.getX(), e.getY());
            return;
          }
        }

        String url = safeHit(urlAt, e.getPoint());
        currentPopupUrl = url;
        rebuildMenu(url);
        updateEnabledState(url);

        // This popup menu is constructed once and reused; refresh UI delegates at show time so it
        // matches the current Look & Feel (e.g., FlatLaf).
        try {
          SwingUtilities.updateComponentTreeUI(menu);
        } catch (Exception ignored) {
        }
        menu.show(transcript, e.getX(), e.getY());
      }
    };

    transcript.addMouseListener(this.mouse);
  }

  public static ChatTranscriptContextMenuDecorator decorate(JTextComponent transcript, Runnable openFind) {
    return new ChatTranscriptContextMenuDecorator(transcript, null, null, null, null, openFind, null);
  }

  public static ChatTranscriptContextMenuDecorator decorate(
      JTextComponent transcript,
      Function<Point, String> urlAt,
      Consumer<String> openUrl,
      Runnable openFind
  ) {
    return new ChatTranscriptContextMenuDecorator(transcript, urlAt, null, null, openUrl, openFind, null);
  }

  public static ChatTranscriptContextMenuDecorator decorate(
      JTextComponent transcript,
      Function<Point, String> urlAt,
      Function<Point, String> nickAt,
      Function<String, JPopupMenu> nickMenuFor,
      Consumer<String> openUrl,
      Runnable openFind
  ) {
    return new ChatTranscriptContextMenuDecorator(transcript, urlAt, nickAt, nickMenuFor, openUrl, openFind, null);
  }

  public static ChatTranscriptContextMenuDecorator decorate(
      JTextComponent transcript,
      Function<Point, String> urlAt,
      Function<Point, String> nickAt,
      Function<String, JPopupMenu> nickMenuFor,
      Consumer<String> openUrl,
      Runnable openFind,
      Supplier<ProxyPlan> proxyPlanSupplier
  ) {
    return new ChatTranscriptContextMenuDecorator(transcript, urlAt, nickAt, nickMenuFor, openUrl, openFind, proxyPlanSupplier);
  }

  public void setClearAction(Runnable clearAction) {
    this.clearAction = clearAction;
  }

  public void setReloadRecentAction(Runnable reloadRecentAction) {
    this.reloadRecentAction = reloadRecentAction;
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
      menu.addSeparator();
      menu.add(reloadRecentItem);
      menu.add(clearItem);
    } else {
      menu.add(copyItem);
      menu.add(selectAllItem);
      menu.addSeparator();
      menu.add(findItem);
      menu.addSeparator();
      menu.add(reloadRecentItem);
      menu.add(clearItem);
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

    try {
      Document doc = transcript.getDocument();
      boolean hasText = (doc != null && doc.getLength() > 0);
      boolean canReload = (reloadRecentAction != null);
      reloadRecentItem.setEnabled(canReload);
      clearItem.setEnabled(hasText);
    } catch (Exception ignored) {
      clearItem.setEnabled(true);
      reloadRecentItem.setEnabled(reloadRecentAction != null);
    }
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

  private void onReloadRecent(ActionEvent e) {
    // Reload recent history is intentionally implemented as: clear buffer first, then reload.
    try {
      clearBufferOnly();
    } catch (Exception ignored) {
    }

    try {
      Runnable reload = reloadRecentAction;
      if (reload != null) reload.run();
    } catch (Exception ignored) {
    }
  }

  private void onClear(ActionEvent e) {
    try {
      clearBufferOnly();
    } catch (Exception ignored) {
    }
  }

  private void clearBufferOnly() throws Exception {
    Runnable r = clearAction;
    if (r != null) {
      r.run();
      return;
    }
    Document doc = transcript.getDocument();
    if (doc == null) return;
    int len = doc.getLength();
    if (len <= 0) return;
    // Clear only the in-memory transcript buffer (the UI document).
    // This intentionally does not touch any persisted logs.
    doc.remove(0, len);
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
  try {
    URI uri = URI.create(url);

    // Use HttpURLConnection (via HttpLite) so SOCKS proxies work.
    // java.net.http.HttpClient does not support SOCKS proxies.
    ProxyPlan plan = null;
    try {
      if (proxyPlanSupplier != null) {
        plan = proxyPlanSupplier.get();
      }
    } catch (Exception ignored) {
    }

    if (plan == null) {
      plan = ProxyPlan.from(NetProxyContext.settings());
    }
    Proxy proxy = (plan.proxy() != null) ? plan.proxy() : Proxy.NO_PROXY;

    Map<String, String> headers = new HashMap<>();
    headers.put("User-Agent", DEFAULT_UA);
    headers.put("Accept", "*/*");
    headers.put("Accept-Language", "en-US,en;q=0.9");

    int connectTimeoutMs = Math.max(1, plan.connectTimeoutMs());
    int readTimeoutMs = Math.max(Math.max(1, plan.readTimeoutMs()), 120_000);

    HttpLite.Response<InputStream> resp = HttpLite.getStream(uri, headers, proxy, connectTimeoutMs, readTimeoutMs);
    int code = resp.statusCode();

    // Second attempt: some hosts require a plausible Referer (anti-hotlinking).
    if (code == 403 && uri.getHost() != null) {
      try (InputStream ignored = resp.body()) {
        // ensure we don't leak the first connection
      }
      String origin = uri.getScheme() + "://" + uri.getHost() + "/";
      headers.put("Referer", origin);
      resp = HttpLite.getStream(uri, headers, proxy, connectTimeoutMs, readTimeoutMs);
      code = resp.statusCode();
    }

    if (code < 200 || code >= 300) {
      try (InputStream ignored = resp.body()) {
        // ensure we don't leak the connection
      }
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
