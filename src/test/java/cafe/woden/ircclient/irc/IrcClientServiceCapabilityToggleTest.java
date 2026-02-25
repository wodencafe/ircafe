package cafe.woden.ircclient.irc;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.reactivex.rxjava3.core.Completable;
import org.junit.jupiter.api.Test;

class IrcClientServiceCapabilityToggleTest {

  @Test
  void enableCapabilitySendsCapReqWithPositiveToken() {
    IrcClientService service = mock(IrcClientService.class, CALLS_REAL_METHODS);
    when(service.sendRaw("libera", "CAP REQ :message-tags")).thenReturn(Completable.complete());

    service.setIrcv3CapabilityEnabled("libera", "Message-Tags", true).test().assertComplete();

    verify(service).sendRaw("libera", "CAP REQ :message-tags");
  }

  @Test
  void disableCapabilitySendsCapReqWithNegativeToken() {
    IrcClientService service = mock(IrcClientService.class, CALLS_REAL_METHODS);
    when(service.sendRaw("libera", "CAP REQ :-typing")).thenReturn(Completable.complete());

    service.setIrcv3CapabilityEnabled("libera", "typing", false).test().assertComplete();

    verify(service).sendRaw("libera", "CAP REQ :-typing");
  }

  @Test
  void invalidCapabilityTokenFailsBeforeSending() {
    IrcClientService service = mock(IrcClientService.class, CALLS_REAL_METHODS);

    service
        .setIrcv3CapabilityEnabled("libera", "message tags", true)
        .test()
        .assertError(
            t -> t instanceof IllegalArgumentException && t.getMessage().contains("unsupported"));
    verify(service, never()).sendRaw(anyString(), anyString());
  }
}
