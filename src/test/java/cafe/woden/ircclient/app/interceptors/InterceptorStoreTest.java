package cafe.woden.ircclient.app.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InterceptorStoreTest {

  @Test
  void capturesMatchingEventsWithRuleDimensions() throws Exception {
    InterceptorStore store = new InterceptorStore(200);
    try {
      InterceptorDefinition def = store.createInterceptor("srv", "Bad words");
      InterceptorDefinition updated = new InterceptorDefinition(
          def.id(),
          def.name(),
          true,
          "srv",
          InterceptorRuleMode.GLOB,
          "#one,#two",
          InterceptorRuleMode.GLOB,
          "#two-ops",
          false,
          false,
          false,
          "NOTIF_1",
          false,
          "",
          false,
          "",
          "",
          "",
          List.of(new InterceptorRule(
              true,
              "Swear words",
              "message,action",
              InterceptorRuleMode.REGEX,
              "(damn|heck)",
              InterceptorRuleMode.LIKE,
              "ali",
              InterceptorRuleMode.GLOB,
              "*!ident@host.example"))
      );
      store.saveInterceptor("srv", updated);

      store.ingestEvent(
          "srv",
          "#one",
          "alice",
          "alice!ident@host.example",
          "this is heck",
          InterceptorEventType.MESSAGE);
      store.ingestEvent(
          "srv",
          "#three",
          "alice",
          "alice!ident@host.example",
          "this is heck",
          InterceptorEventType.MESSAGE);
      store.ingestEvent(
          "srv",
          "#one",
          "alice",
          "alice!other@host.example",
          "this is heck",
          InterceptorEventType.MESSAGE);
      store.ingestEvent(
          "srv",
          "#one",
          "alice",
          "alice!ident@host.example",
          "this is heck",
          InterceptorEventType.NOTICE);

      List<InterceptorHit> hits = waitForHits(store, "srv", def.id(), 1);
      assertEquals(1, hits.size());
      assertEquals("#one", hits.getFirst().channel());
      assertEquals("alice", hits.getFirst().fromNick());
      assertEquals("Swear words", hits.getFirst().reason());
      assertEquals("message", hits.getFirst().eventType());
      assertEquals("alice!ident@host.example", hits.getFirst().fromHostmask());
    } finally {
      store.shutdown();
    }
  }

  @Test
  void anyServerScopeMatchesOtherServers() throws Exception {
    InterceptorStore store = new InterceptorStore(200);
    try {
      InterceptorDefinition def = store.createInterceptor("owner", "Global watcher");
      InterceptorDefinition updated = new InterceptorDefinition(
          def.id(),
          def.name(),
          true,
          "",
          InterceptorRuleMode.GLOB,
          "#*",
          InterceptorRuleMode.GLOB,
          "",
          false,
          false,
          false,
          "NOTIF_1",
          false,
          "",
          false,
          "",
          "",
          "",
          List.of(new InterceptorRule(
              true,
              "Join watcher",
              "join",
              InterceptorRuleMode.LIKE,
              "joined",
              InterceptorRuleMode.GLOB,
              "bad*",
              InterceptorRuleMode.GLOB,
              "*!*@*"))
      );
      store.saveInterceptor("owner", updated);

      store.ingestEvent(
          "other",
          "#chan",
          "badguy",
          "badguy!id@host",
          "joined #chan",
          InterceptorEventType.JOIN);

      List<InterceptorHit> hits = waitForHits(store, "owner", def.id(), 1);
      assertEquals(1, hits.size());
      assertEquals("other", hits.getFirst().serverId());
      assertEquals("join", hits.getFirst().eventType());
    } finally {
      store.shutdown();
    }
  }

  @Test
  void createsUniqueNamesPerServer() {
    InterceptorStore store = new InterceptorStore(200);
    try {
      InterceptorDefinition a = store.createInterceptor("srv", "Watcher");
      InterceptorDefinition b = store.createInterceptor("srv", "Watcher");
      assertTrue(b.name().startsWith("Watcher"));
      assertTrue(!a.id().equals(b.id()));
    } finally {
      store.shutdown();
    }
  }

  private static List<InterceptorHit> waitForHits(
      InterceptorStore store,
      String ownerServerId,
      String interceptorId,
      int atLeast
  ) throws Exception {
    for (int i = 0; i < 30; i++) {
      List<InterceptorHit> hits = store.listHits(ownerServerId, interceptorId, 100);
      if (hits.size() >= atLeast) {
        return hits;
      }
      Thread.sleep(40L);
    }
    return store.listHits(ownerServerId, interceptorId, 100);
  }
}
