package cafe.woden.ircclient.ui.chat.embed;

import cafe.woden.ircclient.ui.SwingEdt;
import io.reactivex.rxjava3.disposables.Disposable;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;

/**
 * Inline link preview component inserted into the chat transcript StyledDocument.
 */
final class ChatLinkPreviewComponent extends JPanel {

  private static final int FALLBACK_MAX_W = 420;
  private static final int WIDTH_MARGIN_PX = 32;

  private static final int THUMB_SIZE = 96;
  private static final int MAX_TITLE_LINES = 2;
  private static final int MAX_DESC_LINES = 3;

  private final String url;
  private final LinkPreviewFetchService fetch;
  private final ImageFetchService imageFetch;

  private final JLabel status = new JLabel("Loading preview…");

  private JPanel card;
  private JLabel thumb;
  private JTextArea title;
  private JTextArea desc;
  private JLabel site;

  private Disposable sub;
  private Disposable thumbSub;

  private volatile int lastMaxW = -1;
  private java.awt.Component resizeListeningOn;
  private final java.awt.event.ComponentListener resizeListener = new java.awt.event.ComponentAdapter() {
    @Override
    public void componentResized(java.awt.event.ComponentEvent e) {
      SwingUtilities.invokeLater(ChatLinkPreviewComponent.this::layoutForCurrentWidth);
    }
  };

