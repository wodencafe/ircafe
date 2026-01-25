package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.ui.settings.UiSettings;
import cafe.woden.ircclient.ui.settings.UiSettingsBus;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

@Component
@Lazy
public class MessageInputDockable extends JPanel implements Dockable {
  public static final String ID = "input";

  private final JTextField input = new JTextField();
  private final JButton send = new JButton("Send");

  private final FlowableProcessor<String> outbound = PublishProcessor.<String>create().toSerialized();

  private final UiSettingsBus settingsBus;
  private final PropertyChangeListener settingsListener = this::onSettingsChanged;

  public MessageInputDockable(UiSettingsBus settingsBus) {
    super(new BorderLayout(8, 0));
    this.settingsBus = settingsBus;

    setPreferredSize(new Dimension(10, 34)); // width ignored by layout; height matters

    add(input, BorderLayout.CENTER);
    add(send, BorderLayout.EAST);

    applySettings(settingsBus.get());

    // Press Enter
    input.addActionListener(e -> emit());
    // Click Send
    send.addActionListener(e -> emit());
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (settingsBus != null) settingsBus.addListener(settingsListener);
  }

  @Override
  public void removeNotify() {
    if (settingsBus != null) settingsBus.removeListener(settingsListener);
    super.removeNotify();
  }

  private void onSettingsChanged(PropertyChangeEvent evt) {
    if (!UiSettingsBus.PROP_UI_SETTINGS.equals(evt.getPropertyName())) return;
    if (evt.getNewValue() instanceof UiSettings s) {
      applySettings(s);
    }
  }

  private void applySettings(UiSettings s) {
    if (s == null) return;
    try {
      Font f = new Font(s.chatFontFamily(), Font.PLAIN, s.chatFontSize());
      input.setFont(f);
      send.setFont(f);
    } catch (Exception ignored) {
    }
  }

  public Flowable<String> outboundMessages() {
    return outbound.onBackpressureBuffer();
  }

  private void emit() {
    String msg = input.getText().trim();
    if (msg.isEmpty()) return;
    input.setText("");
    outbound.onNext(msg);
  }

  @Override public String getPersistentID() { return ID; }
  @Override public String getTabText() { return "Input"; }
}
