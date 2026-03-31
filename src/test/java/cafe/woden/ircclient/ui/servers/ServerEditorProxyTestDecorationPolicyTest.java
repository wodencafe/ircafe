package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ServerEditorProxyTestDecorationPolicyTest {

  @Test
  void decorationStateClearsSuccessWhenThereIsNoSuccessfulProxyTest() {
    ServerEditorProxyTestDecorationPolicy.ProxyTestDecorationState state =
        ServerEditorProxyTestDecorationPolicy.decorationState(false, false, true, "", "");

    assertFalse(state.retainLastSuccessfulSnapshot());
    assertFalse(state.hostSuccess());
    assertFalse(state.portSuccess());
    assertFalse(state.connectTimeoutSuccess());
    assertFalse(state.readTimeoutSuccess());
  }

  @Test
  void decorationStateClearsSuccessWhenInputsNoLongerMatchSuccessfulTest() {
    ServerEditorProxyTestDecorationPolicy.ProxyTestDecorationState state =
        ServerEditorProxyTestDecorationPolicy.decorationState(true, false, true, "5000", "5000");

    assertFalse(state.retainLastSuccessfulSnapshot());
    assertFalse(state.hostSuccess());
    assertFalse(state.portSuccess());
  }

  @Test
  void decorationStateRetainsSnapshotButSkipsSuccessWhenProxyFieldsAreDisabled() {
    ServerEditorProxyTestDecorationPolicy.ProxyTestDecorationState state =
        ServerEditorProxyTestDecorationPolicy.decorationState(true, true, false, "5000", "5000");

    assertTrue(state.retainLastSuccessfulSnapshot());
    assertFalse(state.hostSuccess());
    assertFalse(state.portSuccess());
    assertFalse(state.connectTimeoutSuccess());
    assertFalse(state.readTimeoutSuccess());
  }

  @Test
  void decorationStateMarksEndpointAndValidTimeoutsAsSuccessful() {
    ServerEditorProxyTestDecorationPolicy.ProxyTestDecorationState state =
        ServerEditorProxyTestDecorationPolicy.decorationState(true, true, true, "15000", "");

    assertTrue(state.retainLastSuccessfulSnapshot());
    assertTrue(state.hostSuccess());
    assertTrue(state.portSuccess());
    assertTrue(state.connectTimeoutSuccess());
    assertTrue(state.readTimeoutSuccess());
  }

  @Test
  void decorationStateRejectsInvalidTimeoutDecoration() {
    ServerEditorProxyTestDecorationPolicy.ProxyTestDecorationState state =
        ServerEditorProxyTestDecorationPolicy.decorationState(
            true, true, true, "bad-timeout", "-1");

    assertTrue(state.retainLastSuccessfulSnapshot());
    assertTrue(state.hostSuccess());
    assertTrue(state.portSuccess());
    assertFalse(state.connectTimeoutSuccess());
    assertFalse(state.readTimeoutSuccess());
  }
}
