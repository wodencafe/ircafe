package cafe.woden.ircclient.notify.sound;

import cafe.woden.ircclient.config.RuntimeConfigStore;
import cafe.woden.ircclient.util.VirtualThreads;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.beans.PropertyChangeListener;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class NotificationSoundService {

  private static final Logger log = LoggerFactory.getLogger(NotificationSoundService.class);

  private static final Duration MIN_INTERVAL = Duration.ofMillis(500);
  private static final long CLIP_FINISH_GRACE_MS = 1_500L;
  private static final long CLIP_WAIT_MIN_MS = 2_000L;
  private static final long CLIP_WAIT_MAX_MS = 30_000L;

  private final ExecutorService executor = VirtualThreads.newSingleThreadExecutor("notification-sound-thread");

  private final NotificationSoundSettingsBus settingsBus;
  private final RuntimeConfigStore runtimeConfig;
  private final PropertyChangeListener settingsListener;

  private final AtomicReference<Instant> lastPlayed =
      new AtomicReference<>(Instant.EPOCH);

  /** Global sound enable toggle (Phase 2: defaults to enabled, UI/persistence later). */
  private volatile boolean enabled = true;

  /** Single globally selected sound (Phase 2). */
  private volatile BuiltInSound selectedSound = BuiltInSound.NOTIF_1;

  /** If true, play a user-provided sound file from the runtime config directory. */
  private volatile boolean useCustom = false;

  /** Resolved absolute path for the custom sound file (when enabled). */
  private volatile Path customSoundPath;

  public NotificationSoundService(NotificationSoundSettingsBus settingsBus, RuntimeConfigStore runtimeConfig) {
    this.settingsBus = settingsBus;
    this.runtimeConfig = runtimeConfig;

    NotificationSoundSettings seed = settingsBus != null ? settingsBus.get() : null;
    applySettings(seed);

    this.settingsListener = evt -> {
      if (evt == null) return;
      if (!NotificationSoundSettingsBus.PROP_NOTIFICATION_SOUND_SETTINGS.equals(evt.getPropertyName())) return;
      Object v = evt.getNewValue();
      if (v instanceof NotificationSoundSettings s) {
        applySettings(s);
      }
    };

    if (settingsBus != null) {
      settingsBus.addListener(settingsListener);
    }
  }

  /**
   * Play the currently selected built-in notification sound.
   */
  public void play() {
    if (!enabled) {
      return;
    }

    if (useCustom && customSoundPath != null && Files.exists(customSoundPath)) {
      playFile(customSoundPath, false);
      return;
    }

    if (selectedSound == null) return;
    playResource(selectedSound.resourcePath(), false);
  }

  /**
   * Play a one-off sound override for a specific notification event.
   */
  public void playOverride(String soundId, boolean useCustom, String customPath) {
    if (!enabled) {
      return;
    }

    if (useCustom) {
      Path overridePath = resolveCustomPath(customPath);
      if (overridePath != null && Files.exists(overridePath)) {
        playFile(overridePath, false);
        return;
      }
    }

    BuiltInSound override = BuiltInSound.fromId(soundId);
    playResource(override.resourcePath(), false);
  }

  /**
   * Play the given sound for preview/testing, even if sounds are disabled.
   */
  public void preview(BuiltInSound sound) {
    if (sound == null) return;
    playResource(sound.resourcePath(), true);
  }

  /** Play the configured custom file (if any) for preview/testing. */
  public void previewCustom() {
    Path p = this.customSoundPath;
    if (p == null || !Files.exists(p)) return;
    playFile(p, true);
  }

  /** Play a specific custom file (relative to the runtime config directory) for preview/testing. */
  public void previewCustom(String relativePath) {
    Path p = resolveCustomPath(relativePath);
    if (p == null || !Files.exists(p)) return;
    playFile(p, true);
  }

  private void applySettings(NotificationSoundSettings s) {
    if (s == null) {
      this.enabled = true;
      this.selectedSound = BuiltInSound.NOTIF_1;
      this.useCustom = false;
      this.customSoundPath = null;
      return;
    }
    this.enabled = s.enabled();
    this.selectedSound = BuiltInSound.fromId(s.soundId());

    this.useCustom = s.useCustom();
    this.customSoundPath = resolveCustomPath(s.customPath());
  }

  private Path resolveCustomPath(String relativePath) {
    if (relativePath == null || relativePath.isBlank()) return null;
    try {
      Path cfg = runtimeConfig != null ? runtimeConfig.runtimeConfigPath() : null;
      Path base = cfg != null ? cfg.getParent() : null;
      if (base == null) return null;

      Path resolved = base.resolve(relativePath).normalize();

      // Prevent path traversal outside the runtime config directory.
      if (!resolved.startsWith(base.normalize())) {
        return null;
      }
      return resolved;
    } catch (Exception e) {
      return null;
    }
  }

  private void playResource(String resourcePath, boolean bypassLimiter) {
    executor.submit(() -> {
      if (!bypassLimiter && !canPlay()) {
        return;
      }

      try {
        URL resource = getClass()
            .getClassLoader()
            .getResource(resourcePath);

        if (resource == null) {
          log.debug("Sound resource not found: {}", resourcePath);
          return;
        }

        try (AudioInputStream originalStream =
            AudioSystem.getAudioInputStream(
                new BufferedInputStream(resource.openStream()))) {

          playDecoded(originalStream);
          lastPlayed.set(Instant.now());
        }

      } catch (Exception e) {
        // Don't let audio failures crash or spam logs; debug is enough.
        log.debug("Failed to play notification sound: {}", resourcePath, e);
      }
    });
  }

  private void playFile(Path path, boolean bypassLimiter) {
    executor.submit(() -> {
      if (!bypassLimiter && !canPlay()) {
        return;
      }

      if (path == null || !Files.exists(path)) {
        return;
      }

      try (AudioInputStream originalStream =
          AudioSystem.getAudioInputStream(path.toFile())) {

        playDecoded(originalStream);
        lastPlayed.set(Instant.now());

      } catch (Exception e) {
        log.debug("Failed to play custom notification sound: {}", path, e);
      }
    });
  }

  private void playDecoded(AudioInputStream originalStream) throws Exception {
    AudioFormat baseFormat = originalStream.getFormat();

    boolean needsDecode = baseFormat.getEncoding() != AudioFormat.Encoding.PCM_SIGNED
        || baseFormat.getSampleSizeInBits() != 16;

    AudioInputStream decodedStream = originalStream;

    if (needsDecode) {
      AudioFormat decodedFormat = new AudioFormat(
          AudioFormat.Encoding.PCM_SIGNED,
          baseFormat.getSampleRate(),
          16,
          baseFormat.getChannels(),
          baseFormat.getChannels() * 2,
          baseFormat.getSampleRate(),
          false
      );
      decodedStream = AudioSystem.getAudioInputStream(decodedFormat, originalStream);
    }

    try (AudioInputStream toPlay = decodedStream) {
      Clip clip = AudioSystem.getClip();
      CountDownLatch finished = new CountDownLatch(1);
      LineListener listener = event -> {
        if (event == null) return;
        LineEvent.Type t = event.getType();
        if (t == LineEvent.Type.STOP || t == LineEvent.Type.CLOSE) {
          finished.countDown();
        }
      };

      try {
        clip.addLineListener(listener);
        clip.open(toPlay);
        clip.setFramePosition(0);
        clip.start();

        long durationMs = TimeUnit.MICROSECONDS.toMillis(Math.max(0L, clip.getMicrosecondLength()));
        long waitMs = Math.max(CLIP_WAIT_MIN_MS, Math.min(CLIP_WAIT_MAX_MS, durationMs + CLIP_FINISH_GRACE_MS));
        try {
          finished.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ie) {
          Thread.currentThread().interrupt();
        }
      } finally {
        try {
          clip.removeLineListener(listener);
        } catch (Exception ignored) {
        }
        try {
          if (clip.isRunning()) clip.stop();
        } catch (Exception ignored) {
        }
        try {
          if (clip.isOpen()) clip.close();
        } catch (Exception ignored) {
        }
      }
    }
  }

  private boolean canPlay() {
    Instant now = Instant.now();
    Instant last = lastPlayed.get();
    return Duration.between(last, now)
        .compareTo(MIN_INTERVAL) > 0;
  }

  @PreDestroy
  public void shutdown() {
    try {
      if (settingsBus != null && settingsListener != null) {
        settingsBus.removeListener(settingsListener);
      }
    } catch (Exception ignored) {
    }
    executor.shutdownNow();
  }
}
