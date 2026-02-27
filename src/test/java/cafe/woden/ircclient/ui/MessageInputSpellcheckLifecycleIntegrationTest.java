package cafe.woden.ircclient.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import cafe.woden.ircclient.ui.settings.SpellcheckSettings;
import java.lang.reflect.Field;
import java.util.concurrent.ExecutorService;
import javax.swing.JTextField;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class MessageInputSpellcheckLifecycleIntegrationTest {

  @AfterEach
  void cleanup() {
    MessageInputSpellcheckSupport.shutdownSharedResources();
  }

  @Test
  void springContextCloseInvokesLifecyclePreDestroy() throws Exception {
    MessageInputSpellcheckSupport support =
        new MessageInputSpellcheckSupport(new JTextField(), SpellcheckSettings.defaults());
    ExecutorService execBefore = readSpellcheckExecutor();
    assertNotNull(execBefore);
    try {
      AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
      try {
        ctx.register(MessageInputSpellcheckLifecycle.class);
        ctx.refresh();
      } finally {
        ctx.close();
      }
      assertNull(readSpellcheckExecutor());
    } finally {
      support.shutdown();
    }
  }

  private static ExecutorService readSpellcheckExecutor() throws Exception {
    Field f = MessageInputSpellcheckSupport.class.getDeclaredField("spellcheckExecutor");
    f.setAccessible(true);
    return (ExecutorService) f.get(null);
  }
}

