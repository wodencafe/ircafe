package cafe.woden.ircclient.ui;

import cafe.woden.ircclient.app.api.UiChannelListPort;
import cafe.woden.ircclient.model.TargetRef;
import cafe.woden.ircclient.ui.channellist.ChannelListPanel;
import cafe.woden.ircclient.ui.servertree.ServerTreeDockable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Swing adapter for channel-list, ban-list, and mode-snapshot UI updates. */
final class SwingUiChannelListPort implements UiChannelListPort {

  private final SwingEdtExecutor edt;
  private final ServerTreeDockable serverTree;
  private final ChatDockable chat;
  private final Object channelListAppendLock = new Object();
  private final Map<String, ArrayList<ChannelListPanel.ListEntryRow>>
      pendingChannelListEntriesByServer = new HashMap<>();
  private boolean channelListAppendFlushScheduled;

  SwingUiChannelListPort(SwingEdtExecutor edt, ServerTreeDockable serverTree, ChatDockable chat) {
    this.edt = Objects.requireNonNull(edt, "edt");
    this.serverTree = Objects.requireNonNull(serverTree, "serverTree");
    this.chat = Objects.requireNonNull(chat, "chat");
  }

  @Override
  public void beginChannelList(String serverId, String banner) {
    edt.run(
        () -> {
          flushPendingChannelListEntriesOnEdt();
          String sid = Objects.toString(serverId, "").trim();
          if (sid.isEmpty()) return;
          serverTree.ensureNode(TargetRef.channelList(sid));
          chat.beginChannelList(sid, banner);
        });
  }

  @Override
  public void appendChannelListEntry(
      String serverId, String channel, int visibleUsers, String topic) {
    enqueueChannelListEntry(serverId, channel, visibleUsers, topic);
  }

  @Override
  public void endChannelList(String serverId, String summary) {
    edt.run(
        () -> {
          flushPendingChannelListEntriesOnEdt();
          String sid = Objects.toString(serverId, "").trim();
          if (sid.isEmpty()) return;
          serverTree.ensureNode(TargetRef.channelList(sid));
          chat.endChannelList(sid, summary);
        });
  }

  @Override
  public void beginChannelBanList(String serverId, String channel) {
    edt.run(() -> chat.beginChannelBanList(serverId, channel));
  }

  @Override
  public void appendChannelBanListEntry(
      String serverId, String channel, String mask, String setBy, Long setAtEpochSeconds) {
    edt.run(
        () -> chat.appendChannelBanListEntry(serverId, channel, mask, setBy, setAtEpochSeconds));
  }

  @Override
  public void endChannelBanList(String serverId, String channel, String summary) {
    edt.run(() -> chat.endChannelBanList(serverId, channel, summary));
  }

  @Override
  public void setChannelModeSnapshot(
      String serverId, String channel, String rawModes, String friendlySummary) {
    edt.run(() -> chat.setChannelModeSnapshot(serverId, channel, rawModes, friendlySummary));
  }

  private void enqueueChannelListEntry(
      String serverId, String channel, int visibleUsers, String topic) {
    String sid = Objects.toString(serverId, "").trim();
    String ch = Objects.toString(channel, "").trim();
    if (sid.isEmpty() || ch.isEmpty()) return;

    synchronized (channelListAppendLock) {
      pendingChannelListEntriesByServer
          .computeIfAbsent(sid, __ -> new ArrayList<>())
          .add(new ChannelListPanel.ListEntryRow(ch, Math.max(0, visibleUsers), topic));
      if (channelListAppendFlushScheduled) return;
      channelListAppendFlushScheduled = true;
    }
    edt.run(this::flushPendingChannelListEntriesOnEdt);
  }

  private void flushPendingChannelListEntriesOnEdt() {
    Map<String, ArrayList<ChannelListPanel.ListEntryRow>> drained = new HashMap<>();
    synchronized (channelListAppendLock) {
      if (pendingChannelListEntriesByServer.isEmpty()) {
        channelListAppendFlushScheduled = false;
        return;
      }
      for (Map.Entry<String, ArrayList<ChannelListPanel.ListEntryRow>> e :
          pendingChannelListEntriesByServer.entrySet()) {
        drained.put(e.getKey(), new ArrayList<>(e.getValue()));
      }
      pendingChannelListEntriesByServer.clear();
      channelListAppendFlushScheduled = false;
    }

    for (Map.Entry<String, ArrayList<ChannelListPanel.ListEntryRow>> e : drained.entrySet()) {
      String sid = Objects.toString(e.getKey(), "").trim();
      if (sid.isEmpty()) continue;
      List<ChannelListPanel.ListEntryRow> rows = e.getValue();
      if (rows == null || rows.isEmpty()) continue;
      serverTree.ensureNode(TargetRef.channelList(sid));
      chat.appendChannelListEntries(sid, List.copyOf(rows));
    }

    boolean reschedule;
    synchronized (channelListAppendLock) {
      reschedule = !pendingChannelListEntriesByServer.isEmpty() && !channelListAppendFlushScheduled;
      if (reschedule) channelListAppendFlushScheduled = true;
    }
    if (reschedule) {
      edt.run(this::flushPendingChannelListEntriesOnEdt);
    }
  }
}
