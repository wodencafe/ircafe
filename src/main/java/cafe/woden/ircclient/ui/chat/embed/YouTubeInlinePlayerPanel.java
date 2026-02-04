package cafe.woden.ircclient.ui.chat.embed;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/** Optional inline YouTube playback via JavaFX WebView. */
final class YouTubeInlinePlayerPanel extends JPanel {

  private final String videoId;

  YouTubeInlinePlayerPanel(String videoId) {
    super(new BorderLayout());
    this.videoId = videoId;
    setOpaque(false);
    // reasonable default; caller can override
    setPreferredSize(new Dimension(480, 270));
    init();
  }

  static boolean isSupported() {
    try {
      Class.forName("javafx.embed.swing.JFXPanel");
      Class.forName("javafx.application.Platform");
      Class.forName("javafx.scene.web.WebView");
      Class.forName("javafx.scene.Scene");
      return true;
    } catch (Throwable ignored) {
      return false;
    }
  }

  private void init() {
    if (!isSupported()) {
      add(new JLabel("Inline playback requires JavaFX (javafx-web).", SwingConstants.CENTER),
          BorderLayout.CENTER);
      return;
    }

    try {
      Class<?> jfxPanelClass = Class.forName("javafx.embed.swing.JFXPanel");
      Constructor<?> ctor = jfxPanelClass.getConstructor();
      Object jfxPanel = ctor.newInstance();

      add((Component) jfxPanel, BorderLayout.CENTER);

      Class<?> platformClass = Class.forName("javafx.application.Platform");
      Method runLater = platformClass.getMethod("runLater", Runnable.class);

      final Object jfxFinal = jfxPanel;
      final String embedUrl =
          "https://www.youtube.com/embed/" + videoId + "?autoplay=0&rel=0&modestbranding=1";

      runLater.invoke(
          null,
          (Runnable)
              () -> {
                try {
                  Class<?> webViewClass = Class.forName("javafx.scene.web.WebView");
                  Object webView = webViewClass.getConstructor().newInstance();

                  // webView.getEngine().load(embedUrl)
                  Object engine = webViewClass.getMethod("getEngine").invoke(webView);
                  engine.getClass().getMethod("load", String.class).invoke(engine, embedUrl);

                  Class<?> parentClass = Class.forName("javafx.scene.Parent");
                  Class<?> sceneClass = Class.forName("javafx.scene.Scene");
                  Object scene = sceneClass.getConstructor(parentClass).newInstance(webView);

                  jfxPanelClass.getMethod("setScene", sceneClass).invoke(jfxFinal, scene);
                } catch (Throwable ignored) {
                  // If the WebView fails to initialize, silently fail and keep the blank panel.
                }
              });
    } catch (Throwable t) {
      removeAll();
      add(
          new JLabel("Inline playback failed to initialize.", SwingConstants.CENTER),
          BorderLayout.CENTER);
    }
  }
}
