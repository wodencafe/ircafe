package cafe.woden.ircclient.interceptors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cafe.woden.ircclient.app.api.InterceptorEventType;
import cafe.woden.ircclient.model.InterceptorDefinition;
import cafe.woden.ircclient.model.InterceptorRule;
import cafe.woden.ircclient.model.InterceptorRuleMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class InterceptorStoreTest {

  @Test
  void capturesMatchingEventsWithRuleDimensions() throws Exception {
    InterceptorStore store = new InterceptorStore(200);
    try {
      InterceptorDefinition def = store.createInterceptor("srv", "Bad words");
      InterceptorDefinition updated =
          new InterceptorDefinition(
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
              List.of(
                  new InterceptorRule(
                      true,
                      "Swear words",
                      "message,action",
                      InterceptorRuleMode.REGEX,
                      "(damn|heck)",
                      InterceptorRuleMode.LIKE,
                      "ali",
                      InterceptorRuleMode.GLOB,
                      "*!ident@host.example")));
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
      InterceptorDefinition updated =
          new InterceptorDefinition(
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
              List.of(
                  new InterceptorRule(
                      true,
                      "Join watcher",
                      "join",
                      InterceptorRuleMode.LIKE,
                      "joined",
                      InterceptorRuleMode.GLOB,
                      "bad*",
                      InterceptorRuleMode.GLOB,
                      "*!*@*")));
      store.saveInterceptor("owner", updated);

      store.ingestEvent(
          "other", "#chan", "badguy", "badguy!id@host", "joined #chan", InterceptorEventType.JOIN);

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

  @Test
  void clearServerHitsKeepsDefinitions() {
    InterceptorStore store = new InterceptorStore(200);
    try {
      InterceptorDefinition def = store.createInterceptor("srv", "Watcher");
      store.clearServerHits("srv");

      List<InterceptorDefinition> defs = store.listInterceptors("srv");
      assertEquals(1, defs.size());
      assertEquals(def.id(), defs.getFirst().id());
    } finally {
      store.shutdown();
    }
  }

  @Test
  void setInterceptorEnabledTogglesState() {
    InterceptorStore store = new InterceptorStore(200);
    try {
      InterceptorDefinition def = store.createInterceptor("srv", "Watcher");
      assertTrue(store.interceptor("srv", def.id()).enabled());

      assertTrue(store.setInterceptorEnabled("srv", def.id(), false));
      assertFalse(store.interceptor("srv", def.id()).enabled());
      assertFalse(store.setInterceptorEnabled("srv", def.id(), false));

      assertTrue(store.setInterceptorEnabled("srv", def.id(), true));
      assertTrue(store.interceptor("srv", def.id()).enabled());
    } finally {
      store.shutdown();
    }
  }

  @Test
  void channelFilterModesAllAndNoneOverridePatternText() throws Exception {
    InterceptorStore store = new InterceptorStore(200);
    try {
      InterceptorDefinition def = store.createInterceptor("srv", "Mode test");
      InterceptorRule rule =
          new InterceptorRule(
              true,
              "Any ping",
              "message",
              InterceptorRuleMode.LIKE,
              "ping",
              InterceptorRuleMode.LIKE,
              "",
              InterceptorRuleMode.GLOB,
              "");

      store.saveInterceptor(
          "srv",
          new InterceptorDefinition(
              def.id(),
              def.name(),
              true,
              "srv",
              InterceptorRuleMode.ALL,
              "#never-match-this",
              InterceptorRuleMode.NONE,
              "#also-ignored",
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
              List.of(rule)));

      store.ingestEvent(
          "srv", "#general", "alice", "alice!id@host", "ping", InterceptorEventType.MESSAGE);
      assertEquals(1, waitForHits(store, "srv", def.id(), 1).size());

      store.clearHits("srv", def.id());

      store.saveInterceptor(
          "srv",
          new InterceptorDefinition(
              def.id(),
              def.name(),
              true,
              "srv",
              InterceptorRuleMode.NONE,
              "#general",
              InterceptorRuleMode.NONE,
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
              List.of(rule)));

      store.ingestEvent(
          "srv", "#general", "alice", "alice!id@host", "ping", InterceptorEventType.MESSAGE);
      assertEquals(0, waitForHits(store, "srv", def.id(), 1).size());

      store.clearHits("srv", def.id());

      store.saveInterceptor(
          "srv",
          new InterceptorDefinition(
              def.id(),
              def.name(),
              true,
              "srv",
              InterceptorRuleMode.ALL,
              "",
              InterceptorRuleMode.ALL,
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
              List.of(rule)));

      store.ingestEvent(
          "srv", "#general", "alice", "alice!id@host", "ping", InterceptorEventType.MESSAGE);
      assertEquals(0, waitForHits(store, "srv", def.id(), 1).size());
    } finally {
      store.shutdown();
    }
  }

  private static List<InterceptorHit> waitForHits(
      InterceptorStore store, String ownerServerId, String interceptorId, int atLeast)
      throws Exception {
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
