package cafe.woden.ircclient.ui;

import io.github.andrewauclair.moderndocking.app.Docking;
import io.github.andrewauclair.moderndocking.Dockable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.processors.FlowableProcessor;
import io.reactivex.rxjava3.processors.PublishProcessor;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.swing.*;
import java.awt.*;

@Component
@Lazy
public class MessageInputDockable extends JPanel implements Dockable {
  public static final String ID = "input";

  private final JTextField input = new JTextField();
  private final JButton send = new JButton("Send");

  private final FlowableProcessor<String> outbound = PublishProcessor.<String>create().toSerialized();

  public MessageInputDockable() {
    super(new BorderLayout(8, 0));
    setPreferredSize(new Dimension(10, 34)); // width ignored by layout; height matters

    add(input, BorderLayout.CENTER);
    add(send, BorderLayout.EAST);

    // Press Enter
    input.addActionListener(e -> emit());
    // Click Send
    send.addActionListener(e -> emit());

    //Docking.registerDockable(this);
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
