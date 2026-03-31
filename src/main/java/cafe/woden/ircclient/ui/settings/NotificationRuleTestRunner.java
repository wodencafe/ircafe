package cafe.woden.ircclient.ui.settings;

import cafe.woden.ircclient.config.NotificationRule;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;

final class NotificationRuleTestRunner implements AutoCloseable {
  private static final int MAX_TEST_CHARS = 800;

  private final ExecutorService exec;
  private final AtomicLong seq = new AtomicLong();

  NotificationRuleTestRunner(ExecutorService exec) {
    this.exec = Objects.requireNonNull(exec, "exec");
  }

  void runTest(NotificationRulesControls controls) {
    if (controls == null) return;

    String sample = controls.testInput.getText();
    if (sample == null) sample = "";
    if (sample.length() > MAX_TEST_CHARS) {
      sample = sample.substring(0, MAX_TEST_CHARS);
    }

    List<NotificationRule> rules = controls.model.snapshot();
    List<ValidationError> errors = controls.model.validationErrors();

    long token = seq.incrementAndGet();
    controls.testStatus.setText("Testing…");

    final String sampleFinal = sample;
    exec.submit(
        () -> {
          String report =
              NotificationRuleTestReportSupport.buildRuleTestReport(rules, errors, sampleFinal);
          SwingUtilities.invokeLater(
              () -> {
                if (seq.get() != token) return;
                controls.testOutput.setText(report);
                controls.testOutput.setCaretPosition(0);
                controls.testStatus.setText(" ");
              });
        });
  }

  @Override
  public void close() {
    seq.incrementAndGet();
  }
}