  ChatLinkPreviewComponent(String url, LinkPreviewFetchService fetch, ImageFetchService imageFetch) {
    super(new FlowLayout(FlowLayout.LEFT, 0, 0));
    this.url = url;
    this.fetch = fetch;
    this.imageFetch = imageFetch;

    setOpaque(false);
    setBorder(BorderFactory.createEmptyBorder(2, 0, 6, 0));

    status.setOpaque(false);
    status.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 2));
    add(status);

    beginLoad();
  }

  private void beginLoad() {
    if (fetch == null) {
      status.setText("(link previews disabled)");
      return;
    }

    sub = fetch.fetch(url)
        .observeOn(SwingEdt.scheduler())
        .subscribe(
            this::renderPreview,
            err -> {
              status.setText("");
              status.setText("(preview failed)");
              status.setToolTipText(url + "\n" + err);
            }
        );
  }

  private void renderPreview(LinkPreview p) {
    removeAll();

    card = new JPanel(new BorderLayout(10, 0));
    card.setOpaque(true);
    card.setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createMatteBorder(1, 4, 1, 1, borderColor()),
        BorderFactory.createEmptyBorder(8, 10, 8, 10)
    ));

    // Left thumbnail (optional)
    thumb = new JLabel();
    thumb.setPreferredSize(new Dimension(THUMB_SIZE, THUMB_SIZE));
    thumb.setMinimumSize(new Dimension(THUMB_SIZE, THUMB_SIZE));
    thumb.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));

    JPanel right = new JPanel();
    right.setOpaque(false);
    right.setLayout(new javax.swing.BoxLayout(right, javax.swing.BoxLayout.Y_AXIS));

    site = new JLabel(safe(p.siteName()));
    site.setOpaque(false);
    site.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));

    title = textArea(safe(p.title()), true);
    desc = textArea(safe(p.description()), false);

    right.add(site);
    if (!title.getText().isBlank()) right.add(title);
    if (!desc.getText().isBlank()) {
      right.add(javax.swing.Box.createVerticalStrut(6));
      right.add(desc);
    }

    // Only add thumb if we actually have an image URL.
    if (p.imageUrl() != null && !p.imageUrl().isBlank()) {
      card.add(thumb, BorderLayout.WEST);
      loadThumbnail(p.imageUrl());
    }
    card.add(right, BorderLayout.CENTER);

    // Interactions
    String targetUrl = safe(p.url()) != null ? p.url() : url;
    card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    card.setToolTipText(targetUrl);
    installPopup(card, targetUrl);
    card.addMouseListener(new java.awt.event.MouseAdapter() {
      @Override
      public void mouseClicked(java.awt.event.MouseEvent e) {
        if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 1) {
          openUrl(targetUrl);
        }
      }
    });

    add(card);
    layoutForCurrentWidth();
    revalidate();
    repaint();
  }

  private void loadThumbnail(String imageUrl) {
    if (imageFetch == null) return;
    thumbSub = imageFetch.fetch(imageUrl)
        .observeOn(SwingEdt.scheduler())
        .subscribe(
            bytes -> {
              try {
                DecodedImage d = ImageDecodeUtil.decode(imageUrl, bytes);
                java.awt.image.BufferedImage img = null;
                if (d instanceof StaticImageDecoded st) {
                  img = st.image();
                } else if (d instanceof AnimatedGifDecoded gif && !gif.frames().isEmpty()) {
                  img = gif.frames().get(0);
                }
                if (img != null) {
                  java.awt.image.BufferedImage scaled = ImageScaleUtil.scaleDownToWidth(img, THUMB_SIZE);
                  thumb.setIcon(new javax.swing.ImageIcon(scaled));
                }
              } catch (Exception ignored) {
                // ignore thumbnail failures
              }
            },
            err -> {
              // ignore thumbnail failures
            }
        );
  }

  @Override
  public void addNotify() {
    super.addNotify();
    hookResizeListener();
  }

  @Override
  public void removeNotify() {
    unhookResizeListener();
    super.removeNotify();
  }

  private void layoutForCurrentWidth() {
    if (card == null) return;
    int maxW = computeMaxInlineWidth();
    if (maxW <= 0) maxW = FALLBACK_MAX_W;

    if (Math.abs(maxW - lastMaxW) < 4 && lastMaxW > 0) return;
    lastMaxW = maxW;

    // Let the text areas wrap to the available width.
    int innerW = maxW;
    if (thumb.getParent() == card) {
      innerW = Math.max(120, maxW - THUMB_SIZE - 14);
    }
    title.setSize(new Dimension(innerW, Short.MAX_VALUE));
    desc.setSize(new Dimension(innerW, Short.MAX_VALUE));

    // Clamp heights a bit so giant descriptions don't take over the transcript.
    clampLines(title, MAX_TITLE_LINES);
    clampLines(desc, MAX_DESC_LINES);

    card.setPreferredSize(new Dimension(maxW, card.getPreferredSize().height));
    revalidate();
  }

  private static JTextArea textArea(String text, boolean bold) {
    JTextArea ta = new JTextArea(text == null ? "" : text);
    ta.setOpaque(false);
    ta.setEditable(false);
    ta.setFocusable(false);
    ta.setLineWrap(true);
    ta.setWrapStyleWord(true);
    ta.setBorder(null);
    ta.setHighlighter(null);
    Font f = ta.getFont();
    if (bold) {
      ta.setFont(f.deriveFont(f.getStyle() | Font.BOLD));
    }
    return ta;
  }

  private static void clampLines(JTextArea ta, int maxLines) {
    if (ta == null || maxLines <= 0) return;
    int lh = ta.getFontMetrics(ta.getFont()).getHeight();
    int maxH = lh * maxLines + 4;
    Dimension pref = ta.getPreferredSize();
    if (pref.height > maxH) {
      ta.setPreferredSize(new Dimension(pref.width, maxH));
      ta.setMaximumSize(new Dimension(Integer.MAX_VALUE, maxH));
    }
  }

  private int computeMaxInlineWidth() {
    JTextPane pane = (JTextPane) SwingUtilities.getAncestorOfClass(JTextPane.class, this);
    if (pane != null) {
      int w = pane.getVisibleRect().width;
      if (w <= 0) w = pane.getWidth();
      if (w > 0) return Math.max(220, w - WIDTH_MARGIN_PX);
    }

    JScrollPane scroller = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
    if (scroller != null) {
      int w = scroller.getViewport().getExtentSize().width;
      if (w > 0) return Math.max(220, w - WIDTH_MARGIN_PX);
    }
    return FALLBACK_MAX_W;
  }

  private void hookResizeListener() {
    java.awt.Component target = (java.awt.Component) SwingUtilities.getAncestorOfClass(JTextPane.class, this);
    if (target == null) {
      target = (java.awt.Component) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
    }
    if (target == null) return;

    if (resizeListeningOn == target) return;
    unhookResizeListener();
    resizeListeningOn = target;
    target.addComponentListener(resizeListener);
  }

  private void unhookResizeListener() {
    if (resizeListeningOn != null) {
      try {
        resizeListeningOn.removeComponentListener(resizeListener);
      } catch (Exception ignored) {
      }
      resizeListeningOn = null;
    }
  }

  private void installPopup(JPanel panel, String targetUrl) {
    JPopupMenu menu = new JPopupMenu();

    JMenuItem open = new JMenuItem("Open link");
    open.addActionListener(e -> openUrl(targetUrl));

    JMenuItem copy = new JMenuItem("Copy link");
    copy.addActionListener(e -> {
      try {
        java.awt.Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(targetUrl), null);
      } catch (Exception ignored) {
      }
    });

    menu.add(open);
    menu.add(copy);

    panel.setComponentPopupMenu(menu);
  }

  private void openUrl(String targetUrl) {
    try {
      java.awt.Desktop.getDesktop().browse(new URI(targetUrl));
    } catch (Exception ignored) {
    }
  }

  private static String safe(String s) {
    if (s == null) return null;
    String t = s.trim();
    return t.isEmpty() ? null : t;
  }

  private static java.awt.Color borderColor() {
    java.awt.Color c = javax.swing.UIManager.getColor("Component.borderColor");
    if (c == null) c = javax.swing.UIManager.getColor("Separator.foreground");
    if (c == null) c = java.awt.Color.GRAY;
    return c;
  }
}
