package cafe.woden.ircclient.ui.tray;

import cafe.woden.ircclient.app.TargetCoordinator;
import cafe.woden.ircclient.app.TargetRef;
import cafe.woden.ircclient.notify.sound.NotificationSoundService;
import cafe.woden.ircclient.ui.MainFrame;
import cafe.woden.ircclient.ui.StatusBar;
import cafe.woden.ircclient.ui.tray.dbus.GnomeDbusNotificationBackend;
import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import cafe.woden.ircclient.ui.tray.dbus.GnomeDbusNotificationBackend;
import cafe.woden.ircclient.util.RxVirtualSchedulers;
import com.sshtools.twoslices.Toast;
import com.sshtools.twoslices.ToastType;
import dorkbox.notify.Notify;
import dorkbox.notify.Position;
import dorkbox.notify.Theme;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import jakarta.annotation.PreDestroy;
import java.awt.Frame;
import java.awt.Toolkit;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Desktop notifications for tray users.
 *
 * <p>We intentionally avoid hard dependencies here. On Linux we try {@code notify-send}, on macOS
 * we try {@code osascript}. If neither is available we fall back to a simple beep.
 */
@Component
public class TrayNotificationService {
  private static final Logger log = LoggerFactory.getLogger(TrayNotificationService.class);

  private static final Duration CONTENT_DEDUPE_WINDOW = Duration.ofSeconds(2);
  private static final Duration RATE_WINDOW = Duration.ofSeconds(10);
  private static final int PER_TARGET_MAX_PER_WINDOW = 3;
  private static final int GLOBAL_MAX_PER_WINDOW = 10;
  private static final Duration CONTENT_KEY_TTL = Duration.ofMinutes(2);
  private static final int MAX_BODY_LEN = 220;

  private final UiSettingsBus settingsBus;
  private final ObjectProvider<TrayService> trayServiceProvider;
  private final ObjectProvider<MainFrame> mainFrameProvider;
  private final ObjectProvider<StatusBar> statusBarProvider;
  private final ObjectProvider<TargetCoordinator> targetCoordinatorProvider;
  private final ObjectProvider<cafe.woden.ircclient.ui.ServerTreeDockable> serverTreeProvider;
  private final ObjectProvider<GnomeDbusNotificationBackend> gnomeDbusProvider;
  private final NotificationSoundService soundService;
  private final FlowableProcessor<NotificationRequest> requests;
  private final CompositeDisposable disposables = new CompositeDisposable();
  private final Map<String, Long> lastContentAtMs = new ConcurrentHashMap<>();

  public TrayNotificationService(
      String targetKey,
      String contentKey,
      String title,
      String body,
      Runnable onClick,
      boolean showToast,
      SoundDirective soundDirective
  ) {
  }

  private enum SoundMode {
    GLOBAL,
    NONE,
    OVERRIDE
  }

  private record SoundDirective(
      SoundMode mode,
      String soundId,
      boolean soundUseCustom,
      String soundCustomPath
  ) {
    static SoundDirective global() {
      return new SoundDirective(SoundMode.GLOBAL, null, false, null);
    }

    static SoundDirective none() {
      return new SoundDirective(SoundMode.NONE, null, false, null);
    }

    static SoundDirective override(String soundId, boolean soundUseCustom, String soundCustomPath) {
      return new SoundDirective(SoundMode.OVERRIDE, soundId, soundUseCustom, soundCustomPath);
    }
  }
}
