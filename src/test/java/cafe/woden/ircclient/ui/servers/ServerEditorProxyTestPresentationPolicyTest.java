package cafe.woden.ircclient.ui.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.config.IrcProperties;
import org.junit.jupiter.api.Test;

class ServerEditorProxyTestPresentationPolicyTest {

  @Test
  void successPresentationSummarizesTlsProxyAndElapsedTime() {
    ServerEditorProxyTestPresentationPolicy.ProxyTestSuccessPresentation presentation =
        ServerEditorProxyTestPresentationPolicy.successPresentation(
            true,
            new IrcProperties.Proxy(true, "proxy.example.org", 1080, "", "", true, 20_000, 30_000),
            42);

    assertEquals("OK (42 ms)", presentation.statusText());
    assertTrue(presentation.dialogMessage().contains("TLS: yes"));
    assertTrue(presentation.dialogMessage().contains("Proxy: proxy.example.org:1080"));
    assertTrue(presentation.dialogMessage().contains("Time: 42 ms"));
  }

  @Test
  void failurePresentationFormatsStatusAndDialog() {
    ServerEditorProxyTestPresentationPolicy.ProxyTestFailurePresentation presentation =
        ServerEditorProxyTestPresentationPolicy.failurePresentation(
            "Connection refused", "java.net.ConnectException: Connection refused");

    assertEquals("Failed: Connection refused", presentation.statusText());
    assertTrue(
        presentation.dialogMessage().contains("java.net.ConnectException: Connection refused"));
  }

  @Test
  void unexpectedFailurePresentationUsesGenericStatus() {
    ServerEditorProxyTestPresentationPolicy.ProxyTestFailurePresentation presentation =
        ServerEditorProxyTestPresentationPolicy.unexpectedFailurePresentation(
            "java.lang.IllegalStateException: boom");

    assertEquals("Failed", presentation.statusText());
    assertTrue(presentation.dialogMessage().contains("IllegalStateException"));
  }
}
