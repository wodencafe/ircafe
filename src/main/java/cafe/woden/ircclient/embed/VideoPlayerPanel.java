package cafe.woden.ircclient.embed;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inline video player using JavaFX WebView.
 * Supports YouTube, Vimeo, and direct video files via HTML5 video element.
 */
public class VideoPlayerPanel extends JPanel {

    private static final Pattern YOUTUBE_PATTERN =
        Pattern.compile("(?:youtube\\.com/watch\\?v=|youtu\\.be/)([a-zA-Z0-9_-]{11})");
    private static final Pattern VIMEO_PATTERN =
        Pattern.compile("vimeo\\.com/(\\d+)");

    private final String videoUrl;
    private final String title;
    private JFXPanel jfxPanel;
    private WebView webView;
    private WebEngine webEngine;
    private volatile boolean initialized = false;

    // Fullscreen support
    private JDialog fullscreenDialog;
    private Container originalParent;
    private Object originalConstraints;
    private int originalIndex;

    public VideoPlayerPanel(String videoUrl, String title) {
        this.videoUrl = videoUrl;
        this.title = title;

        setLayout(new BorderLayout());
        setPreferredSize(new Dimension(400, 250));
        setBackground(Color.BLACK);

        // Initialize JavaFX
        initJavaFX();

        // Add controls
        add(createControlsPanel(), BorderLayout.SOUTH);
    }

    private void initJavaFX() {
        jfxPanel = new JFXPanel();
        add(jfxPanel, BorderLayout.CENTER);

        // Initialize JavaFX on the FX thread
        Platform.runLater(() -> {
            webView = new WebView();
            webEngine = webView.getEngine();

            // Enable JavaScript
            webEngine.setJavaScriptEnabled(true);

            StackPane root = new StackPane(webView);
            root.setStyle("-fx-background-color: black;");

            Scene scene = new Scene(root);
            jfxPanel.setScene(scene);

            // Load the video content
            loadVideo();
            initialized = true;
        });

        // Handle resize
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                if (initialized) {
                    Platform.runLater(() -> {
                        webView.setPrefSize(jfxPanel.getWidth(), jfxPanel.getHeight());
                    });
                }
            }
        });
    }

    private void loadVideo() {
        String html = generateVideoHtml();
        webEngine.loadContent(html);
    }

    private String generateVideoHtml() {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html><html><head>");
        html.append("<style>");
        html.append("* { margin: 0; padding: 0; box-sizing: border-box; }");
        html.append("html, body { width: 100%; height: 100%; background: #000; overflow: hidden; }");
        html.append("video, iframe { width: 100%; height: 100%; border: none; }");
        html.append("</style></head><body>");

        // Check for YouTube
        Matcher ytMatcher = YOUTUBE_PATTERN.matcher(videoUrl);
        if (ytMatcher.find()) {
            String videoId = ytMatcher.group(1);
            html.append("<iframe src=\"https://www.youtube.com/embed/")
                .append(escapeHtml(videoId))
                .append("?autoplay=1&rel=0\" ")
                .append("allow=\"accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture\" ")
                .append("allowfullscreen></iframe>");
        }
        // Check for Vimeo
        else {
            Matcher vimeoMatcher = VIMEO_PATTERN.matcher(videoUrl);
            if (vimeoMatcher.find()) {
                String videoId = vimeoMatcher.group(1);
                html.append("<iframe src=\"https://player.vimeo.com/video/")
                    .append(escapeHtml(videoId))
                    .append("?autoplay=1\" ")
                    .append("allow=\"autoplay; fullscreen; picture-in-picture\" ")
                    .append("allowfullscreen></iframe>");
            }
            // Direct video file
            else {
                html.append("<video controls autoplay>");
                html.append("<source src=\"").append(escapeHtml(videoUrl)).append("\">");
                html.append("Your browser does not support video playback.");
                html.append("</video>");
            }
        }

        html.append("</body></html>");
        return html.toString();
    }

    private JPanel createControlsPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 2));
        panel.setBackground(new Color(30, 30, 30));

        JButton fullscreenBtn = new JButton("Fullscreen");
        fullscreenBtn.setFocusable(false);
        fullscreenBtn.addActionListener(e -> toggleFullscreen());
        panel.add(fullscreenBtn);

        JButton closeBtn = new JButton("Close");
        closeBtn.setFocusable(false);
        closeBtn.addActionListener(e -> close());
        panel.add(closeBtn);

        return panel;
    }

    public void toggleFullscreen() {
        if (fullscreenDialog == null) {
            enterFullscreen();
        } else {
            exitFullscreen();
        }
    }

    private void enterFullscreen() {
        Window window = SwingUtilities.getWindowAncestor(this);
        if (window == null) return;

        // Store original parent info
        originalParent = getParent();
        if (originalParent instanceof JComponent jc) {
            LayoutManager lm = jc.getLayout();
            if (lm instanceof BorderLayout bl) {
                originalConstraints = bl.getConstraints(this);
            }
            originalIndex = java.util.Arrays.asList(jc.getComponents()).indexOf(this);
        }

        // Remove from original parent
        originalParent.remove(this);
        originalParent.revalidate();
        originalParent.repaint();

        // Create fullscreen dialog
        fullscreenDialog = new JDialog(window, "Video Player", Dialog.ModalityType.MODELESS);
        fullscreenDialog.setUndecorated(true);
        fullscreenDialog.setLayout(new BorderLayout());
        fullscreenDialog.add(this, BorderLayout.CENTER);

        // Get screen size
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        GraphicsDevice gd = ge.getDefaultScreenDevice();
        Rectangle bounds = gd.getDefaultConfiguration().getBounds();

        fullscreenDialog.setBounds(bounds);
        fullscreenDialog.setVisible(true);

        // ESC to exit fullscreen
        fullscreenDialog.getRootPane().registerKeyboardAction(
            e -> exitFullscreen(),
            KeyStroke.getKeyStroke("ESCAPE"),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        );
    }

    private void exitFullscreen() {
        if (fullscreenDialog == null) return;

        // Remove from dialog
        fullscreenDialog.remove(this);
        fullscreenDialog.dispose();
        fullscreenDialog = null;

        // Restore to original parent
        if (originalParent != null) {
            if (originalConstraints != null) {
                ((Container) originalParent).add(this, originalConstraints);
            } else {
                ((Container) originalParent).add(this, originalIndex);
            }
            originalParent.revalidate();
            originalParent.repaint();
        }
    }

    public void close() {
        if (fullscreenDialog != null) {
            exitFullscreen();
        }

        // Stop video
        Platform.runLater(() -> {
            if (webEngine != null) {
                webEngine.load("about:blank");
            }
        });

        // Remove from parent
        Container parent = getParent();
        if (parent != null) {
            parent.remove(this);
            parent.revalidate();
            parent.repaint();
        }
    }

    public void dispose() {
        close();
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }

    /**
     * Check if JavaFX platform is initialized, initializing if needed.
     */
    public static void ensureFxInitialized() {
        try {
            Platform.runLater(() -> {});
        } catch (IllegalStateException e) {
            // Platform not initialized, create a JFXPanel to initialize it
            new JFXPanel();
        }
    }
}
