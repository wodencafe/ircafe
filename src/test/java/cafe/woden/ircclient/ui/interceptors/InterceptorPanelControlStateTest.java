package cafe.woden.ircclient.ui.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.model.InterceptorRuleMode;
import java.lang.reflect.Method;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

class InterceptorPanelControlStateTest {

  @Test
  void messageRuleControlsStayDisabledUntilMessageEventIsExplicitlySelected() {
    JCheckBox anyEventType = new JCheckBox();
    anyEventType.setSelected(true);
    JCheckBox messageEventSelector = new JCheckBox();
    JComboBox<InterceptorRuleMode> messageMode =
        new JComboBox<>(
            new InterceptorRuleMode[] {
              InterceptorRuleMode.ALL,
              InterceptorRuleMode.LIKE,
              InterceptorRuleMode.GLOB,
              InterceptorRuleMode.REGEX
            });
    messageMode.setSelectedItem(InterceptorRuleMode.LIKE);
    JTextField messagePattern = new JTextField("ping");

    InterceptorPanel.refreshRuleMessageControlEnabledState(
        anyEventType, messageEventSelector, messageMode, messagePattern);
    assertFalse(messageMode.isEnabled());
    assertFalse(messagePattern.isEnabled());

    anyEventType.setSelected(false);
    messageEventSelector.setSelected(true);
    InterceptorPanel.refreshRuleMessageControlEnabledState(
        anyEventType, messageEventSelector, messageMode, messagePattern);
    assertTrue(messageMode.isEnabled());
    assertTrue(messagePattern.isEnabled());

    messageMode.setSelectedItem(InterceptorRuleMode.ALL);
    InterceptorPanel.refreshRuleMessageControlEnabledState(
        anyEventType, messageEventSelector, messageMode, messagePattern);
    assertTrue(messageMode.isEnabled());
    assertFalse(messagePattern.isEnabled());
  }

  @Test
  void defaultRuleUsesAnyForNickAndHostmask() throws Exception {
    Method method = InterceptorPanel.class.getDeclaredMethod("defaultRule", int.class);
    method.setAccessible(true);

    Object rule = method.invoke(null, 2);

    assertTrue(rule instanceof cafe.woden.ircclient.model.InterceptorRule);
    cafe.woden.ircclient.model.InterceptorRule interceptorRule =
        (cafe.woden.ircclient.model.InterceptorRule) rule;
    assertEquals(InterceptorRuleMode.ALL, interceptorRule.nickMode());
    assertEquals(InterceptorRuleMode.ALL, interceptorRule.hostmaskMode());
  }
}
